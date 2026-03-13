package com.hospital.xray.service;

import com.hospital.xray.dto.EvaluationMetricVO;
import com.hospital.xray.dto.EvaluationRunVO;

import java.util.List;
import java.util.Map;

public interface EvaluationService {

    List<EvaluationRunVO> listRuns(String datasetName, String modelName);

    List<EvaluationMetricVO> listMetrics(Long runId, String scope);

    List<Map<String, Object>> compareModels(String datasetName, String metricName, String tagName);
}
