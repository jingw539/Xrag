package com.hospital.xray.controller;

import com.hospital.xray.annotation.OperationLog;
import com.hospital.xray.common.Result;
import com.hospital.xray.dto.AlertQueryDTO;
import com.hospital.xray.dto.AlertRespondDTO;
import com.hospital.xray.dto.AlertVO;
import com.hospital.xray.dto.PageResult;
import com.hospital.xray.service.AlertService;
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

@Tag(name = "危急值预警", description = "危急值检测、预警列表、医师响应处理")
@Validated
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @Operation(summary = "查询预警列表")
    @GetMapping
    public Result<PageResult<AlertVO>> listAlerts(AlertQueryDTO queryDTO) {
        return Result.success(alertService.listAlerts(queryDTO));
    }

    @Operation(summary = "查询预警详情")
    @GetMapping("/{alertId}")
    public Result<AlertVO> getById(@PathVariable String alertId) {
        return Result.success(alertService.getById(Long.parseLong(alertId)));
    }

    @Operation(summary = "处理预警（确认/上转/驳回）")
    @PostMapping("/{alertId}/respond")
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'QC', 'ADMIN')")
    @OperationLog(type = "RESPOND_ALERT")
    public Result<Void> respond(@PathVariable String alertId,
                                @Valid @RequestBody AlertRespondDTO dto) {
        alertService.respond(Long.parseLong(alertId), dto, SecurityUtils.getCurrentUserId());
        return Result.success(null, "预警已处理");
    }

    @Operation(summary = "查询病例的所有预警")
    @GetMapping("/case/{caseId}")
    public Result<List<AlertVO>> getByCaseId(@PathVariable String caseId) {
        return Result.success(alertService.getByCaseId(Long.parseLong(caseId)));
    }

    @Operation(summary = "获取待处理预警数量")
    @GetMapping("/pending/count")
    public Result<Long> countPending() {
        return Result.success(alertService.countPending());
    }

    @Operation(summary = "获取预警统计数据")
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStats() {
        return Result.success(alertService.getAlertStats());
    }
}
