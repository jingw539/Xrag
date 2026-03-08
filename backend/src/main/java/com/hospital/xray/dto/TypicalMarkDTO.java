package com.hospital.xray.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 典型病例标记请求 DTO
 */
@Data
public class TypicalMarkDTO {
    
    /**
     * 是否典型病例：0=否，1=是
     */
    @NotNull(message = "是否典型病例不能为空（0=否，1=是）")
    private Integer isTypical;
    
    /**
     * 典型病例标签（多个标签用逗号分隔）
     */
    private String typicalTags;
    
    /**
     * 典型病例备注说明
     */
    private String typicalRemark;
}
