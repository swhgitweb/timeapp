package com.focus.service;

import com.focus.common.AuthContext;
import com.focus.common.BizException;
import com.focus.common.CommonResult;
import com.focus.dto.SessionCreateReq;
import com.focus.entity.FocusSession;
import com.focus.mapper.SessionMapper;
import com.focus.service.impl.SessionServiceImpl;
import com.focus.vo.SessionVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionServiceImplTest {
    private SessionMapper sessionMapper;
    private BasicDataService basicDataService;
    private AuthService authService;
    private SessionServiceImpl service;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(SessionMapper.class);
        basicDataService = mock(BasicDataService.class);
        authService = mock(AuthService.class);
        service = new SessionServiceImpl(sessionMapper, basicDataService, authService);
        AuthContext.setUserId(7L);
    }

    @AfterEach
    void tearDown() { AuthContext.clear(); }

    @Test
    void createRejectsDurationMismatchBeforeWriting() {
        SessionCreateReq req = validRequest();
        req.setDurationSec(100);

        BizException error = assertThrows(BizException.class, () -> service.create(req));
        assertEquals(CommonResult.CODE_BAD_REQUEST, error.getCode());
        verify(sessionMapper, never()).insert(any());
    }

    @Test
    void createStoresUtcAndUserTimezoneDate() {
        SessionCreateReq req = validRequest();
        req.setStartedAt(Instant.parse("2026-11-15T16:30:00Z"));
        req.setEndedAt(Instant.parse("2026-11-15T16:55:00Z"));
        when(authService.zoneOf(7L)).thenReturn(ZoneId.of("Asia/Shanghai"));

        SessionVO result = service.create(req);

        ArgumentCaptor<FocusSession> captor = ArgumentCaptor.forClass(FocusSession.class);
        verify(sessionMapper).insert(captor.capture());
        FocusSession saved = captor.getValue();
        assertEquals(LocalDate.of(2026, 11, 16), saved.getSessionDate());
        assertEquals(LocalDateTime.of(2026, 11, 15, 16, 30), saved.getStartedAt());
        assertEquals(1500, saved.getDurationSec());
        assertEquals("manual", result.getSource());
        assertEquals(7L, saved.getUserId());
    }

    @Test
    void getAlwaysQueriesWithinCurrentUser() {
        when(sessionMapper.selectById(7L, 99L)).thenReturn(null);
        BizException error = assertThrows(BizException.class, () -> service.get(99L));
        assertEquals(CommonResult.CODE_NOT_FOUND, error.getCode());
        verify(sessionMapper).selectById(7L, 99L);
    }

    private SessionCreateReq validRequest() {
        SessionCreateReq req = new SessionCreateReq();
        req.setClientId("6f1d92c4-05a1-4ef5-b51c-4bf77f43f0c1");
        req.setTaskTitle("自由专注");
        req.setNote("笔记");
        req.setMode("countup");
        req.setStartedAt(Instant.parse("2026-11-15T01:00:00Z"));
        req.setEndedAt(Instant.parse("2026-11-15T01:25:00Z"));
        req.setDurationSec(1500);
        req.setPausedSec(0);
        req.setStatus("completed");
        req.setSource("manual");
        return req;
    }
}
