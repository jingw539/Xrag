package com.hospital.xray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.xray.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT u.*, r.role_code FROM sys_user u " +
            "LEFT JOIN sys_role r ON u.role_id = r.role_id " +
            "WHERE u.username = #{username}")
    SysUser selectByUsernameWithRole(@Param("username") String username);

    @Select("SELECT u.*, r.role_code FROM sys_user u " +
            "LEFT JOIN sys_role r ON u.role_id = r.role_id " +
            "WHERE u.user_id = #{userId}")
    SysUser selectWithRoleByUserId(@Param("userId") Long userId);

    @Update("UPDATE sys_user SET last_login_at = #{loginAt}, updated_at = #{loginAt} WHERE user_id = #{userId}")
    int updateLastLoginAt(@Param("userId") Long userId, @Param("loginAt") LocalDateTime loginAt);

    @Select("<script>" +
            "SELECT u.*, r.role_code FROM sys_user u " +
            "LEFT JOIN sys_role r ON u.role_id = r.role_id " +
            "WHERE 1=1 " +
            "<if test=\"username != null and username != ''\"> AND u.username LIKE CONCAT('%', #{username}, '%') </if>" +
            "<if test=\"realName != null and realName != ''\"> AND u.real_name LIKE CONCAT('%', #{realName}, '%') </if>" +
            "<if test=\"department != null and department != ''\"> AND u.department LIKE CONCAT('%', #{department}, '%') </if>" +
            "<if test=\"status != null\"> AND u.status = #{status} </if>" +
            "<if test=\"roleCode != null and roleCode != ''\"> AND r.role_code = #{roleCode} </if>" +
            "ORDER BY u.created_at DESC" +
            "</script>")
    Page<SysUser> selectPageWithRole(Page<SysUser> page,
                                     @Param("username") String username,
                                     @Param("realName") String realName,
                                     @Param("department") String department,
                                     @Param("status") Integer status,
                                     @Param("roleCode") String roleCode);
}
