package com.focus.mapper;

import com.focus.entity.CalendarEvent;
import com.focus.entity.CalendarSubscription;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface SubscriptionMapper {
    List<CalendarSubscription> selectList(@Param("userId") Long userId);
    CalendarSubscription selectById(@Param("userId") Long userId, @Param("id") Long id);
    List<CalendarSubscription> selectEnabledStale(@Param("cutoff") LocalDateTime cutoff);
    int insert(CalendarSubscription subscription);
    int update(CalendarSubscription subscription);
    int delete(@Param("userId") Long userId, @Param("id") Long id);
    int markSuccess(@Param("userId") Long userId, @Param("id") Long id, @Param("etag") String etag,
                    @Param("lastModified") String lastModified, @Param("refreshedAt") LocalDateTime refreshedAt);
    int markError(@Param("userId") Long userId, @Param("id") Long id, @Param("error") String error);
    int deleteEvents(@Param("userId") Long userId, @Param("subscriptionId") Long subscriptionId);
    int insertIcs(CalendarEvent event);
}
