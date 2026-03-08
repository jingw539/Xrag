package com.hospital.xray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.xray.dto.AlertQueryDTO;
import com.hospital.xray.dto.AlertRespondDTO;
import com.hospital.xray.dto.AlertVO;
import com.hospital.xray.dto.PageResult;
import com.hospital.xray.entity.CriticalAlert;
import com.hospital.xray.exception.BusinessException;
import com.hospital.xray.mapper.CriticalAlertMapper;
import com.hospital.xray.mapper.SysUserMapper;
import com.hospital.xray.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private final CriticalAlertMapper alertMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public PageResult<AlertVO> listAlerts(AlertQueryDTO queryDTO) {
        LambdaQueryWrapper<CriticalAlert> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(queryDTO.getAlertStatus())) {
            wrapper.eq(CriticalAlert::getAlertStatus, queryDTO.getAlertStatus());
        }
        if (StringUtils.hasText(queryDTO.getLabelType())) {
            wrapper.eq(CriticalAlert::getLabelType, queryDTO.getLabelType());
        }
        if (queryDTO.getCaseId() != null) {
            wrapper.eq(CriticalAlert::getCaseId, queryDTO.getCaseId());
        }
        if (StringUtils.hasText(queryDTO.getStartDate())) {
            wrapper.ge(CriticalAlert::getAlertTime, LocalDate.parse(queryDTO.getStartDate()).atStartOfDay());
        }
        if (StringUtils.hasText(queryDTO.getEndDate())) {
            wrapper.le(CriticalAlert::getAlertTime, LocalDate.parse(queryDTO.getEndDate()).atTime(23, 59, 59));
        }
        wrapper.orderByDesc(CriticalAlert::getAlertTime);

        Page<CriticalAlert> page = new Page<>(queryDTO.getPage(), queryDTO.getPageSize());
        Page<CriticalAlert> result = alertMapper.selectPage(page, wrapper);

        List<Long> responderIds = result.getRecords().stream()
                .map(CriticalAlert::getResponderId).filter(java.util.Objects::nonNull)
                .distinct().collect(Collectors.toList());
        Map<Long, String> responderNameMap = responderIds.isEmpty() ? Map.of() :
                sysUserMapper.selectBatchIds(responderIds).stream()
                        .collect(Collectors.toMap(u -> u.getUserId(),
                                u -> u.getRealName() != null ? u.getRealName() : ""));

        List<AlertVO> list = result.getRecords().stream()
                .map(a -> toVO(a, responderNameMap)).collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), list);
    }

    @Override
    public AlertVO getById(Long alertId) {
        CriticalAlert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw new BusinessException(404, "预警记录不存在");
        }
        return toVO(alert);
    }

    @Override
    @Transactional
    public void respond(Long alertId, AlertRespondDTO dto, Long responderId) {
        CriticalAlert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw new BusinessException(404, "预警记录不存在");
        }
        if (!"PENDING".equals(alert.getAlertStatus())) {
            throw new BusinessException(400, "该预警已处理，不可重复操作");
        }
        alert.setAlertStatus(dto.getAction());
        alert.setResponseAction(dto.getAction());
        alert.setResponderId(responderId);
        alert.setResponseTime(LocalDateTime.now());
        alert.setResponseNote(dto.getNote());
        alertMapper.updateById(alert);
    }

    @Override
    public List<AlertVO> getByCaseId(Long caseId) {
        LambdaQueryWrapper<CriticalAlert> wrapper = new LambdaQueryWrapper<CriticalAlert>()
                .eq(CriticalAlert::getCaseId, caseId)
                .orderByDesc(CriticalAlert::getAlertTime);
        return alertMapper.selectList(wrapper).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public Long countPending() {
        return alertMapper.countPending();
    }

    @Override
    public Map<String, Object> getAlertStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("pendingCount", alertMapper.countPending());
        stats.put("typeDistribution", alertMapper.selectAlertTypeStats());
        stats.put("avgAckSeconds", alertMapper.avgResponseSeconds("ACKNOWLEDGED"));
        return stats;
    }

    private AlertVO toVO(CriticalAlert alert) {
        return toVO(alert, null);
    }

    private AlertVO toVO(CriticalAlert alert, Map<Long, String> responderNameCache) {
        AlertVO vo = new AlertVO();
        vo.setAlertId(alert.getAlertId());
        vo.setCaseId(alert.getCaseId());
        vo.setReportId(alert.getReportId());
        vo.setLabelType(alert.getLabelType());
        vo.setLabelProb(alert.getLabelProb());
        vo.setAlertStatus(alert.getAlertStatus());
        vo.setResponderId(alert.getResponderId());
        vo.setResponseAction(alert.getResponseAction());
        vo.setResponseTime(alert.getResponseTime());
        vo.setResponseNote(alert.getResponseNote());
        vo.setAlertTime(alert.getAlertTime());
        if (alert.getResponderId() != null) {
            if (responderNameCache != null) {
                vo.setResponderName(responderNameCache.get(alert.getResponderId()));
            } else {
                var responder = sysUserMapper.selectById(alert.getResponderId());
                if (responder != null) vo.setResponderName(responder.getRealName());
            }
        }
        return vo;
    }
}
