package com.hospital.xray.util;

import com.hospital.xray.security.XrayUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityUtilsTest {

    private SecurityContext securityContext;
    private Authentication authentication;
    private XrayUserDetails userDetails;

    @BeforeEach
    void setUp() {
        securityContext = mock(SecurityContext.class);
        authentication = mock(Authentication.class);
        userDetails = mock(XrayUserDetails.class);

        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUserId_whenAuthenticated_shouldReturnUserId() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUserId()).thenReturn(123L);

        Long userId = SecurityUtils.getCurrentUserId();

        assertEquals(123L, userId);
    }

    @Test
    void getCurrentUserId_whenNotAuthenticated_shouldReturnNull() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);

        Long userId = SecurityUtils.getCurrentUserId();

        assertNull(userId);
    }

    @Test
    void getCurrentUserId_whenAuthenticationNull_shouldReturnNull() {
        when(securityContext.getAuthentication()).thenReturn(null);

        Long userId = SecurityUtils.getCurrentUserId();

        assertNull(userId);
    }

    @Test
    void getCurrentUserId_whenPrincipalNotXrayUserDetails_shouldReturnNull() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(new Object());

        Long userId = SecurityUtils.getCurrentUserId();

        assertNull(userId);
    }

    @Test
    void getCurrentUsername_whenAuthenticated_shouldReturnUsername() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn("testuser");

        String username = SecurityUtils.getCurrentUsername();

        assertEquals("testuser", username);
    }

    @Test
    void getCurrentUsername_whenNotAuthenticated_shouldReturnAnonymous() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);

        String username = SecurityUtils.getCurrentUsername();

        assertEquals("anonymous", username);
    }

    @Test
    void getCurrentRoleCode_whenAuthenticated_shouldReturnRoleCode() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getRoleCode()).thenReturn("ROLE_USER");

        String roleCode = SecurityUtils.getCurrentRoleCode();

        assertEquals("ROLE_USER", roleCode);
    }

    @Test
    void getCurrentRoleCode_whenNotAuthenticated_shouldReturnNull() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);

        String roleCode = SecurityUtils.getCurrentRoleCode();

        assertNull(roleCode);
    }

    @Test
    void isAuthenticated_whenAuthenticatedAndPrincipalValid_shouldReturnTrue() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        boolean authenticated = SecurityUtils.isAuthenticated();

        assertTrue(authenticated);
    }

    @Test
    void isAuthenticated_whenNotAuthenticated_shouldReturnFalse() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);

        boolean authenticated = SecurityUtils.isAuthenticated();

        assertFalse(authenticated);
    }

    @Test
    void isAuthenticated_whenAuthenticationNull_shouldReturnFalse() {
        when(securityContext.getAuthentication()).thenReturn(null);

        boolean authenticated = SecurityUtils.isAuthenticated();

        assertFalse(authenticated);
    }

    @Test
    void hasRole_whenRoleMatches_shouldReturnTrue() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getRoleCode()).thenReturn("ROLE_ADMIN");

        boolean hasRole = SecurityUtils.hasRole("ROLE_ADMIN");

        assertTrue(hasRole);
    }

    @Test
    void hasRole_whenRoleDoesNotMatch_shouldReturnFalse() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getRoleCode()).thenReturn("ROLE_USER");

        boolean hasRole = SecurityUtils.hasRole("ROLE_ADMIN");

        assertFalse(hasRole);
    }

    @Test
    void hasRole_whenNotAuthenticated_shouldReturnFalse() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);

        boolean hasRole = SecurityUtils.hasRole("ROLE_ADMIN");

        assertFalse(hasRole);
    }

    @Test
    void isAdmin_whenRoleIsAdmin_shouldReturnTrue() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getRoleCode()).thenReturn("ADMIN");

        boolean isAdmin = SecurityUtils.isAdmin();

        assertTrue(isAdmin);
    }

    @Test
    void isAdmin_whenRoleIsNotAdmin_shouldReturnFalse() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getRoleCode()).thenReturn("USER");

        boolean isAdmin = SecurityUtils.isAdmin();

        assertFalse(isAdmin);
    }

    @Test
    void isAdmin_whenNotAuthenticated_shouldReturnFalse() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);

        boolean isAdmin = SecurityUtils.isAdmin();

        assertFalse(isAdmin);
    }
}
