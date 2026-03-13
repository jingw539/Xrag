package com.hospital.xray.security;

import com.hospital.xray.entity.SysUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PreviewTokenAuthFilter extends OncePerRequestFilter {

    public static final String PREVIEW_ATTR = "PREVIEW_MODE";

    @Value("${app.security.preview-token:}")
    private String previewTokenRaw;

    @Value("${app.security.preview-user-id:1}")
    private Long previewUserId;

    @Value("${app.security.preview-role:ADMIN}")
    private String previewRole;

    @Value("${app.security.preview-header:X-Preview-Token}")
    private String previewHeader;

    private volatile List<String> cachedTokens;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = extractToken(request);
        if (isValidPreviewToken(token)) {
            request.setAttribute(PREVIEW_ATTR, Boolean.TRUE);
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof XrayUserDetails)) {
                SysUser user = new SysUser();
                user.setUserId(previewUserId);
                user.setUsername("preview");
                user.setPasswordHash("N/A");
                user.setRealName("Preview User");
                user.setDepartment("PREVIEW");
                user.setStatus(1);
                user.setRoleCode(previewRole);
                XrayUserDetails mockUser = new XrayUserDetails(user, previewRole);
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(mockUser, null, mockUser.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String token = request.getParameter("preview_token");
        if (StringUtils.hasText(token)) return token.trim();
        token = request.getHeader(previewHeader);
        return StringUtils.hasText(token) ? token.trim() : null;
    }

    private boolean isValidPreviewToken(String token) {
        if (!StringUtils.hasText(token)) return false;
        List<String> tokens = getTokens();
        if (tokens.isEmpty()) return false;
        return tokens.contains(token);
    }

    private List<String> getTokens() {
        List<String> tokens = cachedTokens;
        if (tokens != null) return tokens;
        if (!StringUtils.hasText(previewTokenRaw)) {
            cachedTokens = List.of();
            return cachedTokens;
        }
        cachedTokens = Arrays.stream(previewTokenRaw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        return cachedTokens;
    }
}
