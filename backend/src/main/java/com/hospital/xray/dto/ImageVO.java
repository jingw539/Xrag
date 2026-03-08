package com.hospital.xray.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 影像信息视图对象
 */
@Data
public class ImageVO {
    
    /**
     * 影像ID
     */
    private Long imageId;
    
    /**
     * 所属病例ID
     */
    private Long caseId;
    
    /**
     * 原始文件名
     */
    private String fileName;
    
    /**
     * 文件类型
     */
    private String fileType;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 投照体位
     */
    private String viewPosition;
    
    /**
     * 图像宽度
     */
    private Integer imgWidth;
    
    /**
     * 图像高度
     */
    private Integer imgHeight;
    
    /**
     * 拍摄时间
     */
    private LocalDateTime shootTime;
    
    /**
     * 缩略图URL
     */
    private String thumbnailUrl;
    
    /**
     * 完整图像URL
     */
    private String fullUrl;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
