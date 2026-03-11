package com.hospital.xray.service;

import com.hospital.xray.config.JwtConfig;
import com.hospital.xray.dto.LoginDTO;
import com.hospital.xray.exception.BusinessException;
import com.hospital.xray.mapper.SysRefreshTokenMapper;
import com.hospital.xray.mapper.SysUserMapper;
import com.hospital.xray.service.impl.AuthServiceImpl;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

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

    @Test
    void testLogin_DisabledException() {
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("disabled_user");
        loginDTO.setPassword("password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new DisabledException("Disabled"));

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.login(loginDTO);
        });

        assertEquals(401, exception.getCode());
        assertEquals("账号已被禁用，请联系管理员", exception.getMessage());
    }

    @Test
    void testLogin_LockedException() {
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("locked_user");
        loginDTO.setPassword("password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new LockedException("Locked"));

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.login(loginDTO);
        });

        assertEquals(401, exception.getCode());
        assertEquals("账号已被禁用，请联系管理员", exception.getMessage());
    }

    @Test
    void testLogin_BadCredentialsException() {
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("wrong_user");
        loginDTO.setPassword("wrong_password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.login(loginDTO);
        });

        assertEquals(401, exception.getCode());
        assertEquals("用户名或密码错误", exception.getMessage());
    }
}