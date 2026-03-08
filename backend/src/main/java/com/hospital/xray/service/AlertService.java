package com.hospital.xray.service;

import com.hospital.xray.dto.AlertQueryDTO;
import com.hospital.xray.dto.AlertRespondDTO;
import com.hospital.xray.dto.AlertVO;
import com.hospital.xray.dto.PageResult;

import java.util.List;
import java.util.Map;

public interface AlertService {

    PageResult<AlertVO> listAlerts(AlertQueryDTO queryDTO);

    AlertVO getById(Long alertId);

    void respond(Long alertId, AlertRespondDTO dto, Long responderId);

    List<AlertVO> getByCaseId(Long caseId);

    Long countPending();

    Map<String, Object> getAlertStats();
}
