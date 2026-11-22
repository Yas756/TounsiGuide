package com.tounsiguide.gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * S2-T03: when the service behind a route cannot be reached (refused, unknown host, timeout), the browser gets
 * {@code 503 SERVICE_UNAVAILABLE} in the common error format instead of Spring's default 500 body.
 * Any other error is left to the normal handling.
 */
@Component
public class ServiceUnavailableFilter extends OncePerRequestFilter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(ServiceUnavailableFilter.class);

    /** Outside the JWT filter, so a 401 is never hidden and every proxied call is covered. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            Throwable cause = unreachable(exception);
            if (cause == null || response.isCommitted()) {
                throw exception;
            }
            log.warn("{} {}: service unavailable ({})", request.getMethod(), request.getRequestURI(), cause.toString());
            response.resetBuffer();
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"timestamp\":\"" + Instant.now()
                    + "\",\"status\":503,\"code\":\"SERVICE_UNAVAILABLE\","
                    + "\"message\":\"This part of TounsiGuide is temporarily unavailable. Try again in a moment.\","
                    + "\"path\":\"" + json(request.getRequestURI()) + "\",\"details\":{}}");
        }
    }

    /** The connection problem in the cause chain, or null when the error is something else. */
    static Throwable unreachable(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof ConnectException || t instanceof UnknownHostException || t instanceof SocketTimeoutException
                    || t instanceof HttpConnectTimeoutException || t instanceof HttpTimeoutException) {
                return t;
            }
        }
        return null;
    }

    private static String json(String value) {
        StringBuilder out = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> out.append(c < 0x20 ? ' ' : c);
            }
        }
        return out.toString();
    }
}
