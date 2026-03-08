package com.hospital.xray.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 病例查询请求 DTO
 */
@Data
public class CaseQueryDTO {
    
    /**
     * 检查号（模糊查询）
     */
    private String examNo;
    
    /**
     * 患者匿名ID（模糊查询）
     */
    private String patientAnonId;
    
    /**
     * 开始时间
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;
    
    /**
     * 结束时间
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
    
    /**
     * 报告状态
     */
    private String reportStatus;
    
    /**
     * 科室
     */
    private String department;
    
    /**
     * 是否典型病例（0=否，1=是）
     */
    private Integer isTypical;

    /**
     * 按签发/编辑医生ID过滤（用于个人工作台视图）
     */
    private Long doctorId;

    /**
     * 是否只查看未绑定责任医生的病例
     */
    private Boolean unassignedOnly = false;
    
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
    
    /**
     * 排序字段，默认按检查时间
     */
    private String sortBy = "exam_time";
    
    /**
     * 排序方向，默认降序
     */
    private String sortOrder = "desc";
}
