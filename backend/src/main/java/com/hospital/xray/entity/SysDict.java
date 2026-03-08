package com.hospital.xray.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_dict")
public class SysDict {

    @TableId("dict_id")
    private Long dictId;

    @TableField("dict_code")
    private String dictCode;

    @TableField("dict_name")
    private String dictName;
}
