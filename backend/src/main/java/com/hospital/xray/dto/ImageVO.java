package com.hospital.xray.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImageVO {
    private Long imageId;
    private Long caseId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String viewPosition;
    private Integer imgWidth;
    private Integer imgHeight;
    private Double pixelSpacingXmm;
    private Double pixelSpacingYmm;
    private LocalDateTime shootTime;
    private String thumbnailUrl;
    private String fullUrl;
    private LocalDateTime createdAt;
    private String examNo;
    private String patientAnonId;
    private LocalDateTime examTime;
}
