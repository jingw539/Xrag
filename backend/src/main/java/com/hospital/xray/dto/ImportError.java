package com.hospital.xray.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 导入错误信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportError {
    
    /**
     * 错误行号
     */
    private Integer row;
    
    /**
     * 错误原因
     */
    private String reason;
}
