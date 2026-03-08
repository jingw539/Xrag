package com.hospital.xray.controller;

import com.hospital.xray.annotation.OperationLog;
import com.hospital.xray.common.Result;
import com.hospital.xray.dto.RetrievalResultVO;
import com.hospital.xray.service.RetrievalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "RAG检索", description = "MedCLIP+FAISS相似病例检索")
@RestController
@RequestMapping("/api/retrieval")
@RequiredArgsConstructor
public class RetrievalController {

    private final RetrievalService retrievalService;

    @Operation(summary = "执行RAG检索")
    @PostMapping("/search")
    @OperationLog(type = "RAG_SEARCH")
    public Result<RetrievalResultVO> search(@RequestParam String caseId,
                                            @RequestParam String imageId,
                                            @RequestParam(defaultValue = "3") Integer topK) {
        return Result.success(retrievalService.search(Long.parseLong(caseId), Long.parseLong(imageId), topK));
    }

    @Operation(summary = "查询检索记录详情")
    @GetMapping("/{retrievalId}")
    public Result<RetrievalResultVO> getById(@PathVariable String retrievalId) {
        return Result.success(retrievalService.getById(Long.parseLong(retrievalId)));
    }

    @Operation(summary = "查询病例的检索历史")
    @GetMapping("/case/{caseId}")
    public Result<List<RetrievalResultVO>> listByCaseId(@PathVariable String caseId) {
        return Result.success(retrievalService.listByCaseId(Long.parseLong(caseId)));
    }
}
