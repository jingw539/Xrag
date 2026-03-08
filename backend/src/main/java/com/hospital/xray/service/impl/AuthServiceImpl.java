package com.hospital.xray.service.impl;

import com.hospital.xray.config.JwtConfig;
import com.hospital.xray.dto.AccessTokenVO;
import com.hospital.xray.dto.LoginDTO;
import com.hospital.xray.dto.LoginVO;
import com.hospital.xray.dto.UserInfoVO;
import com.hospital.xray.entity.SysRefreshToken;
import com.hospital.xray.entity.SysUser;
import com.hospital.xray.exception.BusinessException;
import com.hospital.xray.mapper.SysRefreshTokenMapper;
import com.hospital.xray.mapper.SysUserMapper;
import com.hospital.xray.security.XrayUserDetails;
import com.hospital.xray.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtConfig jwtConfig;
    private final SysUserMapper sysUserMapper;
    private final SysRefreshTokenMapper refreshTokenMapper;

    @Value("${jwt.expiration:86400}")
    private Long jwtExpiration;

    @Value("${jwt.refresh-expiration:604800}")
    private Long refreshExpiration;

    @Override
    @Transactional
    public LoginVO login(LoginDTO loginDTO) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginDTO.getUsername(), loginDTO.getPassword()));
        } catch (DisabledException | LockedException e) {
            throw new BusinessException(401, "账号已被禁用，请联系管理员");
        } catch (BadCredentialsException e) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        XrayUserDetails userDetails = (XrayUserDetails) authentication.getPrincipal();
        SysUser sysUser = userDetails.getSysUser();

        String accessToken = jwtConfig.generateToken(userDetails, sysUser.getUserId(), userDetails.getRoleCode());

        String rawRefreshToken = UUID.randomUUID().toString().replace("-", "");
        String tokenHash = sha256(rawRefreshToken);

        SysRefreshToken refreshToken = new SysRefreshToken();
        refreshToken.setUserId(sysUser.getUserId());
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(LocalDateTime.now().plusSeconds(refreshExpiration));
        refreshToken.setRevoked(0);
        refreshToken.setCreatedAt(LocalDateTime.now());
        refreshTokenMapper.insert(refreshToken);

        sysUserMapper.updateLastLoginAt(sysUser.getUserId(), LocalDateTime.now());

        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .expiresIn(jwtExpiration)
                .userInfo(UserInfoVO.builder()
                        .userId(sysUser.getUserId())
                        .username(sysUser.getUsername())
                        .realName(sysUser.getRealName())
                        .roleCode(userDetails.getRoleCode())
                        .department(sysUser.getDepartment())
                        .status(sysUser.getStatus())
                        .lastLoginAt(sysUser.getLastLoginAt())
                        .build())
                .build();
    }

    @Override
    @Transactional
    public AccessTokenVO refreshToken(String rawRefreshToken) {
        String tokenHash = sha256(rawRefreshToken);
        SysRefreshToken storedToken = refreshTokenMapper.selectActiveByHash(tokenHash);

        if (storedToken == null) {
            throw new BusinessException(401, "refreshToken无效或已过期");
        }
        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(401, "refreshToken已过期，请重新登录");
        }

        SysUser user = sysUserMapper.selectWithRoleByUserId(storedToken.getUserId());
        if (user == null || user.getStatus() != 1) {
            throw new BusinessException(401, "用户不存在或已被禁用");
        }

        String roleCode = user.getRoleCode() != null ? user.getRoleCode() : "DOCTOR";
        XrayUserDetails userDetails = new XrayUserDetails(user, roleCode);
        String newAccessToken = jwtConfig.generateToken(userDetails, user.getUserId(), roleCode);

        return AccessTokenVO.builder()
                .accessToken(newAccessToken)
                .expiresIn(jwtExpiration)
                .build();
    }

    @Override
    @Transactional
    public void logout(Long userId) {
        refreshTokenMapper.revokeAllByUserId(userId);
    }

    @Override
    public UserInfoVO getCurrentUserInfo(Long userId) {
        SysUser user = sysUserMapper.selectWithRoleByUserId(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return UserInfoVO.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .roleCode(user.getRoleCode())
                .department(user.getDepartment())
                .status(user.getStatus())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 hashing failed", e);
        }
    }
}
