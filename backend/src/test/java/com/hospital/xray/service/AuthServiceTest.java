package com.hospital.xray.service;

import com.hospital.xray.config.JwtConfig;
import com.hospital.xray.dto.LoginDTO;
import com.hospital.xray.dto.LoginVO;
import com.hospital.xray.entity.SysUser;
import com.hospital.xray.exception.BusinessException;
import com.hospital.xray.mapper.SysRefreshTokenMapper;
import com.hospital.xray.mapper.SysUserMapper;
import com.hospital.xray.security.XrayUserDetails;
import com.hospital.xray.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

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
    void testLogin_LockedException() {
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("testuser");
        loginDTO.setPassword("password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new LockedException("User account is locked"));

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.login(loginDTO);
        });

        assertEquals(401, exception.getCode());
        assertEquals("账号已被禁用，请联系管理员", exception.getMessage());
    }

    @Test
    void testLogin_DisabledException() {
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("testuser");
        loginDTO.setPassword("password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new DisabledException("User account is disabled"));

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.login(loginDTO);
        });

        assertEquals(401, exception.getCode());
        assertEquals("账号已被禁用，请联系管理员", exception.getMessage());
    }

    @Test
    void testLogin_BadCredentialsException() {
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("testuser");
        loginDTO.setPassword("password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.login(loginDTO);
        });

        assertEquals(401, exception.getCode());
        assertEquals("用户名或密码错误", exception.getMessage());
    }

    @Test
    void testLogin_Success() {
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("testuser");
        loginDTO.setPassword("password");

        SysUser sysUser = new SysUser();
        sysUser.setUserId(1L);
        sysUser.setUsername("testuser");
        sysUser.setRoleCode("ADMIN");
        sysUser.setStatus(1);

        XrayUserDetails userDetails = new XrayUserDetails(sysUser, "ADMIN");
        Authentication authentication = mock(Authentication.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(jwtConfig.generateToken(any(XrayUserDetails.class), anyLong(), anyString()))
                .thenReturn("mockedAccessToken");
        when(refreshTokenMapper.insert(any(com.hospital.xray.entity.SysRefreshToken.class))).thenReturn(1);
        when(sysUserMapper.updateLastLoginAt(anyLong(), any())).thenReturn(1);

        LoginVO loginVO = authService.login(loginDTO);

        assertNotNull(loginVO);
        assertEquals("mockedAccessToken", loginVO.getAccessToken());
        assertNotNull(loginVO.getRefreshToken());
        assertEquals(86400L, loginVO.getExpiresIn());
        assertNotNull(loginVO.getUserInfo());
        assertEquals(1L, loginVO.getUserInfo().getUserId());
        assertEquals("testuser", loginVO.getUserInfo().getUsername());

        verify(refreshTokenMapper, times(1)).insert(any(com.hospital.xray.entity.SysRefreshToken.class));
        verify(sysUserMapper, times(1)).updateLastLoginAt(anyLong(), any());
    }
}
