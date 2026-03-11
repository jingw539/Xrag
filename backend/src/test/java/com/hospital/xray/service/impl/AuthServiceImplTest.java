package com.hospital.xray.service.impl;

import com.hospital.xray.config.JwtConfig;
import com.hospital.xray.dto.AccessTokenVO;
import com.hospital.xray.entity.SysRefreshToken;
import com.hospital.xray.entity.SysUser;
import com.hospital.xray.exception.BusinessException;
import com.hospital.xray.mapper.SysRefreshTokenMapper;
import com.hospital.xray.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtConfig jwtConfig;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysRefreshTokenMapper refreshTokenMapper;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "jwtExpiration", 86400L);
        ReflectionTestUtils.setField(authService, "refreshExpiration", 604800L);
    }

    @Test
    void testRefreshToken_Success() {
        String rawToken = "valid-raw-token";
        SysRefreshToken storedToken = new SysRefreshToken();
        storedToken.setUserId(1L);
        storedToken.setExpiresAt(LocalDateTime.now().plusDays(1));

        SysUser user = new SysUser();
        user.setUserId(1L);
        user.setStatus(1);
        user.setRoleCode("ADMIN");

        when(refreshTokenMapper.selectActiveByHash(anyString())).thenReturn(storedToken);
        when(sysUserMapper.selectWithRoleByUserId(1L)).thenReturn(user);
        when(jwtConfig.generateToken(any(), any(), any())).thenReturn("new-access-token");

        AccessTokenVO result = authService.refreshToken(rawToken);

        assertNotNull(result);
        assertEquals("new-access-token", result.getAccessToken());
        assertEquals(86400L, result.getExpiresIn());
    }

    @Test
    void testRefreshToken_TokenNotFound() {
        String rawToken = "invalid-raw-token";

        when(refreshTokenMapper.selectActiveByHash(anyString())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.refreshToken(rawToken);
        });

        assertEquals(401, exception.getCode());
        assertEquals("refreshToken 无效或已过期", exception.getMessage());
    }

    @Test
    void testRefreshToken_TokenExpired() {
        String rawToken = "expired-raw-token";
        SysRefreshToken storedToken = new SysRefreshToken();
        storedToken.setUserId(1L);
        storedToken.setExpiresAt(LocalDateTime.now().minusDays(1));

        when(refreshTokenMapper.selectActiveByHash(anyString())).thenReturn(storedToken);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.refreshToken(rawToken);
        });

        assertEquals(401, exception.getCode());
        assertEquals("refreshToken 已过期，请重新登录", exception.getMessage());
    }

    @Test
    void testRefreshToken_UserNotFoundOrDisabled() {
        String rawToken = "valid-raw-token";
        SysRefreshToken storedToken = new SysRefreshToken();
        storedToken.setUserId(1L);
        storedToken.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(refreshTokenMapper.selectActiveByHash(anyString())).thenReturn(storedToken);
        when(sysUserMapper.selectWithRoleByUserId(1L)).thenReturn(null); // User not found

        BusinessException exception1 = assertThrows(BusinessException.class, () -> {
            authService.refreshToken(rawToken);
        });
        assertEquals(401, exception1.getCode());
        assertEquals("用户不存在或已被禁用", exception1.getMessage());

        SysUser disabledUser = new SysUser();
        disabledUser.setUserId(1L);
        disabledUser.setStatus(0); // Disabled

        when(sysUserMapper.selectWithRoleByUserId(1L)).thenReturn(disabledUser);

        BusinessException exception2 = assertThrows(BusinessException.class, () -> {
            authService.refreshToken(rawToken);
        });
        assertEquals(401, exception2.getCode());
        assertEquals("用户不存在或已被禁用", exception2.getMessage());
    }
}
