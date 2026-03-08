package com.hospital.xray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report_edit_history")
public class ReportEditHistory {

    @TableId(value = "history_id", type = IdType.ASSIGN_ID)
    private Long historyId;

    @TableField("report_id")
    private Long reportId;

    @TableField("editor_id")
    private Long editorId;

    @TableField("findings_before")
    private String findingsBefore;

    @TableField("findings_after")
    private String findingsAfter;

    @TableField("impression_before")
    private String impressionBefore;

    @TableField("impression_after")
    private String impressionAfter;

    @TableField("edit_note")
    private String editNote;

    @TableField("edit_time")
    private LocalDateTime editTime;
}
