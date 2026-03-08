package com.hospital.xray.service;

import com.hospital.xray.dto.EvalResultVO;
import com.hospital.xray.dto.PageResult;
import com.hospital.xray.dto.StatisticsVO;

import java.util.List;
import java.util.Map;

public interface EvalService {

    EvalResultVO evaluate(Long reportId);

    List<EvalResultVO> getByReportId(Long reportId);

    Map<String, Object> getEvalStatistics();
}
