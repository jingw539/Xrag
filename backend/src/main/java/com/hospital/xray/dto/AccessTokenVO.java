package com.hospital.xray.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccessTokenVO {

    private String accessToken;
    private Long expiresIn;
}
