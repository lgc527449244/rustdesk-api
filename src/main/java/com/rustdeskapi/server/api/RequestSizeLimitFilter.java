package com.rustdeskapi.server.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestSizeLimitFilter.class);

    static final String CLIENT_IP_ATTRIBUTE = "clientIp";

    private static final String HEARTBEAT_PATH = "/api/heartbeat";
    private static final String SYSINFO_PATH = "/api/sysinfo";
    private static final String AUDIT_PATH_PREFIX = "/api/audit/";

    private final int maxRequestSize;
    private final List<String> trustedProxies;

    public RequestSizeLimitFilter(
            @Value("${rustdesk.max-request-size:65536}") int maxRequestSize,
            @Value("${rustdesk.trusted-proxies:}") List<String> trustedProxies) {
        if (maxRequestSize <= 0 || maxRequestSize == Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "rustdesk.max-request-size must be between 1 and " + (Integer.MAX_VALUE - 1));
        }
        this.maxRequestSize = maxRequestSize;
        this.trustedProxies = trustedProxies.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !HEARTBEAT_PATH.equals(path)
                && !SYSINFO_PATH.equals(path)
                && !path.startsWith(AUDIT_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String clientIp = resolveClientIp(request);
        request.setAttribute(CLIENT_IP_ATTRIBUTE, clientIp);

        log.info("incoming {} {} from={} content-length={}",
                request.getMethod(),
                request.getRequestURI(),
                clientIp,
                request.getContentLengthLong());
        if (request.getContentLengthLong() > maxRequestSize) {
            log.warn("reject payload-too-large {} {} declared={} max={}",
                    request.getMethod(), request.getRequestURI(),
                    request.getContentLengthLong(), maxRequestSize);
            reject(response);
            return;
        }

        byte[] body = request.getInputStream().readNBytes(maxRequestSize + 1);
        if (body.length > maxRequestSize) {
            log.warn("reject payload-too-large {} {} read={} max={}",
                    request.getMethod(), request.getRequestURI(),
                    body.length, maxRequestSize);
            reject(response);
            return;
        }

        filterChain.doFilter(new ReplayableHttpServletRequest(request, body), response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (trustedProxies.contains(remote)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                int comma = forwarded.indexOf(',');
                String first = (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
                if (!first.isEmpty()) {
                    return first;
                }
            }
            String real = request.getHeader("X-Real-IP");
            if (real != null && !real.isBlank()) {
                return real.trim();
            }
        }
        return remote;
    }

    private void reject(HttpServletResponse response) {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentLength(0);
    }
}
