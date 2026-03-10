package com.hospital.xray.controller;

import com.hospital.xray.common.Result;
import com.hospital.xray.dto.AccessTokenVO;
import com.hospital.xray.dto.LoginDTO;
import com.hospital.xray.dto.LoginVO;
import com.hospital.xray.dto.RefreshTokenDTO;
import com.hospital.xray.dto.UserInfoVO;
import com.hospital.xray.service.AuthService;
import com.hospital.xray.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户认证", description = "登录、登出、Token 刷新")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        return Result.success(authService.login(loginDTO));
    }

    @Operation(summary = "刷新 Access Token")
    @PostMapping("/refresh")
    public Result<AccessTokenVO> refresh(@Valid @RequestBody RefreshTokenDTO dto) {
        return Result.success(authService.refreshToken(dto.getRefreshToken()));
    }

    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout(SecurityUtils.getCurrentUserId());
        return Result.success(null, "已登出");
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/me")
    public Result<UserInfoVO> me() {
        return Result.success(authService.getCurrentUserInfo(SecurityUtils.getCurrentUserId()));
    }
}