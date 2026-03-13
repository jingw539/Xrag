package com.hospital.xray.controller;

import com.hospital.xray.common.Result;
import com.hospital.xray.dto.LogQueryDTO;
import com.hospital.xray.dto.OperationLogVO;
import com.hospital.xray.dto.PageResult;
import com.hospital.xray.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Tag(name = "操作日志", description = "系统操作日志查询与导出接口")
@Validated
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService operationLogService;

    @Operation(summary = "查询操作日志")
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public Result<PageResult<OperationLogVO>> listLogs(LogQueryDTO query) {
        return Result.success(operationLogService.listLogs(query));
    }

    @Operation(summary = "导出操作日志")
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ByteArrayResource> exportLogs(LogQueryDTO query) {
        byte[] content = operationLogService.exportLogs(query);
        String fileName = "operation-logs-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .contentLength(content.length)
                .body(new ByteArrayResource(content));
    }
}
