package com.hospital.xray.controller;

import com.hospital.xray.annotation.OperationLog;
import com.hospital.xray.common.Result;
import com.hospital.xray.dto.*;
import com.hospital.xray.service.ReportService;
import com.hospital.xray.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "报告管理", description = "AI报告生成、草稿编辑、报告签发、修改历史")
@Validated
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "生成AI报告")
    @PostMapping("/generate")
    @OperationLog(type = "GENERATE_REPORT")
    public Result<ReportDetailVO> generate(@Valid @RequestBody ReportGenerateDTO dto) {
        return Result.success(reportService.generate(dto, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "重新生成AI报告")
    @PostMapping("/{reportId}/regenerate")
    @OperationLog(type = "REGENERATE_REPORT")
    public Result<ReportDetailVO> regenerate(@PathVariable String reportId) {
        return Result.success(reportService.regenerate(Long.parseLong(reportId), SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "保存报告草稿")
    @PutMapping("/{reportId}/draft")
    @OperationLog(type = "SAVE_DRAFT")
    public Result<Void> saveDraft(@PathVariable String reportId,
                                  @RequestBody ReportSaveDTO dto) {
        reportService.saveDraft(Long.parseLong(reportId), dto, SecurityUtils.getCurrentUserId());
        return Result.success();
    }

    @Operation(summary = "签发报告")
    @PostMapping("/{reportId}/sign")
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'ADMIN')")
    @OperationLog(type = "SIGN_REPORT")
    public Result<Void> sign(@PathVariable String reportId) {
        reportService.sign(Long.parseLong(reportId), SecurityUtils.getCurrentUserId());
        return Result.success(null, "报告签发成功");
    }

    @Operation(summary = "查询报告详情")
    @GetMapping("/{reportId}")
    public Result<ReportDetailVO> getById(@PathVariable String reportId) {
        return Result.success(reportService.getById(Long.parseLong(reportId)));
    }

    @Operation(summary = "查询报告列表")
    @GetMapping
    public Result<PageResult<ReportVO>> listReports(ReportQueryDTO queryDTO) {
        return Result.success(reportService.listReports(queryDTO));
    }

    @Operation(summary = "查询报告修改历史")
    @GetMapping("/{reportId}/history")
    public Result<List<ReportEditHistoryVO>> getEditHistory(@PathVariable String reportId) {
        return Result.success(reportService.getEditHistory(Long.parseLong(reportId)));
    }

    @Operation(summary = "AI报告润色")
    @PostMapping("/polish")
    @OperationLog(type = "POLISH_REPORT")
    public Result<Map<String, Object>> polishReport(@RequestBody Map<String, String> body) {
        return Result.success(reportService.polishReport(body.get("findings"), body.get("impression")));
    }

    @Operation(summary = "撤回已签发报告至编辑状态")
    @PostMapping("/{reportId}/revert")
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'ADMIN')")
    @OperationLog(type = "REVERT_REPORT")
    public Result<Void> revertToEdit(@PathVariable String reportId) {
        reportService.revertToEdit(Long.parseLong(reportId), SecurityUtils.getCurrentUserId());
        return Result.success(null, "报告已撤回，可重新编辑");
    }

    @Operation(summary = "获取AI审核建议")
    @PostMapping("/{reportId}/ai-advice")
    @OperationLog(type = "GET_AI_ADVICE")
    public Result<Map<String, Object>> getAiAdvice(@PathVariable String reportId) {
        return Result.success(reportService.getAiAdvice(Long.parseLong(reportId)));
    }
}
