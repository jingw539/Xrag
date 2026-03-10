package com.hospital.xray.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 操作日志查询条件 DTO
 */
@Data
public class LogQueryDTO {

    /**
     * 用户ID（可选）
     */
    private Long userId;

    /**
     * 操作类型（可选）
     */
    private String operationType;

    /**
     * 开始时间（可选）
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    /**
     * 结束时间（可选）
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;

    /**
     * 执行结果：success / error
     */
    private String resultType;

    /**
     * 错误关键字（从 detail 中模糊匹配）
     */
    private String errorKeyword;

    /**
     * 页码，默认第1页
     */
    @Min(value = 1, message = "页码必须大于0")
    private Integer page = 1;

    /**
     * 每页大小，默认20条
     */
    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = 100, message = "每页数量不能超过100")
    private Integer pageSize = 20;
}
