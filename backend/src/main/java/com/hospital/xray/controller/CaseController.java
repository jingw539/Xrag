package com.hospital.xray.controller;

import com.hospital.xray.annotation.OperationLog;
import com.hospital.xray.common.Result;
import com.hospital.xray.dto.CaseCreateDTO;
import com.hospital.xray.dto.CaseDetailVO;
import com.hospital.xray.dto.CaseQueryDTO;
import com.hospital.xray.dto.CaseUpdateDTO;
import com.hospital.xray.dto.CaseVO;
import com.hospital.xray.dto.ImportResult;
import com.hospital.xray.dto.PageResult;
import com.hospital.xray.dto.TypicalMarkDTO;
import com.hospital.xray.service.CaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "病例管理", description = "病例查询、创建、更新、删除、典型病例标记与接诊")
@Validated
@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;

    @Operation(summary = "查询病例列表", description = "支持多条件筛选和分页查询病例")
    @GetMapping
    public Result<PageResult<CaseVO>> listCases(CaseQueryDTO query) {
        return Result.success(caseService.listCases(query));
    }

    @Operation(summary = "查询病例详情", description = "根据病例ID获取病例详细信息")
    @GetMapping("/{caseId}")
    public Result<CaseDetailVO> getCaseById(
            @Parameter(description = "病例ID", required = true) @PathVariable String caseId) {
        return Result.success(caseService.getCaseById(Long.parseLong(caseId)));
    }

    @Operation(summary = "接诊病例", description = "将未绑定病例绑定给当前登录医生")
    @PostMapping("/{caseId}/claim")
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'ADMIN')")
    @OperationLog(type = "CASE_CLAIM", detail = "接诊病例")
    public Result<CaseDetailVO> claimCase(
            @Parameter(description = "病例ID", required = true) @PathVariable String caseId) {
        return Result.success(caseService.claimCase(Long.parseLong(caseId)), "claim success");
    }

    @Operation(summary = "创建病例", description = "创建新的病例记录")
    @PostMapping
    @OperationLog(type = "CASE_CREATE", detail = "创建病例")
    public Result<Long> createCase(@Valid @RequestBody CaseCreateDTO dto) {
        return Result.success(caseService.createCase(dto));
    }

    @Operation(summary = "更新病例", description = "更新指定病例的信息")
    @PutMapping("/{caseId}")
    @OperationLog(type = "CASE_UPDATE", detail = "更新病例")
    public Result<Void> updateCase(
            @Parameter(description = "病例ID", required = true) @PathVariable String caseId,
            @Valid @RequestBody CaseUpdateDTO dto) {
        caseService.updateCase(Long.parseLong(caseId), dto);
        return Result.success();
    }

    @Operation(summary = "删除病例", description = "删除指定病例记录")
    @DeleteMapping("/{caseId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @OperationLog(type = "CASE_DELETE", detail = "删除病例")
    public Result<Void> deleteCase(
            @Parameter(description = "病例ID", required = true) @PathVariable String caseId) {
        caseService.deleteCase(Long.parseLong(caseId));
        return Result.success();
    }

    @Operation(summary = "标记典型病例", description = "标记或取消典型病例")
    @PostMapping("/{caseId}/typical")
    @OperationLog(type = "TYPICAL_MARK", detail = "标记典型病例")
    public Result<Void> markTypical(
            @Parameter(description = "病例ID", required = true) @PathVariable String caseId,
            @Valid @RequestBody TypicalMarkDTO dto) {
        caseService.markTypical(Long.parseLong(caseId), dto);
        return Result.success();
    }

    @Operation(summary = "批量导入病例", description = "通过CSV文件批量导入病例数据")
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('ADMIN')")
    @OperationLog(type = "CASE_IMPORT", detail = "批量导入病例")
    public Result<ImportResult> importCases(
            @Parameter(description = "CSV文件", required = true) @RequestParam("file") MultipartFile file) {
        return Result.success(caseService.importCases(file));
    }
}
