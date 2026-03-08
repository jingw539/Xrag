package com.hospital.xray.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {

    private Long userId;
    private String username;
    private String realName;
    private String roleCode;
    private String roleName;
    private String department;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
