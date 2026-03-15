package com.hospital.xray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.xray.dto.EvaluationMetricVO;
import com.hospital.xray.dto.EvaluationRunVO;
import com.hospital.xray.entity.EvaluationRun;
import com.hospital.xray.mapper.EvaluationMetricMapper;
import com.hospital.xray.mapper.EvaluationRunMapper;
import com.hospital.xray.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EvaluationServiceImpl implements EvaluationService {

    private final EvaluationRunMapper evaluationRunMapper;
    private final EvaluationMetricMapper evaluationMetricMapper;

    @Override
    public List<EvaluationRunVO> listRuns(String datasetName, String modelName) {
        try {
        LambdaQueryWrapper<EvaluationRun> qw = new LambdaQueryWrapper<>();
        if (datasetName != null && !datasetName.isBlank()) {
            qw.eq(EvaluationRun::getDatasetName, datasetName);
        }
        if (modelName != null && !modelName.isBlank()) {
            qw.eq(EvaluationRun::getModelName, modelName);
        }
        qw.orderByDesc(EvaluationRun::getCreatedAt);
        List<EvaluationRun> runs = evaluationRunMapper.selectList(qw);
        List<EvaluationRunVO> result = new ArrayList<>();
        for (EvaluationRun run : runs) {
            EvaluationRunVO vo = new EvaluationRunVO();
            BeanUtils.copyProperties(run, vo);
            result.add(vo);
        }
        return result;
        } catch (Exception e) {
            // Gracefully degrade when evaluation tables are missing or not populated.
            return List.of();
        }
    }

    @Override
    public List<EvaluationMetricVO> listMetrics(Long runId, String scope) {
        List<Map<String, Object>> rows;
        try {
            rows = evaluationMetricMapper.selectMetricsByRun(runId, scope);
        } catch (Exception e) {
            return List.of();
        }
        List<EvaluationMetricVO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            EvaluationMetricVO vo = new EvaluationMetricVO();
            Object scopeValue = row.get("scope");
            vo.setScope(scopeValue == null ? null : scopeValue.toString());
            Object tag = row.get("tag_name");
            vo.setTagName(tag == null ? null : tag.toString());
            Object metric = row.get("metric_name");
            vo.setMetricName(metric == null ? null : metric.toString());
            Object val = row.get("metric_value");
            vo.setMetricValue(val == null ? null : Double.valueOf(val.toString()));
            Object support = row.get("support");
            vo.setSupport(support == null ? null : Integer.valueOf(support.toString()));
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> compareModels(String datasetName, String metricName, String tagName) {
        try {
            return evaluationMetricMapper.selectModelCompare(datasetName, metricName, tagName);
        } catch (Exception e) {
            return List.of();
        }
    }
}
