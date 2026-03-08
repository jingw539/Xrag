package com.hospital.xray.controller;

import com.hospital.xray.common.Result;
import com.hospital.xray.dto.LogQueryDTO;
import com.hospital.xray.dto.OperationLogVO;
import com.hospital.xray.dto.PageResult;
import com.hospital.xray.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志控制器
 * 提供操作日志查询接口
 */
@Tag(name = "操作日志", description = "系统操作日志的查询功能")
@Validated
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class OperationLogController {
    
    private final OperationLogService operationLogService;
    
    /**
     * 查询操作日志列表
     * 支持多条件筛选和分页
     * 
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "查询操作日志", description = "支持多条件筛选和分页查询操作日志，需要管理员或质控角色权限")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'QC')")
    public Result<PageResult<OperationLogVO>> listLogs(LogQueryDTO query) {
        PageResult<OperationLogVO> result = operationLogService.listLogs(query);
        return Result.success(result);
    }
}
