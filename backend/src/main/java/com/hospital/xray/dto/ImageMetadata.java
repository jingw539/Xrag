package com.hospital.xray.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageMetadata {
    private Integer width;
    private Integer height;
    private String format;
    private Double pixelSpacingXmm;
    private Double pixelSpacingYmm;
}
