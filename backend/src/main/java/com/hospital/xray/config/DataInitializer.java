package com.hospital.xray.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.xray.entity.SysRole;
import com.hospital.xray.entity.SysUser;
import com.hospital.xray.mapper.SysRoleMapper;
import com.hospital.xray.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 应用启动后自动初始化基础数据（角色 + 默认管理员）
 * 幂等：若数据已存在则跳过
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        try {
            initRoles();
            initAdminUser();
        } catch (Exception e) {
            log.error("[DataInitializer] 初始化失败，请检查数据库连接: {}", e.getMessage());
        }
    }

    private void initRoles() {
        List<Object[]> roles = List.of(
                new Object[]{"ADMIN",  "系统管理员"},
                new Object[]{"DOCTOR", "放射科医生"}
        );
        for (Object[] r : roles) {
            String code = (String) r[0];
            String name = (String) r[1];
            Long count = roleMapper.selectCount(
                    new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, code));
            if (count == 0) {
                SysRole role = new SysRole();
                role.setRoleCode(code);
                role.setRoleName(name);
                roleMapper.insert(role);
                log.info("[DataInitializer] 角色已创建: {}", code);
            }
        }
    }

    private void initAdminUser() {
        Long count = userMapper.selectCount(null);
        if (count > 0) return;

        SysRole adminRole = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, "ADMIN"));
        if (adminRole == null) {
            log.warn("[DataInitializer] ADMIN 角色不存在，跳过管理员创建");
            return;
        }

        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode("Admin@123"));
        admin.setRealName("系统管理员");
        admin.setRoleId(adminRole.getRoleId());
        admin.setDepartment("信息科");
        admin.setStatus(1);
        admin.setCreatedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(admin);

        log.info("[DataInitializer] 默认管理员账号已创建 — 用户名: admin  密码: Admin@123");
        log.warn("[DataInitializer] ⚠ 请登录后立即修改默认密码！");
    }
}
