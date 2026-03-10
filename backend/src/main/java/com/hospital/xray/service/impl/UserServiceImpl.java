package com.hospital.xray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.xray.dto.PageResult;
import com.hospital.xray.dto.PasswordUpdateDTO;
import com.hospital.xray.dto.UserCreateDTO;
import com.hospital.xray.dto.UserQueryDTO;
import com.hospital.xray.dto.UserUpdateDTO;
import com.hospital.xray.dto.UserVO;
import com.hospital.xray.entity.SysRole;
import com.hospital.xray.entity.SysUser;
import com.hospital.xray.exception.BusinessException;
import com.hospital.xray.mapper.SysRoleMapper;
import com.hospital.xray.mapper.SysUserMapper;
import com.hospital.xray.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<UserVO> listUsers(UserQueryDTO queryDTO) {
        Page<SysUser> page = new Page<>(queryDTO.getPage(), queryDTO.getPageSize());
        Page<SysUser> result = sysUserMapper.selectPageWithRole(
                page,
                queryDTO.getUsername(),
                queryDTO.getRealName(),
                queryDTO.getDepartment(),
                queryDTO.getStatus(),
                queryDTO.getRoleCode());

        List<UserVO> list = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), list);
    }

    @Override
    public UserVO getUserById(Long userId) {
        SysUser user = sysUserMapper.selectWithRoleByUserId(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return toVO(user);
    }

    @Override
    @Transactional
    public Long createUser(UserCreateDTO createDTO) {
        LambdaQueryWrapper<SysUser> existCheck = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, createDTO.getUsername());
        if (sysUserMapper.selectCount(existCheck) > 0) {
            throw new BusinessException(400, "用户名已存在");
        }

        SysRole role = sysRoleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, createDTO.getRoleCode()));
        if (role == null) {
            throw new BusinessException(400, "角色不存在: " + createDTO.getRoleCode());
        }

        SysUser user = new SysUser();
        user.setUsername(createDTO.getUsername());
        user.setPasswordHash(passwordEncoder.encode(createDTO.getPassword()));
        user.setRealName(createDTO.getRealName());
        user.setRoleId(role.getRoleId());
        user.setDepartment(createDTO.getDepartment());
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.insert(user);
        return user.getUserId();
    }

    @Override
    @Transactional
    public void updateUser(Long userId, UserUpdateDTO updateDTO) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (StringUtils.hasText(updateDTO.getRealName())) {
            user.setRealName(updateDTO.getRealName());
        }
        if (StringUtils.hasText(updateDTO.getDepartment())) {
            user.setDepartment(updateDTO.getDepartment());
        }
        if (StringUtils.hasText(updateDTO.getRoleCode())) {
            SysRole role = sysRoleMapper.selectOne(
                    new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, updateDTO.getRoleCode()));
            if (role == null) {
                throw new BusinessException(400, "角色不存在: " + updateDTO.getRoleCode());
            }
            user.setRoleId(role.getRoleId());
        }
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
    }

    @Override
    @Transactional
    public void updatePassword(Long userId, PasswordUpdateDTO dto, Long operatorId, boolean isAdmin) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (!isAdmin) {
            if (!StringUtils.hasText(dto.getOldPassword())) {
                throw new BusinessException(400, "旧密码不能为空");
            }
            if (!passwordEncoder.matches(dto.getOldPassword(), user.getPasswordHash())) {
                throw new BusinessException(400, "旧密码错误");
            }
        }
        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
    }

    @Override
    @Transactional
    public void updateStatus(Long userId, Integer status) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        user.setStatus(status);
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        sysUserMapper.deleteById(userId);
    }

    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setUserId(user.getUserId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setDepartment(user.getDepartment());
        vo.setStatus(user.getStatus());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreatedAt(user.getCreatedAt());
        if (user.getRoleCode() != null) {
            vo.setRoleCode(user.getRoleCode());
        } else if (user.getRoleId() != null) {
            SysRole role = sysRoleMapper.selectById(user.getRoleId());
            if (role != null) {
                vo.setRoleCode(role.getRoleCode());
                vo.setRoleName(role.getRoleName());
            }
        }
        return vo;
    }
}
