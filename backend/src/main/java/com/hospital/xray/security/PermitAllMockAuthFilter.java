package com.hospital.xray.security;

import com.hospital.xray.entity.SysUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class PermitAllMockAuthFilter extends OncePerRequestFilter {

    private final XrayUserDetails mockUser;

    public PermitAllMockAuthFilter() {
        long userId = getLongEnv("APP_SECURITY_MOCK_USER_ID", 1L);
        String roleCode = getEnv("APP_SECURITY_MOCK_ROLE", "ADMIN");
        String username = getEnv("APP_SECURITY_MOCK_USERNAME", "eval-admin");
        String realName = getEnv("APP_SECURITY_MOCK_REALNAME", "Eval Admin");
        String department = getEnv("APP_SECURITY_MOCK_DEPARTMENT", "EVAL");

        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setUsername(username);
        user.setPasswordHash("N/A");
        user.setRealName(realName);
        user.setDepartment(department);
        user.setStatus(1);
        user.setRoleCode(roleCode);
        this.mockUser = new XrayUserDetails(user, roleCode);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof XrayUserDetails)) {
            UsernamePasswordAuthenticationToken token =
                    new UsernamePasswordAuthenticationToken(mockUser, null, mockUser.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(token);
        }
        filterChain.doFilter(request, response);
    }

    private static String getEnv(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static long getLongEnv(String key, long fallback) {
        String value = System.getenv(key);
        if (value == null) return fallback;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
