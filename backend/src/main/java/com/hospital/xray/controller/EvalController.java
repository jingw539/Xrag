package com.hospital.xray.controller;

import com.hospital.xray.annotation.OperationLog;
import com.hospital.xray.common.Result;
import com.hospital.xray.dto.EvalResultVO;
import com.hospital.xray.service.EvalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "CheXbert评测", description = "14类病理标签提取与临床一致性评测")
@RestController
@RequestMapping("/api/eval")
@RequiredArgsConstructor
public class EvalController {

    private final EvalService evalService;

    @Operation(summary = "触发CheXbert评测")
    @PostMapping("/reports/{reportId}")
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'QC', 'ADMIN')")
    @OperationLog(type = "CHEXBERT_EVAL")
    public Result<EvalResultVO> evaluate(@PathVariable String reportId) {
        return Result.success(evalService.evaluate(Long.parseLong(reportId)));
    }

    @Operation(summary = "查询报告的评测结果列表")
    @GetMapping("/reports/{reportId}")
    public Result<List<EvalResultVO>> getByReportId(@PathVariable String reportId) {
        return Result.success(evalService.getByReportId(Long.parseLong(reportId)));
    }

    @Operation(summary = "查询评测统计数据")
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getEvalStatistics() {
        return Result.success(evalService.getEvalStatistics());
    }
}
