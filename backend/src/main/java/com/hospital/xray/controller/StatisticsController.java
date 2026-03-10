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

@Tag(name = "Statistics", description = "Report generation overview and trends")
@Validated
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @Operation(summary = "Get system overview")
    @GetMapping("/overview")
    public Result<StatisticsVO> getOverview() {
        return Result.success(statisticsService.getOverview());
    }

    @Operation(summary = "Get report generation trend")
    @GetMapping("/report-trend")
    public Result<List<Map<String, Object>>> getReportTrend(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @Pattern(regexp = "^(day|week|month)$", message = "groupBy must be day, week or month")
            @RequestParam(defaultValue = "day") String groupBy) {
        return Result.success(statisticsService.getReportTrend(startDate, endDate, groupBy));
    }
}
