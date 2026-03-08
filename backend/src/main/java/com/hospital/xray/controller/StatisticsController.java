package com.hospital.xray.controller;

import com.hospital.xray.common.Result;
import com.hospital.xray.dto.StatisticsVO;
import com.hospital.xray.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "质控统计", description = "报告生成趋势、CheXbert分数趋势、质量评级分布")
@Validated
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @Operation(summary = "获取系统概览统计")
    @GetMapping("/overview")
    public Result<StatisticsVO> getOverview() {
        return Result.success(statisticsService.getOverview());
    }

    @Operation(summary = "获取报告生成趋势")
    @GetMapping("/report-trend")
    public Result<List<Map<String, Object>>> getReportTrend(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @Pattern(regexp = "^(day|week|month)$", message = "groupBy 只能为 day、week 或 month")
            @RequestParam(defaultValue = "day") String groupBy) {
        return Result.success(statisticsService.getReportTrend(startDate, endDate, groupBy));
    }

    @Operation(summary = "获取CheXbert评测趋势")
    @GetMapping("/eval-trend")
    public Result<List<Map<String, Object>>> getEvalTrend(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @Pattern(regexp = "^(day|week|month)$", message = "groupBy 只能为 day、week 或 month")
            @RequestParam(defaultValue = "month") String groupBy) {
        return Result.success(statisticsService.getEvalTrend(startDate, endDate, groupBy));
    }

    @Operation(summary = "获取模型版本对比数据")
    @GetMapping("/model-comparison")
    public Result<List<Map<String, Object>>> getModelVersionComparison() {
        return Result.success(statisticsService.getModelVersionComparison());
    }

    @Operation(summary = "获取14类病理标签各自F1统计")
    @GetMapping("/per-label-stats")
    public Result<List<Map<String, Object>>> getPerLabelStats() {
        return Result.success(statisticsService.getPerLabelStats());
    }

    @Operation(summary = "获取质量异常报告列表（需要关注的低质量报告）")
    @GetMapping("/quality-issues")
    public Result<List<Map<String, Object>>> getQualityIssues(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(statisticsService.getQualityIssues(limit));
    }
}
