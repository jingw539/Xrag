package com.hospital.xray.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserInfoVO {

    private Long userId;
    private String username;
    private String realName;
    private String roleCode;
    private String department;
    private Integer status;
    private LocalDateTime lastLoginAt;
}
