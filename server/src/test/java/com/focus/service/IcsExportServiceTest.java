package com.focus.service;

import com.focus.common.AuthContext;
import com.focus.entity.AppUser;
import com.focus.entity.CalendarEvent;
import com.focus.mapper.CalendarMapper;
import com.focus.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IcsExportServiceTest {
    private UserMapper userMapper;
    private CalendarMapper calendarMapper;
    private IcsExportService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        calendarMapper = mock(CalendarMapper.class);
        service = new IcsExportService(userMapper, calendarMapper);
        AuthContext.setUserId(7L);
    }

    @AfterEach
    void tearDown() { AuthContext.clear(); }

    @Test
    void enableGeneratesStableTokenAndRevokeClearsIt() {
        AppUser user = user(7L);
        when(userMapper.selectById(7L)).thenReturn(user);

        Map<String, Object> first = service.enable();
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(userMapper).updateIcsToken(eq(7L), token.capture());
        assertTrue((Boolean) first.get("enabled"));
        assertEquals("/calendars/" + token.getValue() + ".ics", first.get("url"));

        Map<String, Object> second = service.enable();
        assertEquals(first.get("url"), second.get("url"));
        service.revoke();
        verify(userMapper).updateIcsToken(7L, null);
    }

    @Test
    void exportUsesUtcAndEscapesText() {
        String token = "6f1d92c4-05a1-4ef5-b51c-4bf77f43f0c1";
        AppUser user = user(7L);
        when(userMapper.selectByIcsToken(token)).thenReturn(user);
        CalendarEvent event = event();
        event.setTitle("复盘,;A\nB");
        event.setNote("第一行\n第二行");
        when(calendarMapper.selectExport(7L)).thenReturn(Collections.singletonList(event));

        String ics = new String(service.export(token), StandardCharsets.UTF_8);
        assertTrue(ics.contains("VERSION:2.0\r\n"));
        assertTrue(ics.contains("UID:focus-event-42@focus.local\r\n"));
        assertTrue(ics.contains("DTSTART:20261115T010000Z\r\n"));
        assertTrue(ics.contains("DTEND:20261115T020000Z\r\n"));
        assertTrue(ics.contains("SUMMARY:复盘\\,\\;A\\nB\r\n"));
        assertTrue(ics.contains("DESCRIPTION:第一行\\n第二行\r\n"));
        assertFalse(ics.contains("focus_session"));
    }

    @Test
    void exportAllDayUsesOriginalTimezoneDate() {
        String token = "6f1d92c4-05a1-4ef5-b51c-4bf77f43f0c1";
        AppUser user = user(7L);
        when(userMapper.selectByIcsToken(token)).thenReturn(user);
        CalendarEvent event = event();
        event.setAllDay(true);
        event.setStartAt(LocalDateTime.of(2026, 11, 14, 16, 0));
        event.setEndAt(LocalDateTime.of(2026, 11, 15, 16, 0));
        when(calendarMapper.selectExport(7L)).thenReturn(Collections.singletonList(event));

        String ics = new String(service.export(token), StandardCharsets.UTF_8);
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261115\r\n"));
        assertTrue(ics.contains("DTEND;VALUE=DATE:20261116\r\n"));
    }

    @Test
    void exportRejectsMalformedTokenWithoutDatabaseAccess() {
        assertNull(service.export("not-a-token"));
        verifyNoInteractions(userMapper, calendarMapper);
    }

    private AppUser user(Long id) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setDisplayName("测试用户");
        return user;
    }

    private CalendarEvent event() {
        CalendarEvent event = new CalendarEvent();
        event.setId(42L);
        event.setKind("task");
        event.setTitle("任务");
        event.setStartAt(LocalDateTime.of(2026, 11, 15, 1, 0));
        event.setEndAt(LocalDateTime.of(2026, 11, 15, 2, 0));
        event.setTimezone("Asia/Shanghai");
        event.setAllDay(false);
        return event;
    }
}
