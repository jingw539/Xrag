package com.hospital.xray.dto;

import lombok.Data;

@Data
public class AnnotationCreateDTO {
    private Long imageId;
    private Long reportId;
    private String annoType;
    private String label;
    private String remark;
    private Double x;
    private Double y;
    private Double width;
    private Double height;
    private String color;
}
