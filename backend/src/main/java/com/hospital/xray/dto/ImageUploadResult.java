package com.hospital.xray.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 影像上传结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageUploadResult {
    
    /**
     * 影像ID
     */
    private Long imageId;
    
    /**
     * MinIO中的文件路径
     */
    private String filePath;
    
    /**
     * 缩略图URL
     */
    private String thumbnailUrl;
    
    /**
     * 完整图像URL
     */
    private String fullUrl;
}
