package com.hospital.xray.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志视图对象 VO
 */
@Data
public class OperationLogVO {

    /**
     * 日志ID
     */
    private Long logId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 操作类型
     */
    private String operationType;

    /**
     * 操作目标ID
     */
    private String targetId;

    /**
     * 操作详情
     */
    private String detail;

    /**
     * 失败原因
     */
    private String errorMsg;

    /**
     * 客户端IP
     */
    private String clientIp;

    /**
     * API路径
     */
    private String apiPath;

    /**
     * 操作耗时（毫秒）
     */
    private Integer elapsedMs;

    /**
     * 操作时间
     */
    private LocalDateTime createdAt;
}
