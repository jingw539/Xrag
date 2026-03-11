package com.hospital.xray.service.impl;

import com.hospital.xray.config.JwtConfig;
import com.hospital.xray.mapper.SysRefreshTokenMapper;
import com.hospital.xray.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * 认证服务单元测试
 */
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

    @Test
    void sha256_Exception_ThrowsRuntimeException() {
        // Arrange
        // The private sha256 method is called inside refreshToken, among other places.
        // We simulate an exception being thrown by MessageDigest.getInstance
        try (MockedStatic<MessageDigest> mockedMessageDigest = Mockito.mockStatic(MessageDigest.class)) {
            mockedMessageDigest.when(() -> MessageDigest.getInstance(anyString()))
                    .thenThrow(new NoSuchAlgorithmException("Mocked exception"));

            // Act & Assert
            // refreshToken calls sha256 at the very beginning
            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                authService.refreshToken("some-refresh-token");
            });

            // Verify exception message and cause
            assertEquals("SHA-256 hashing failed", exception.getMessage());
            assertInstanceOf(NoSuchAlgorithmException.class, exception.getCause());
            assertEquals("Mocked exception", exception.getCause().getMessage());
        }
    }
}
