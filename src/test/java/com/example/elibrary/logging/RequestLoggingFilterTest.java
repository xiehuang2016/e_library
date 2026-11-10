package com.example.elibrary.logging;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestLoggingFilterTest {
    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void echoesSafeRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/books");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "client-request-42");

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        assertEquals("client-request-42", response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER));
    }

    @Test
    void replacesUnsafeRequestIdAndClearsScope() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/books");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "forged\r\nvalue");

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        String requestId = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertTrue(Pattern.matches("[0-9a-f-]{36}", requestId));
        assertTrue(ScopeValue.get(ScopeValue.REQUEST_ID).isEmpty());
    }
}
