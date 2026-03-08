package com.hospital.xray.controller;

import com.hospital.xray.annotation.OperationLog;
import com.hospital.xray.common.Result;
import com.hospital.xray.dto.ConfigUpdateDTO;
import com.hospital.xray.dto.ConfigVO;
import com.hospital.xray.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "系统配置", description = "检索阈值、模型路径等系统参数管理")
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @Operation(summary = "查询所有配置项")
    @GetMapping
    public Result<List<ConfigVO>> listAll() {
        return Result.success(configService.listAll());
    }

    @Operation(summary = "查询单个配置项")
    @GetMapping("/{key}")
    public Result<ConfigVO> getByKey(@PathVariable String key) {
        return Result.success(configService.getByKey(key));
    }

    @Operation(summary = "更新配置项")
    @PutMapping("/{key}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @OperationLog(type = "UPDATE_CONFIG")
    public Result<Void> update(@PathVariable String key,
                               @Valid @RequestBody ConfigUpdateDTO dto) {
        configService.update(key, dto);
        return Result.success();
    }
}
