package com.hospital.xray.controller;

import com.hospital.xray.common.Result;
import com.hospital.xray.dto.DictItemVO;
import com.hospital.xray.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "数据字典", description = "报告状态、病理标签、投照体位等枚举值")
@RestController
@RequestMapping("/api/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    @Operation(summary = "根据字典编码查询字典项列表")
    @GetMapping("/{dictCode}/items")
    public Result<List<DictItemVO>> listItems(@PathVariable String dictCode) {
        return Result.success(dictService.listItemsByCode(dictCode));
    }
}
