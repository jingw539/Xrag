package com.hospital.xray.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_dict_item")
public class SysDictItem {

    @TableId("item_id")
    private Long itemId;

    @TableField("dict_id")
    private Long dictId;

    @TableField("item_code")
    private String itemCode;

    @TableField("item_name")
    private String itemName;

    @TableField("sort_order")
    private Integer sortOrder;
}
