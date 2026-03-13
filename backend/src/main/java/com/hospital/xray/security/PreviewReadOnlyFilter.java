package com.hospital.xray.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class PreviewReadOnlyFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Object preview = request.getAttribute(PreviewTokenAuthFilter.PREVIEW_ATTR);
        if (Boolean.TRUE.equals(preview)) {
            String method = request.getMethod();
            if (!HttpMethod.GET.matches(method) && !HttpMethod.HEAD.matches(method) && !HttpMethod.OPTIONS.matches(method)) {
                response.setStatus(403);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"message\":\"Preview mode is read-only\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
