package com.hospital.xray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.xray.dto.LogQueryDTO;
import com.hospital.xray.dto.OperationLogVO;
import com.hospital.xray.dto.PageResult;
import com.hospital.xray.entity.SysOperationLog;
import com.hospital.xray.mapper.SysOperationLogMapper;
import com.hospital.xray.mapper.SysUserMapper;
import com.hospital.xray.service.OperationLogService;
import com.hospital.xray.util.RequestUtils;
import com.hospital.xray.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 操作日志服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private static final String ERROR_MARKER = "错误:";

    private final SysOperationLogMapper operationLogMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public void log(String operationType, String targetId, String detail) {
        SysOperationLog entity = new SysOperationLog();
        entity.setUserId(SecurityUtils.getCurrentUserId());
        entity.setOperationType(operationType);
        entity.setTargetId(targetId);
        entity.setDetail(detail);
        entity.setClientIp(RequestUtils.getClientIp());
        entity.setApiPath(RequestUtils.getRequestPath());
        entity.setCreatedAt(LocalDateTime.now());
        saveLog(entity);
    }

    @Async
    @Override
    public void saveLog(SysOperationLog logEntity) {
        try {
            if (logEntity.getCreatedAt() == null) {
                logEntity.setCreatedAt(LocalDateTime.now());
            }
            if (logEntity.getTargetId() != null && logEntity.getTargetId().length() > 256) {
                logEntity.setTargetId(logEntity.getTargetId().substring(0, 256));
            }
            operationLogMapper.insert(logEntity);
        } catch (Exception e) {
            log.error("保存操作日志失败: {}", logEntity, e);
        }
    }

    @Override
    public void logError(String operationType, String errorMessage) {
        SysOperationLog entity = new SysOperationLog();
        entity.setUserId(SecurityUtils.getCurrentUserId());
        entity.setOperationType(operationType);
        entity.setDetail(ERROR_MARKER + " 操作失败: " + errorMessage);
        entity.setClientIp(RequestUtils.getClientIp());
        entity.setApiPath(RequestUtils.getRequestPath());
        entity.setCreatedAt(LocalDateTime.now());
        saveLog(entity);
    }

    @Override
    public PageResult<OperationLogVO> listLogs(LogQueryDTO query) {
        Page<SysOperationLog> page = new Page<>(query.getPage(), query.getPageSize());
        Page<SysOperationLog> result = operationLogMapper.selectPage(page, buildWrapper(query));
        List<OperationLogVO> voList = convertToVOList(result.getRecords());
        return PageResult.of(result.getTotal(), voList);
    }

    @Override
    public byte[] exportLogs(LogQueryDTO query) {
        List<SysOperationLog> records = operationLogMapper.selectList(buildWrapper(query));
        List<OperationLogVO> logs = convertToVOList(records);
        StringBuilder csv = new StringBuilder();
        csv.append("\uFEFF")
                .append("用户,操作类型,结果类型,错误信息,目标ID,API路径,客户端IP,耗时(ms),详情,时间\n");
        for (OperationLogVO logItem : logs) {
            csv.append(csvCell(logItem.getUserName()))
                    .append(',')
                    .append(csvCell(logItem.getOperationType()))
                    .append(',')
                    .append(csvCell(logItem.getErrorMsg() != null ? "失败" : "成功"))
                    .append(',')
                    .append(csvCell(logItem.getErrorMsg()))
                    .append(',')
                    .append(csvCell(logItem.getTargetId()))
                    .append(',')
                    .append(csvCell(logItem.getApiPath()))
                    .append(',')
                    .append(csvCell(logItem.getClientIp()))
                    .append(',')
                    .append(csvCell(logItem.getElapsedMs()))
                    .append(',')
                    .append(csvCell(logItem.getDetail()))
                    .append(',')
                    .append(csvCell(logItem.getCreatedAt()))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private LambdaQueryWrapper<SysOperationLog> buildWrapper(LogQueryDTO query) {
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getUserId() != null, SysOperationLog::getUserId, query.getUserId())
                .eq(query.getOperationType() != null && !query.getOperationType().isEmpty(),
                        SysOperationLog::getOperationType, query.getOperationType())
                .between(query.getStartTime() != null && query.getEndTime() != null,
                        SysOperationLog::getCreatedAt, query.getStartTime(), query.getEndTime())
                .like(query.getErrorKeyword() != null && !query.getErrorKeyword().isBlank(),
                        SysOperationLog::getDetail, query.getErrorKeyword())
                .and(query.getResultType() != null && !query.getResultType().isBlank(), builder -> {
                    if ("error".equalsIgnoreCase(query.getResultType())) {
                        builder.like(SysOperationLog::getDetail, ERROR_MARKER);
                    } else if ("success".equalsIgnoreCase(query.getResultType())) {
                        builder.notLike(SysOperationLog::getDetail, ERROR_MARKER);
                    }
                })
                .orderByDesc(SysOperationLog::getCreatedAt);
        return wrapper;
    }

    private List<OperationLogVO> convertToVOList(List<SysOperationLog> entities) {
        List<Long> userIds = entities.stream()
                .map(SysOperationLog::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, String> userNameMap = userIds.isEmpty()
                ? Map.of()
                : sysUserMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(
                        u -> u.getUserId(),
                        u -> u.getRealName() != null ? u.getRealName() : ""
                ));

        return entities.stream()
                .map(entity -> convertToVO(entity, userNameMap))
                .collect(Collectors.toList());
    }

    private OperationLogVO convertToVO(SysOperationLog entity, Map<Long, String> userNameCache) {
        OperationLogVO vo = new OperationLogVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setErrorMsg(extractErrorMessage(entity.getDetail()));
        if (entity.getUserId() != null) {
            vo.setUserName(userNameCache.get(entity.getUserId()));
        }
        return vo;
    }

    private String extractErrorMessage(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        int idx = detail.indexOf(ERROR_MARKER);
        if (idx < 0) {
            return null;
        }
        return detail.substring(idx + ERROR_MARKER.length()).trim();
    }

    private String csvCell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return '"' + text.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + '"';
    }
}
