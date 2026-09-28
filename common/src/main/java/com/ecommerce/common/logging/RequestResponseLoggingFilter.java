package com.ecommerce.common.logging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

@Component
@Slf4j
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-ID";
    private static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString();
        }

        MDC.put(TRACE_ID_MDC_KEY, traceId);
        long startTime = System.currentTimeMillis();

        try {
            log.info("Incoming Request - Method: {}, URI: {}",
                request.getMethod(), request.getRequestURI());

            response.addHeader(TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Outgoing Response - Status: {}, Duration: {}ms",
                response.getStatus(), duration);

        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Request Failed - Method: {}, URI: {}, Duration: {}ms, Error: {}",
                request.getMethod(), request.getRequestURI(), duration, ex.getMessage(), ex);
            throw ex;
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }
}
