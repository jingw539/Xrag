package com.hospital.xray.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestUtilsTest {

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testGetCurrentRequest() {
        HttpServletRequest currentRequest = RequestUtils.getCurrentRequest();
        assertEquals(request, currentRequest);

        RequestContextHolder.resetRequestAttributes();
        assertNull(RequestUtils.getCurrentRequest());
    }

    @Test
    void testGetClientIp_WhenRequestIsNull() {
        RequestContextHolder.resetRequestAttributes();
        assertEquals("unknown", RequestUtils.getClientIp());
    }

    @Test
    void testGetClientIp_XForwardedFor() {
        request.addHeader("X-Forwarded-For", "192.168.1.1");
        assertEquals("192.168.1.1", RequestUtils.getClientIp());
    }

    @Test
    void testGetClientIp_ProxyClientIP() {
        request.addHeader("Proxy-Client-IP", "192.168.1.2");
        assertEquals("192.168.1.2", RequestUtils.getClientIp());
    }

    @Test
    void testGetClientIp_WLProxyClientIP() {
        request.addHeader("WL-Proxy-Client-IP", "192.168.1.3");
        assertEquals("192.168.1.3", RequestUtils.getClientIp());
    }

    @Test
    void testGetClientIp_HttpClientIp() {
        request.addHeader("HTTP_CLIENT_IP", "192.168.1.4");
        assertEquals("192.168.1.4", RequestUtils.getClientIp());
    }

    @Test
    void testGetClientIp_HttpXForwardedFor() {
        request.addHeader("HTTP_X_FORWARDED_FOR", "192.168.1.5");
        assertEquals("192.168.1.5", RequestUtils.getClientIp());
    }

    @Test
    void testGetClientIp_RemoteAddr() {
        request.setRemoteAddr("192.168.1.6");
        assertEquals("192.168.1.6", RequestUtils.getClientIp());
    }

    @Test
    void testGetClientIp_MultipleIPs() {
        request.addHeader("X-Forwarded-For", "192.168.1.1, 10.0.0.1, 172.16.0.1");
        assertEquals("192.168.1.1", RequestUtils.getClientIp());
    }

    @Test
    void testGetClientIp_MultipleIPsWithUnknown() {
        request.addHeader("X-Forwarded-For", "unknown, 10.0.0.1");
        assertEquals("unknown", RequestUtils.getClientIp());
    }

    @Test
    void testGetRequestPath() {
        request.setRequestURI("/api/test");
        assertEquals("/api/test", RequestUtils.getRequestPath());

        RequestContextHolder.resetRequestAttributes();
        assertEquals("", RequestUtils.getRequestPath());
    }

    @Test
    void testGetRequestMethod() {
        request.setMethod("POST");
        assertEquals("POST", RequestUtils.getRequestMethod());

        RequestContextHolder.resetRequestAttributes();
        assertEquals("", RequestUtils.getRequestMethod());
    }
}
