package com.hospital.xray.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConfigVO {

    private Long configId;
    private String configKey;
    private String configValue;
    private String description;
    private LocalDateTime updatedAt;
}
