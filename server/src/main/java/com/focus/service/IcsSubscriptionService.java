package com.focus.service;

import com.focus.common.AuthContext;
import com.focus.common.BizException;
import com.focus.dto.SubscriptionReq;
import com.focus.entity.CalendarEvent;
import com.focus.entity.CalendarSubscription;
import com.focus.mapper.SubscriptionMapper;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DateProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class IcsSubscriptionService {
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_EVENTS = 2000;

    private final SubscriptionMapper mapper;
    private final AuthService authService;
    private final TransactionTemplate transactions;

    public IcsSubscriptionService(SubscriptionMapper mapper, AuthService authService,
                                  PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.authService = authService;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public List<CalendarSubscription> list() { return mapper.selectList(AuthContext.userId()); }

    public CalendarSubscription create(SubscriptionReq req) {
        Long userId = AuthContext.userId();
        CalendarSubscription subscription = new CalendarSubscription();
        subscription.setUserId(userId);
        apply(subscription, req);
        validateUrl(subscription.getUrl());
        mapper.insert(subscription);
        if (Boolean.TRUE.equals(subscription.getEnabled())) refreshSafely(subscription);
        return mapper.selectById(userId, subscription.getId());
    }

    public CalendarSubscription update(Long id, SubscriptionReq req) {
        CalendarSubscription subscription = require(AuthContext.userId(), id);
        String previousUrl = subscription.getUrl();
        apply(subscription, req);
        if (!subscription.getUrl().equals(previousUrl)) {
            subscription.setEtag(null);
            subscription.setLastModified(null);
            subscription.setLastRefreshedAt(null);
        }
        validateUrl(subscription.getUrl());
        if (mapper.update(subscription) == 0) throw BizException.notFound("订阅不存在");
        if (Boolean.TRUE.equals(subscription.getEnabled())) refreshSafely(subscription);
        return mapper.selectById(subscription.getUserId(), id);
    }

    public void delete(Long id) {
        Long userId = AuthContext.userId();
        require(userId, id);
        transactions.executeWithoutResult(status -> {
            mapper.deleteEvents(userId, id);
            if (mapper.delete(userId, id) == 0) throw BizException.notFound("订阅不存在");
        });
    }

    public Map<String, Object> refresh(Long id) {
        CalendarSubscription subscription = require(AuthContext.userId(), id);
        return refreshSafely(subscription);
    }

    /** 单进程内串行刷新，避免同一订阅并发替换；失败只记录错误，不动已有事件。 */
    private synchronized Map<String, Object> refreshSafely(CalendarSubscription subscription) {
        Map<String, Object> result = new HashMap<>();
        try {
            validateUrl(subscription.getUrl());
            FetchResult fetched = fetch(subscription);
            if (fetched.notModified) {
                mapper.markSuccess(subscription.getUserId(), subscription.getId(), fetched.etag,
                        fetched.lastModified, LocalDateTime.now(ZoneOffset.UTC));
                result.put("notModified", true);
                result.put("eventCount", null);
            } else {
                List<CalendarEvent> events = parse(fetched.body, subscription);
                transactions.executeWithoutResult(status -> {
                    mapper.deleteEvents(subscription.getUserId(), subscription.getId());
                    for (CalendarEvent event : events) mapper.insertIcs(event);
                    mapper.markSuccess(subscription.getUserId(), subscription.getId(), fetched.etag,
                            fetched.lastModified, LocalDateTime.now(ZoneOffset.UTC));
                });
                result.put("notModified", false);
                result.put("eventCount", events.size());
            }
            result.put("ok", true);
            result.put("lastError", null);
        } catch (Exception e) {
            String message = e instanceof BizException ? e.getMessage() : "订阅刷新失败，请检查地址或稍后重试";
            if (message.length() > 500) message = message.substring(0, 500);
            mapper.markError(subscription.getUserId(), subscription.getId(), message);
            result.put("ok", false);
            result.put("lastError", message);
        }
        return result;
    }

    @Scheduled(fixedDelayString = "${calendar.subscription.refresh-ms:3600000}", initialDelay = 60000)
    public void autoRefresh() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusHours(1);
        for (CalendarSubscription subscription : mapper.selectEnabledStale(cutoff)) {
            refreshSafely(subscription);
        }
    }

    private FetchResult fetch(CalendarSubscription subscription) throws IOException {
        URI current = URI.create(subscription.getUrl());
        String etag = subscription.getEtag();
        String lastModified = subscription.getLastModified();
        for (int redirects = 0; redirects <= 5; redirects++) {
            validateUrl(current.toString());
            HttpURLConnection connection = null;
            try {
                URL url = current.toURL();
                connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("Accept", "text/calendar, text/plain;q=0.8, */*;q=0.1");
                connection.setRequestProperty("User-Agent", "FocusCalendar/1.0");
                if (etag != null) connection.setRequestProperty("If-None-Match", etag);
                if (lastModified != null) connection.setRequestProperty("If-Modified-Since", lastModified);
                int code = connection.getResponseCode();
                if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    return new FetchResult(null, connection.getHeaderField("ETag"),
                            connection.getHeaderField("Last-Modified"), true);
                }
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("重定向缺少 Location");
                    current = current.resolve(location);
                    etag = null;
                    lastModified = null;
                    continue;
                }
                if (code < 200 || code >= 300) throw new IOException("订阅服务器返回 " + code);
                byte[] body = readLimited(connection.getInputStream());
                return new FetchResult(body, connection.getHeaderField("ETag"),
                        connection.getHeaderField("Last-Modified"), false);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        throw new IOException("订阅重定向次数过多");
    }

    private byte[] readLimited(InputStream input) throws IOException {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) throw new IOException("订阅文件超过 2MB 限制");
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private List<CalendarEvent> parse(byte[] body, CalendarSubscription subscription) throws Exception {
        Calendar calendar = new CalendarBuilder().build(new ByteArrayInputStream(body));
        List<CalendarEvent> result = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        ZoneId defaultZone = ZoneId.of(authService.requireUser(subscription.getUserId()).getTimezone());
        for (Object item : calendar.getComponents(Component.VEVENT)) {
            VEvent source = (VEvent) item;
            if (source.getUid() == null || source.getStartDate() == null) {
                throw new IOException("ICS 中存在缺少 UID 或 DTSTART 的事件");
            }
            String uid = source.getUid().getValue().trim();
            if (uid.isEmpty() || uid.length() > 512) throw new IOException("ICS 事件 UID 无效");
            String recurrenceId = source.getRecurrenceId() == null ? "" : source.getRecurrenceId().getValue();
            if (recurrenceId.length() > 128) throw new IOException("ICS  recurrence-id 过长");
            if (!unique.add(uid + "\n" + recurrenceId)) continue;

            DateProperty startProperty = source.getStartDate();
            Date startDate = startProperty.getDate();
            Instant start = startDate.toInstant();
            boolean allDay = isDateOnly(startProperty);
            Instant end;
            if (source.getEndDate() != null && source.getEndDate().getDate() != null) {
                end = source.getEndDate().getDate().toInstant();
            } else {
                end = start.plusSeconds(allDay ? 86400 : 3600);
            }
            if (!start.isBefore(end)) throw new IOException("ICS 事件结束时间必须晚于开始时间");
            String timezone = timezoneOf(startProperty, startDate, defaultZone);

            CalendarEvent event = new CalendarEvent();
            event.setUserId(subscription.getUserId());
            event.setKind("ics");
            event.setSubscriptionId(subscription.getId());
            event.setTitle(limit(source.getSummary() == null ? "无标题" : source.getSummary().getValue(), 200));
            event.setNote(source.getDescription() == null ? "" : source.getDescription().getValue());
            event.setStartAt(LocalDateTime.ofInstant(start, ZoneOffset.UTC));
            event.setEndAt(LocalDateTime.ofInstant(end, ZoneOffset.UTC));
            event.setAllDay(allDay);
            event.setTimezone(timezone);
            Property rrule = source.getProperty(Property.RRULE);
            event.setRrule(rrule == null ? null : rrule.getValue());
            event.setExternalUid(uid);
            event.setRecurrenceId(recurrenceId);
            result.add(event);
            if (result.size() > MAX_EVENTS) throw new IOException("单个订阅事件数超过 2000 条限制");
        }
        return result;
    }

    private boolean isDateOnly(DateProperty property) {
        Parameter value = property.getParameter(Parameter.VALUE);
        return value != null && "DATE".equalsIgnoreCase(value.getValue());
    }

    private String timezoneOf(DateProperty property, Date date, ZoneId defaultZone) {
        Parameter tzid = property.getParameter(Parameter.TZID);
        if (tzid != null) {
            try { return ZoneId.of(tzid.getValue()).getId(); }
            catch (Exception ignored) { return defaultZone.getId(); }
        }
        if (date instanceof DateTime && ((DateTime) date).isUtc()) return "UTC";
        return defaultZone.getId();
    }

    private void apply(CalendarSubscription subscription, SubscriptionReq req) {
        subscription.setUrl(req.getUrl().trim());
        subscription.setName(req.getName().trim());
        subscription.setColor(req.getColor() == null ? "#7c6cf7" : req.getColor());
        subscription.setEnabled(req.getEnabled() == null || req.getEnabled());
    }

    private CalendarSubscription require(Long userId, Long id) {
        CalendarSubscription subscription = mapper.selectById(userId, id);
        if (subscription == null) throw BizException.notFound("订阅不存在");
        return subscription;
    }

    private void validateUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw BizException.badRequest("订阅地址仅支持 HTTP/HTTPS");
            }
            if (uri.getHost() == null || uri.getUserInfo() != null) throw BizException.badRequest("订阅地址无效");
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (isForbidden(address)) throw BizException.badRequest("订阅地址不允许访问内网或本机");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.badRequest("订阅地址无法解析");
        }
    }

    private boolean isForbidden(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
        byte[] bytes = address.getAddress();
        if (bytes.length == 4 && (bytes[0] == 0 || bytes[0] == (byte) 255)) return true;
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    private String limit(String value, int max) {
        String text = value == null ? "" : value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static class FetchResult {
        final byte[] body;
        final String etag;
        final String lastModified;
        final boolean notModified;
        FetchResult(byte[] body, String etag, String lastModified, boolean notModified) {
            this.body = body; this.etag = etag; this.lastModified = lastModified; this.notModified = notModified;
        }
    }
}
