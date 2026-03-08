package com.hospital.xray.service;

import com.hospital.xray.dto.LogQueryDTO;
import com.hospital.xray.dto.OperationLogVO;
import com.hospital.xray.dto.PageResult;
import com.hospital.xray.entity.SysOperationLog;
import com.hospital.xray.mapper.SysOperationLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 操作日志服务单元测试
 */
@SpringBootTest
@Transactional
class OperationLogServiceTest {
    
    @Autowired
    private OperationLogService operationLogService;
    
    @Autowired
    private SysOperationLogMapper operationLogMapper;
    
    @Test
    void testLog_Success() throws InterruptedException {
        // 执行日志记录
        operationLogService.log("CASE_CREATE", "123456", "创建病例测试");
        
        // 等待异步操作完成
        Thread.sleep(1000);
        
        // 验证日志已记录（通过查询验证）
        LogQueryDTO query = new LogQueryDTO();
        query.setOperationType("CASE_CREATE");
        PageResult<OperationLogVO> result = operationLogService.listLogs(query);
        
        assertNotNull(result);
        assertTrue(result.getTotal() > 0);
    }
    
    @Test
    void testSaveLog_Success() throws InterruptedException {
        // 准备测试数据
        SysOperationLog log = new SysOperationLog();
        log.setUserId(1001L);
        log.setOperationType("CASE_UPDATE");
        log.setTargetId("789012");
        log.setDetail("更新病例信息");
        log.setClientIp("192.168.1.100");
        log.setApiPath("/api/cases/789012");
        log.setElapsedMs(150);
        log.setCreatedAt(LocalDateTime.now());
        
        // 执行保存
        operationLogService.saveLog(log);
        
        // 等待异步操作完成
        Thread.sleep(1000);
        
        // 验证日志已保存
        LogQueryDTO query = new LogQueryDTO();
        query.setOperationType("CASE_UPDATE");
        PageResult<OperationLogVO> result = operationLogService.listLogs(query);
        
        assertNotNull(result);
        assertTrue(result.getTotal() > 0);
    }
    
    @Test
    void testLogError_Success() throws InterruptedException {
        // 执行错误日志记录
        operationLogService.logError("CASE_DELETE", "病例不存在");
        
        // 等待异步操作完成
        Thread.sleep(1000);
        
        // 验证错误日志已记录
        LogQueryDTO query = new LogQueryDTO();
        query.setOperationType("CASE_DELETE");
        PageResult<OperationLogVO> result = operationLogService.listLogs(query);
        
        assertNotNull(result);
        assertTrue(result.getTotal() > 0);
        assertTrue(result.getList().stream()
                .anyMatch(vo -> vo.getDetail().contains("操作失败")));
    }
    
    @Test
    void testListLogs_WithNoFilters() {
        // 准备测试数据
        createTestLog("CASE_VIEW", "123456", 1001L);
        createTestLog("CASE_CREATE", "123457", 1002L);
        
        // 执行查询
        LogQueryDTO query = new LogQueryDTO();
        query.setPage(1);
        query.setPageSize(20);
        
        PageResult<OperationLogVO> result = operationLogService.listLogs(query);
        
        // 验证结果
        assertNotNull(result);
        assertTrue(result.getTotal() >= 2);
        assertNotNull(result.getList());
    }
    
    @Test
    void testListLogs_WithUserIdFilter() {
        // 准备测试数据
        createTestLog("CASE_VIEW", "123456", 1001L);
        createTestLog("CASE_CREATE", "123457", 1002L);
        createTestLog("CASE_UPDATE", "123458", 1001L);
        
        // 执行查询 - 按用户ID筛选
        LogQueryDTO query = new LogQueryDTO();
        query.setUserId(1001L);
        
        PageResult<OperationLogVO> result = operationLogService.listLogs(query);
        
        // 验证结果
        assertNotNull(result);
        assertTrue(result.getList().stream()
                .allMatch(vo -> vo.getUserId().equals(1001L)));
    }
    
    @Test
    void testListLogs_WithOperationTypeFilter() {
        // 准备测试数据
        createTestLog("CASE_VIEW", "123456", 1001L);
        createTestLog("CASE_CREATE", "123457", 1002L);
        createTestLog("CASE_CREATE", "123458", 1003L);
        
        // 执行查询 - 按操作类型筛选
        LogQueryDTO query = new LogQueryDTO();
        query.setOperationType("CASE_CREATE");
        
        PageResult<OperationLogVO> result = operationLogService.listLogs(query);
        
        // 验证结果
        assertNotNull(result);
        assertTrue(result.getList().stream()
                .allMatch(vo -> "CASE_CREATE".equals(vo.getOperationType())));
    }
    
    @Test
    void testListLogs_WithTimeRangeFilter() {
        // 准备测试数据
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yesterday = now.minusDays(1);
        LocalDateTime tomorrow = now.plusDays(1);
        
        SysOperationLog log1 = createTestLog("CASE_VIEW", "123456", 1001L);
        log1.setCreatedAt(yesterday);
        operationLogMapper.updateById(log1);
        
        SysOperationLog log2 = createTestLog("CASE_CREATE", "123457", 1002L);
        log2.setCreatedAt(now);
        operationLogMapper.updateById(log2);
        
        // 执行查询 - 按时间范围筛选
        LogQueryDTO query = new LogQueryDTO();
        query.setStartTime(yesterday.minusHours(1));
        query.setEndTime(now.plusHours(1));
        
        PageResult<OperationLogVO> result = operationLogService.listLogs(query);
        
        // 验证结果
        assertNotNull(result);
        assertTrue(result.getTotal() >= 2);
    }
    
    @Test
    void testListLogs_WithMultipleFilters() {
        // 准备测试数据
        createTestLog("CASE_VIEW", "123456", 1001L);
        createTestLog("CASE_CREATE", "123457", 1001L);
        createTestLog("CASE_CREATE", "123458", 1002L);
        
        // 执行查询 - 多条件筛选
        LogQueryDTO query = new LogQueryDTO();
        query.setUserId(1001L);
        query.setOperationType("CASE_CREATE");
        
        PageResult<OperationLogVO> result = operationLogService.listLogs(query);
        
        // 验证结果
        assertNotNull(result);
        assertTrue(result.getList().stream()
                .allMatch(vo -> vo.getUserId().equals(1001L) 
                        && "CASE_CREATE".equals(vo.getOperationType())));
    }
    
    @Test
    void testListLogs_OrderByCreatedAtDesc() {
        // 准备测试数据
        LocalDateTime now = LocalDateTime.now();
        
        SysOperationLog log1 = createTestLog("CASE_VIEW", "123456", 1001L);
        log1.setCreatedAt(now.minusHours(2));
        operationLogMapper.updateById(log1);
        
        SysOperationLog log2 = createTestLog("CASE_CREATE", "123457", 1002L);
        log2.setCreatedAt(now.minusHours(1));
        operationLogMapper.updateById(log2);
        
        SysOperationLog log3 = createTestLog("CASE_UPDATE", "123458", 1003L);
        log3.setCreatedAt(now);
        operationLogMapper.updateById(log3);
        
        // 执行查询
        LogQueryDTO query = new LogQueryDTO();
        query.setPage(1);
        query.setPageSize(10);
        
        PageResult<OperationLogVO> result = operationLogService.listLogs(query);
        
        // 验证结果按时间倒序排列
        assertNotNull(result);
        assertTrue(result.getList().size() >= 3);
        
        // 验证第一条记录是最新的
        LocalDateTime firstTime = result.getList().get(0).getCreatedAt();
        LocalDateTime secondTime = result.getList().get(1).getCreatedAt();
        assertTrue(firstTime.isAfter(secondTime) || firstTime.isEqual(secondTime));
    }
    
    @Test
    void testListLogs_Pagination() {
        // 准备测试数据 - 创建多条日志
        for (int i = 0; i < 25; i++) {
            createTestLog("CASE_VIEW", "12345" + i, 1001L);
        }
        
        // 执行查询 - 第1页
        LogQueryDTO query1 = new LogQueryDTO();
        query1.setPage(1);
        query1.setPageSize(10);
        
        PageResult<OperationLogVO> result1 = operationLogService.listLogs(query1);
        
        // 验证第1页结果
        assertNotNull(result1);
        assertTrue(result1.getTotal() >= 25);
        assertEquals(10, result1.getList().size());
        
        // 执行查询 - 第2页
        LogQueryDTO query2 = new LogQueryDTO();
        query2.setPage(2);
        query2.setPageSize(10);
        
        PageResult<OperationLogVO> result2 = operationLogService.listLogs(query2);
        
        // 验证第2页结果
        assertNotNull(result2);
        assertEquals(10, result2.getList().size());
        
        // 验证两页数据不重复
        assertNotEquals(result1.getList().get(0).getLogId(), 
                       result2.getList().get(0).getLogId());
    }
    
    /**
     * 创建测试日志
     */
    private SysOperationLog createTestLog(String operationType, String targetId, Long userId) {
        SysOperationLog log = new SysOperationLog();
        log.setUserId(userId);
        log.setOperationType(operationType);
        log.setTargetId(targetId);
        log.setDetail("测试操作: " + operationType);
        log.setClientIp("192.168.1.100");
        log.setApiPath("/api/test");
        log.setElapsedMs(100);
        log.setCreatedAt(LocalDateTime.now());
        
        operationLogMapper.insert(log);
        return log;
    }
}
