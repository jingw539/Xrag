package com.hospital.xray.controller;

import com.hospital.xray.annotation.OperationLog;
import com.hospital.xray.common.Result;
import com.hospital.xray.dto.TermCorrectionVO;
import com.hospital.xray.service.TermService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "术语规范化", description = "报告术语检测与规范化建议")
@RestController
@RequestMapping("/api/terms")
@RequiredArgsConstructor
public class TermController {

    private final TermService termService;

    @Operation(summary = "分析报告术语规范性")
    @PostMapping("/reports/{reportId}/analyze")
    @OperationLog(type = "TERM_ANALYZE")
    public Result<List<TermCorrectionVO>> analyze(@PathVariable String reportId, @RequestBody(required = false) Map<String, String> payload) {
        String draftFindings = (payload != null) ? payload.get("findings") : null;
        String draftImpression = (payload != null) ? payload.get("impression") : null;
        return Result.success(termService.analyzeReport(Long.parseLong(reportId), draftFindings, draftImpression));
    }

    @Operation(summary = "查询报告的术语校正建议")
    @GetMapping("/reports/{reportId}")
    public Result<List<TermCorrectionVO>> getByReportId(@PathVariable String reportId) {
        return Result.success(termService.getByReportId(Long.parseLong(reportId)));
    }

    @Operation(summary = "采纳术语校正建议")
    @PostMapping("/{correctionId}/accept")
    @OperationLog(type = "ACCEPT_TERM")
    public Result<Void> accept(@PathVariable String correctionId) {
        termService.acceptCorrection(Long.parseLong(correctionId));
        return Result.success();
    }

    @Operation(summary = "忽略术语校正建议")
    @PostMapping("/{correctionId}/dismiss")
    @OperationLog(type = "DISMISS_TERM")
    public Result<Void> dismiss(@PathVariable String correctionId) {
        termService.dismissCorrection(Long.parseLong(correctionId));
        return Result.success();
    }
}
