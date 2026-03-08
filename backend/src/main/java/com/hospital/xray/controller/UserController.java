package com.hospital.xray.controller;

import com.hospital.xray.common.Result;
import com.hospital.xray.dto.*;
import com.hospital.xray.exception.BusinessException;
import com.hospital.xray.service.UserService;
import com.hospital.xray.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户管理", description = "用户CRUD、密码修改、状态切换")
@Validated
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "查询用户列表")
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public Result<PageResult<UserVO>> listUsers(UserQueryDTO queryDTO) {
        return Result.success(userService.listUsers(queryDTO));
    }

    @Operation(summary = "查询用户详情")
    @GetMapping("/{userId}")
    public Result<UserVO> getUserById(@PathVariable String userId) {
        Long id = Long.parseLong(userId);
        Long currentId = SecurityUtils.getCurrentUserId();
        if (!SecurityUtils.isAdmin() && !id.equals(currentId)) {
            throw new BusinessException(403, "无权限查看其他用户信息");
        }
        return Result.success(userService.getUserById(id));
    }

    @Operation(summary = "创建用户")
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public Result<Long> createUser(@Valid @RequestBody UserCreateDTO createDTO) {
        return Result.success(userService.createUser(createDTO));
    }

    @Operation(summary = "更新用户信息")
    @PutMapping("/{userId}")
    public Result<Void> updateUser(@PathVariable String userId,
                                   @Valid @RequestBody UserUpdateDTO updateDTO) {
        Long id = Long.parseLong(userId);
        Long currentId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();
        if (!isAdmin && !id.equals(currentId)) {
            throw new BusinessException(403, "无权限修改其他用户信息");
        }
        if (!isAdmin) {
            updateDTO.setRoleCode(null);
        }
        userService.updateUser(id, updateDTO);
        return Result.success();
    }

    @Operation(summary = "修改密码")
    @PutMapping("/{userId}/password")
    public Result<Void> updatePassword(@PathVariable String userId,
                                       @Valid @RequestBody PasswordUpdateDTO dto) {
        Long id = Long.parseLong(userId);
        Long currentId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();
        if (!isAdmin && !id.equals(currentId)) {
            throw new BusinessException(403, "无权限修改其他用户密码");
        }
        userService.updatePassword(id, dto, currentId, isAdmin);
        return Result.success();
    }

    @Operation(summary = "启用/禁用用户")
    @PutMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('ADMIN')")
    public Result<Void> updateStatus(@PathVariable String userId,
                                     @Valid @RequestBody StatusUpdateDTO dto) {
        userService.updateStatus(Long.parseLong(userId), dto.getStatus());
        return Result.success();
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public Result<Void> deleteUser(@PathVariable String userId) {
        userService.deleteUser(Long.parseLong(userId));
        return Result.success();
    }
}
