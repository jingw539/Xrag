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
    void testGetCurrentUserId_Authenticated() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUserId()).thenReturn(123L);

        assertEquals(123L, SecurityUtils.getCurrentUserId());
    }

    @Test
    void testGetCurrentUserId_NotAuthenticated() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);

        assertNull(SecurityUtils.getCurrentUserId());
    }

    @Test
    void testGetCurrentUsername_Authenticated() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn("testuser");

        assertEquals("testuser", SecurityUtils.getCurrentUsername());
    }

    @Test
    void testGetCurrentUsername_NotAuthenticated() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);

        assertEquals("anonymous", SecurityUtils.getCurrentUsername());
    }

    @Test
    void testGetCurrentUsername_NoAuthentication() {
        when(securityContext.getAuthentication()).thenReturn(null);

        assertEquals("anonymous", SecurityUtils.getCurrentUsername());
    }

    @Test
    void testGetCurrentUsername_InvalidPrincipal() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("NotAUserDetailsObject");

        assertEquals("anonymous", SecurityUtils.getCurrentUsername());
    }

    @Test
    void testGetCurrentRoleCode_Authenticated() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getRoleCode()).thenReturn("ADMIN");

        assertEquals("ADMIN", SecurityUtils.getCurrentRoleCode());
    }

    @Test
    void testGetCurrentRoleCode_NotAuthenticated() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);

        assertNull(SecurityUtils.getCurrentRoleCode());
    }

    @Test
    void testIsAuthenticated_True() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        assertTrue(SecurityUtils.isAuthenticated());
    }

    @Test
    void testIsAuthenticated_False_NotAuthenticated() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);

        assertFalse(SecurityUtils.isAuthenticated());
    }

    @Test
    void testIsAuthenticated_False_NoAuthentication() {
        when(securityContext.getAuthentication()).thenReturn(null);

        assertFalse(SecurityUtils.isAuthenticated());
    }

    @Test
    void testIsAuthenticated_False_InvalidPrincipal() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("NotAUserDetailsObject");

        assertFalse(SecurityUtils.isAuthenticated());
    }

    @Test
    void testHasRole_True() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getRoleCode()).thenReturn("USER");

        assertTrue(SecurityUtils.hasRole("USER"));
    }

    @Test
    void testHasRole_False() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getRoleCode()).thenReturn("USER");

        assertFalse(SecurityUtils.hasRole("ADMIN"));
    }

    @Test
    void testIsAdmin_True() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getRoleCode()).thenReturn("ADMIN");

        assertTrue(SecurityUtils.isAdmin());
    }

    @Test
    void testIsAdmin_False() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getRoleCode()).thenReturn("USER");

        assertFalse(SecurityUtils.isAdmin());
    }
}
