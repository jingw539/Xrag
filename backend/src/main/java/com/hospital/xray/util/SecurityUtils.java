package com.hospital.xray.util;

import com.hospital.xray.security.XrayUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;

public class SecurityUtils {

    public static Long getCurrentUserId() {
        XrayUserDetails details = getPrincipal();
        return details != null ? details.getUserId() : null;
    }

    public static String getCurrentUsername() {
        XrayUserDetails details = getPrincipal();
        return details != null ? details.getUsername() : "anonymous";
    }

    public static String getCurrentRoleCode() {
        XrayUserDetails details = getPrincipal();
        return details != null ? details.getRoleCode() : null;
    }

    public static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && getPrincipal() != null;
    }

    public static boolean hasRole(String roleCode) {
        return Objects.equals(roleCode, getCurrentRoleCode());
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(getCurrentRoleCode());
    }

    private static XrayUserDetails getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        return principal instanceof XrayUserDetails ? (XrayUserDetails) principal : null;
    }
}
