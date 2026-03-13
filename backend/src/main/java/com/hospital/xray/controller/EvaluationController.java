package com.hospital.xray.controller;

import com.hospital.xray.dto.EvaluationMetricVO;
import com.hospital.xray.dto.EvaluationRunVO;
import com.hospital.xray.dto.Result;
import com.hospital.xray.service.EvaluationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
@Tag(name = "Evaluation", description = "Evaluation runs and model comparisons")
public class EvaluationController {

    private final EvaluationService evaluationService;

    @GetMapping("/runs")
    public Result<List<EvaluationRunVO>> listRuns(@RequestParam(required = false) String datasetName,
                                                  @RequestParam(required = false) String modelName) {
        return Result.success(evaluationService.listRuns(datasetName, modelName));
    }

    @GetMapping("/runs/{runId}/metrics")
    public Result<List<EvaluationMetricVO>> listMetrics(@PathVariable Long runId,
                                                        @RequestParam(required = false) String scope) {
        return Result.success(evaluationService.listMetrics(runId, scope));
    }

    @GetMapping("/compare")
    public Result<List<Map<String, Object>>> compare(@RequestParam(required = false) String datasetName,
                                                     @RequestParam String metricName,
                                                     @RequestParam(required = false) String tagName) {
        return Result.success(evaluationService.compareModels(datasetName, metricName, tagName));
    }
}
