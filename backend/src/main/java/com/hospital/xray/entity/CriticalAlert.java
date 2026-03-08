package com.hospital.xray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("critical_alert")
public class CriticalAlert {

    @TableId(value = "alert_id", type = IdType.ASSIGN_ID)
    private Long alertId;

    @TableField("case_id")
    private Long caseId;

    @TableField("report_id")
    private Long reportId;

    @TableField("label_type")
    private String labelType;

    @TableField("label_prob")
    private BigDecimal labelProb;

    @TableField("alert_status")
    private String alertStatus;

    @TableField("responder_id")
    private Long responderId;

    @TableField("response_action")
    private String responseAction;

    @TableField("response_time")
    private LocalDateTime responseTime;

    @TableField("response_note")
    private String responseNote;

    @TableField("alert_time")
    private LocalDateTime alertTime;
}
