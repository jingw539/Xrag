package com.hospital.xray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统操作日志实体类
 * 对应数据库表 sys_operation_log
 */
@Data
@TableName("sys_operation_log")
public class SysOperationLog {

    /**
     * 日志唯一标识
     */
    @TableId(value = "log_id", type = IdType.ASSIGN_ID)
    private Long logId;

    /**
     * 操作用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 操作类型：
     * LOGIN=登录
     * CASE_VIEW=查看病例
     * CASE_CREATE=创建病例
     * CASE_UPDATE=更新病例
     * CASE_DELETE=删除病例
     * IMAGE_UPLOAD=上传影像
     * TYPICAL_MARK=标记典型病例
     */
    @TableField("operation_type")
    private String operationType;

    /**
     * 操作目标ID（如病例ID、影像ID等）
     */
    @TableField("target_id")
    private String targetId;

    /**
     * 操作详情描述
     */
    @TableField("detail")
    private String detail;

    /**
     * 客户端IP地址
     */
    @TableField("client_ip")
    private String clientIp;

    /**
     * API请求路径
     */
    @TableField("api_path")
    private String apiPath;

    /**
     * 操作耗时（毫秒）
     */
    @TableField("elapsed_ms")
    private Integer elapsedMs;

    /**
     * 操作时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
