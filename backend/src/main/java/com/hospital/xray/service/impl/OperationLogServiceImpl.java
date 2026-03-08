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

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 操作日志服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {
    
    private final SysOperationLogMapper operationLogMapper;
    private final SysUserMapper sysUserMapper;
    
    /**
     * 记录操作日志
     * 在主线程采集 SecurityContext/RequestContext，再通过 saveLog 异步写入
     */
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
    
    /**
     * 保存操作日志实体
     */
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
    
    /**
     * 记录错误日志
     * 在主线程采集 SecurityContext/RequestContext，再通过 saveLog 异步写入
     */
    @Override
    public void logError(String operationType, String errorMessage) {
        SysOperationLog entity = new SysOperationLog();
        entity.setUserId(SecurityUtils.getCurrentUserId());
        entity.setOperationType(operationType);
        entity.setDetail("操作失败: " + errorMessage);
        entity.setClientIp(RequestUtils.getClientIp());
        entity.setApiPath(RequestUtils.getRequestPath());
        entity.setCreatedAt(LocalDateTime.now());
        saveLog(entity);
    }
    
    /**
     * 分页查询操作日志
     */
    @Override
    public PageResult<OperationLogVO> listLogs(LogQueryDTO query) {
        // 创建分页对象
        Page<SysOperationLog> page = new Page<>(query.getPage(), query.getPageSize());
        
        // 构建查询条件
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getUserId() != null, SysOperationLog::getUserId, query.getUserId())
               .eq(query.getOperationType() != null && !query.getOperationType().isEmpty(), 
                   SysOperationLog::getOperationType, query.getOperationType())
               .between(query.getStartTime() != null && query.getEndTime() != null,
                       SysOperationLog::getCreatedAt, query.getStartTime(), query.getEndTime())
               .orderByDesc(SysOperationLog::getCreatedAt);
        
        // 执行分页查询
        Page<SysOperationLog> result = operationLogMapper.selectPage(page, wrapper);
        
        List<Long> userIds = result.getRecords().stream()
                .map(SysOperationLog::getUserId).filter(java.util.Objects::nonNull)
                .distinct().collect(Collectors.toList());
        java.util.Map<Long, String> userNameMap = userIds.isEmpty() ? java.util.Map.of() :
                sysUserMapper.selectBatchIds(userIds).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                u -> u.getUserId(),
                                u -> u.getRealName() != null ? u.getRealName() : ""));

        List<OperationLogVO> voList = result.getRecords().stream()
                .map(e -> convertToVO(e, userNameMap))
                .collect(Collectors.toList());
        
        return PageResult.of(result.getTotal(), voList);
    }
    
    /**
     * 将实体转换为 VO
     */
    private OperationLogVO convertToVO(SysOperationLog entity) {
        return convertToVO(entity, null);
    }

    private OperationLogVO convertToVO(SysOperationLog entity, java.util.Map<Long, String> userNameCache) {
        OperationLogVO vo = new OperationLogVO();
        BeanUtils.copyProperties(entity, vo);
        if (entity.getUserId() != null) {
            if (userNameCache != null) {
                vo.setUserName(userNameCache.get(entity.getUserId()));
            } else {
                var user = sysUserMapper.selectById(entity.getUserId());
                if (user != null) vo.setUserName(user.getRealName());
            }
        }
        return vo;
    }
}
