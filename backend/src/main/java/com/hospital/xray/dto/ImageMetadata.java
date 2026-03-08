package com.hospital.xray.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图像元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageMetadata {
    
    /**
     * 图像宽度（像素）
     */
    private Integer width;
    
    /**
     * 图像高度（像素）
     */
    private Integer height;
    
    /**
     * 文件格式
     */
    private String format;
}
