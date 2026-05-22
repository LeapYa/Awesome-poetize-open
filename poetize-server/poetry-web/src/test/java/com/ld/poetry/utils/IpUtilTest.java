package com.ld.poetry.utils;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpUtilTest {

    @Test
    void rejectsPrivateReservedAndMulticastAddressesForVisitStatistics() {
        assertFalse(IpUtil.isPublicRoutableIp("172.28.147.1"));
        assertFalse(IpUtil.isPublicRoutableIp("127.37.17.232"));
        assertFalse(IpUtil.isPublicRoutableIp("224.173.246.204"));
        assertFalse(IpUtil.isPublicRoutableIp("254.29.158.111"));
        assertFalse(IpUtil.isPublicRoutableIp("100.64.0.1"));
        assertFalse(IpUtil.isPublicRoutableIp("198.51.100.12"));
    }

    @Test
    void acceptsPublicRoutableAddressesForVisitStatistics() {
        assertTrue(IpUtil.isPublicRoutableIp("154.37.208.64"));
        assertTrue(IpUtil.isPublicRoutableIp("120.239.141.213"));
    }

    @Test
    void extractsRightMostPublicIpFromForwardedChain() {
        assertEquals("107.174.45.194",
                IpUtil.extractPublicIpFromForwardedFor("127.37.17.232, 107.174.45.194, 172.28.147.1"));
        assertEquals("107.174.45.194",
                IpUtil.extractPublicIpFromForwardedFor("107.174.45.194, 172.28.147.1"));
    }

    @Test
    void visitStatisticsIpIgnoresPrivateRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.28.147.1");
        request.addHeader("X-Forwarded-For", "127.37.17.232, 107.174.45.194, 172.28.147.1");
        request.addHeader("X-Real-IP", "172.28.147.1");

        assertEquals("107.174.45.194", IpUtil.getClientPublicIp(request));
    }

    @Test
    void directPublicRequestsIgnoreSpoofedForwardHeadersForVisitStatistics() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("120.239.141.213");
        request.addHeader("X-Forwarded-For", "8.8.8.8");
        request.addHeader("X-Real-IP", "1.1.1.1");
        request.addHeader("CF-Connecting-IP", "9.9.9.9");

        assertEquals("120.239.141.213", IpUtil.getClientPublicIp(request));
    }

    @Test
    void directPublicRequestsIgnoreSpoofedForwardHeadersForRealIpLookup() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("120.239.141.213");
        request.addHeader("X-Forwarded-For", "8.8.8.8");
        request.addHeader("X-Real-IP", "1.1.1.1");

        assertEquals("120.239.141.213", IpUtil.getClientRealIp(request));
    }

    @Test
    void realIpLookupStillAllowsInternalServiceAddresses() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.28.147.7");

        assertEquals("172.28.147.7", IpUtil.getClientRealIp(request));
    }
}
