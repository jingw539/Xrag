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
        SysUser user = new SysUser();
        user.setUserId(1L);
        user.setUsername("eval-admin");
        user.setPasswordHash("N/A");
        user.setRealName("Eval Admin");
        user.setDepartment("EVAL");
        user.setStatus(1);
        user.setRoleCode("ADMIN");
        this.mockUser = new XrayUserDetails(user, "ADMIN");
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
}
