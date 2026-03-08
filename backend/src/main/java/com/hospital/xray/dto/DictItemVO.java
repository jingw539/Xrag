package com.hospital.xray.dto;

import lombok.Data;

@Data
public class DictItemVO {

    private Long itemId;
    private String itemCode;
    private String itemName;
    private Integer sortOrder;
}
