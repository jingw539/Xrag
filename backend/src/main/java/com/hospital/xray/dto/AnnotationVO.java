package com.hospital.xray.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AnnotationVO {
    private Long annotationId;
    private Long imageId;
    private Long reportId;
    private String source;
    private String annoType;
    private String label;
    private String remark;
    private Double x;
    private Double y;
    private Double width;
    private Double height;
    private Double measuredWidthMm;
    private Double measuredHeightMm;
    private String compareStatus;
    private String compareNote;
    private String color;
    private Double confidence;
    private String createdByName;
    private LocalDateTime createdAt;
}
