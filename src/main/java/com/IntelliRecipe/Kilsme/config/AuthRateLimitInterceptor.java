package com.IntelliRecipe.Kilsme.config;

import com.IntelliRecipe.Kilsme.dto.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

@Component
public class AuthRateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${security.rate-limit.window-seconds:60}")
    private long windowSeconds;

    @Value("${security.rate-limit.max-requests-per-window:30}")
    private long maxRequestsPerWindow;

    @Value("${security.rate-limit.login-max-requests:8}")
    private long loginMaxRequests;

    @Value("${security.rate-limit.register-max-requests:6}")
    private long registerMaxRequests;

    @Value("${security.rate-limit.session-max-requests:40}")
    private long sessionMaxRequests;

    @Value("${security.rate-limit.logout-max-requests:20}")
    private long logoutMaxRequests;

    public AuthRateLimitInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String ip = extractIp(request);
        String uri = request.getRequestURI();
        long endpointLimit = resolveLimit(uri);
        String identity = resolveIdentity(request, uri);
        String key = "rate:auth:" + uri + ":" + ip + ":" + identity;

        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }

        if (count != null && count > endpointLimit) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(Result.fail("请求过于频繁，请稍后再试")));
            return false;
        }

        return true;
    }

    private String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private long resolveLimit(String uri) {
        if ("/api/auth/login".equals(uri)) {
            return loginMaxRequests;
        }
        if ("/api/auth/register".equals(uri)) {
            return registerMaxRequests;
        }
        if ("/api/auth/session".equals(uri)) {
            return sessionMaxRequests;
        }
        if ("/api/auth/logout".equals(uri)) {
            return logoutMaxRequests;
        }
        return maxRequestsPerWindow;
    }

    private String resolveIdentity(HttpServletRequest request, String uri) {
        if ("/api/auth/login".equals(uri) || "/api/auth/register".equals(uri)) {
            String username = request.getParameter("username");
            return username == null || username.isBlank() ? "anonymous" : username.trim().toLowerCase();
        }
        if (request.getSession(false) != null) {
            return "sid-" + request.getSession(false).getId();
        }
        return "anonymous";
    }
}

