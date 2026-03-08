package com.hospital.xray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_refresh_token")
public class SysRefreshToken {

    @TableId(value = "token_id", type = IdType.ASSIGN_ID)
    private Long tokenId;

    @TableField("user_id")
    private Long userId;

    @TableField("token_hash")
    private String tokenHash;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("revoked")
    private Integer revoked;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
