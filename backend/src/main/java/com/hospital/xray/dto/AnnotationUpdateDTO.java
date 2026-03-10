package com.hospital.xray.dto;

import lombok.Data;

@Data
public class AnnotationUpdateDTO {
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
}
