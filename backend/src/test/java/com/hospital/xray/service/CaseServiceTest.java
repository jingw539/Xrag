package com.hospital.xray.service;

import com.hospital.xray.dto.CaseCreateDTO;
import com.hospital.xray.dto.CaseQueryDTO;
import com.hospital.xray.dto.CaseUpdateDTO;
import com.hospital.xray.dto.CaseVO;
import com.hospital.xray.dto.PageResult;
import com.hospital.xray.dto.TypicalMarkDTO;
import com.hospital.xray.entity.CaseInfo;
import com.hospital.xray.entity.ReportInfo;
import com.hospital.xray.exception.CaseHasSignedReportException;
import com.hospital.xray.exception.CaseNotFoundException;
import com.hospital.xray.exception.DuplicateExamNoException;
import com.hospital.xray.mapper.CaseInfoMapper;
import com.hospital.xray.mapper.ReportInfoMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 病例服务单元测试
 */
@SpringBootTest
@Transactional
class CaseServiceTest {
    
    @Autowired
    private CaseService caseService;
    
    @Autowired
    private CaseInfoMapper caseInfoMapper;
    
    @Autowired
    private ReportInfoMapper reportInfoMapper;
    
    @Test
    void testListCases_WithNoFilters() {
        // 准备测试数据
        createTestCase("EX20240301001", "P123456");
        createTestCase("EX20240301002", "P123457");
        
        // 执行查询
        CaseQueryDTO query = new CaseQueryDTO();
        query.setPage(1);
        query.setPageSize(20);
        
        PageResult<CaseVO> result = caseService.listCases(query);
        
        // 验证结果
        assertNotNull(result);
        assertTrue(result.getTotal() >= 2);
        assertNotNull(result.getList());
    }
    
    @Test
    void testListCases_WithExamNoFilter() {
        // 准备测试数据
        createTestCase("EX20240301001", "P123456");
        createTestCase("EX20240302001", "P123457");
        
        // 执行查询
        CaseQueryDTO query = new CaseQueryDTO();
        query.setExamNo("EX20240301");
        
        PageResult<CaseVO> result = caseService.listCases(query);
        
        // 验证结果
        assertNotNull(result);
        assertTrue(result.getList().stream()
                .allMatch(vo -> vo.getExamNo().contains("EX20240301")));
    }
    
    @Test
    void testListCases_WithReportStatusFilter() {
        // 准备测试数据
        CaseInfo case1 = createTestCase("EX20240301001", "P123456");
        case1.setReportStatus("AI_DRAFT");
        caseInfoMapper.updateById(case1);
        // Create corresponding ReportInfo to avoid self-healing to NONE
        ReportInfo report1 = new ReportInfo();
        report1.setCaseId(case1.getCaseId());
        report1.setReportStatus("AI_DRAFT");
        report1.setCreatedAt(LocalDateTime.now());
        report1.setUpdatedAt(LocalDateTime.now());
        reportInfoMapper.insert(report1);
        
        CaseInfo case2 = createTestCase("EX20240301002", "P123457");
        case2.setReportStatus("SIGNED");
        caseInfoMapper.updateById(case2);
        // Create corresponding ReportInfo for SIGNED status
        ReportInfo report2 = new ReportInfo();
        report2.setCaseId(case2.getCaseId());
        report2.setReportStatus("SIGNED");
        report2.setSignTime(LocalDateTime.now());
        report2.setCreatedAt(LocalDateTime.now());
        report2.setUpdatedAt(LocalDateTime.now());
        reportInfoMapper.insert(report2);
        
        // 执行查询
        CaseQueryDTO query = new CaseQueryDTO();
        query.setReportStatus("AI_DRAFT");
        
        PageResult<CaseVO> result = caseService.listCases(query);
        
        // 验证结果
        assertNotNull(result);
        assertTrue(result.getList().stream()
                .allMatch(vo -> "AI_DRAFT".equals(vo.getReportStatus())));
    }
    
    @Test
    void testCreateCase_Success() {
        // 准备测试数据
        CaseCreateDTO dto = new CaseCreateDTO();
        dto.setExamNo("EX20240301999");
        dto.setPatientAnonId("P999999");
        dto.setGender("M");
        dto.setAge(45);
        dto.setExamTime(LocalDateTime.now());
        dto.setBodyPart("胸部");
        dto.setDepartment("放射科");
        
        // 执行创建
        Long caseId = caseService.createCase(dto);
        
        // 验证结果
        assertNotNull(caseId);
        
        // 验证数据库中的记录
        CaseInfo saved = caseInfoMapper.selectById(caseId);
        assertNotNull(saved);
        assertEquals("EX20240301999", saved.getExamNo());
        assertEquals("P999999", saved.getPatientAnonId());
        assertEquals("NONE", saved.getReportStatus());
        assertEquals(0, saved.getIsTypical());
    }
    
    @Test
    void testCreateCase_DuplicateExamNo() {
        // 准备测试数据 - 先创建一个病例
        createTestCase("EX20240301001", "P123456");
        
        // 尝试创建重复检查号的病例
        CaseCreateDTO dto = new CaseCreateDTO();
        dto.setExamNo("EX20240301001");
        dto.setPatientAnonId("P999999");
        dto.setExamTime(LocalDateTime.now());
        dto.setBodyPart("胸部");
        
        // 验证抛出异常
        assertThrows(DuplicateExamNoException.class, () -> {
            caseService.createCase(dto);
        });
    }
    
    @Test
    void testUpdateCase_Success() {
        // 准备测试数据 - 先创建一个病例
        CaseInfo caseInfo = createTestCase("EX20240301001", "P123456");
        Long caseId = caseInfo.getCaseId();
        
        // 准备更新数据
        CaseUpdateDTO dto = new CaseUpdateDTO();
        dto.setPatientAnonId("P888888");
        dto.setGender("F");
        dto.setAge(50);
        dto.setDepartment("急诊科");
        
        // 执行更新
        caseService.updateCase(caseId, dto);
        
        // 验证更新结果
        CaseInfo updated = caseInfoMapper.selectById(caseId);
        assertNotNull(updated);
        assertEquals("P888888", updated.getPatientAnonId());
        assertEquals("F", updated.getGender());
        assertEquals(50, updated.getAge());
        assertEquals("急诊科", updated.getDepartment());
    }
    
    @Test
    void testUpdateCase_CaseNotFound() {
        // 准备更新数据
        CaseUpdateDTO dto = new CaseUpdateDTO();
        dto.setPatientAnonId("P888888");
        
        // 验证抛出异常
        assertThrows(CaseNotFoundException.class, () -> {
            caseService.updateCase(999999L, dto);
        });
    }
    
    @Test
    void testDeleteCase_Success() {
        // 准备测试数据 - 创建一个未签发报告的病例
        CaseInfo caseInfo = createTestCase("EX20240301001", "P123456");
        Long caseId = caseInfo.getCaseId();
        
        // 执行删除
        caseService.deleteCase(caseId);
        
        // 验证病例已被删除
        CaseInfo deleted = caseInfoMapper.selectById(caseId);
        assertNull(deleted);
    }
    
    @Test
    void testDeleteCase_HasSignedReport() {
        // 准备测试数据 - 创建一个已签发报告的病例
        CaseInfo caseInfo = createTestCase("EX20240301001", "P123456");
        caseInfo.setReportStatus("SIGNED");
        caseInfoMapper.updateById(caseInfo);
        
        Long caseId = caseInfo.getCaseId();
        
        // 验证抛出异常
        assertThrows(CaseHasSignedReportException.class, () -> {
            caseService.deleteCase(caseId);
        });
        
        // 验证病例未被删除
        CaseInfo notDeleted = caseInfoMapper.selectById(caseId);
        assertNotNull(notDeleted);
    }
    
    @Test
    void testDeleteCase_CaseNotFound() {
        // 验证抛出异常
        assertThrows(CaseNotFoundException.class, () -> {
            caseService.deleteCase(999999L);
        });
    }
    
    @Test
    void testMarkTypical_Success() {
        // 准备测试数据 - 创建一个病例
        CaseInfo caseInfo = createTestCase("EX20240301001", "P123456");
        Long caseId = caseInfo.getCaseId();
        
        // 准备标记数据
        TypicalMarkDTO dto = new TypicalMarkDTO();
        dto.setIsTypical(1);
        dto.setTypicalTags("经典肺炎,教学案例");
        dto.setTypicalRemark("典型的肺炎影像表现");
        
        // 执行标记
        caseService.markTypical(caseId, dto);
        
        // 验证标记结果
        CaseInfo marked = caseInfoMapper.selectById(caseId);
        assertNotNull(marked);
        assertEquals(1, marked.getIsTypical());
        assertEquals("经典肺炎,教学案例", marked.getTypicalTags());
        assertEquals("典型的肺炎影像表现", marked.getTypicalRemark());
    }
    
    @Test
    void testMarkTypical_Unmark() {
        // 准备测试数据 - 创建一个已标记的典型病例
        CaseInfo caseInfo = createTestCase("EX20240301001", "P123456");
        caseInfo.setIsTypical(1);
        caseInfo.setTypicalTags("经典肺炎");
        caseInfoMapper.updateById(caseInfo);
        
        Long caseId = caseInfo.getCaseId();
        
        // 准备取消标记数据
        TypicalMarkDTO dto = new TypicalMarkDTO();
        dto.setIsTypical(0);
        dto.setTypicalTags(null);
        dto.setTypicalRemark(null);
        
        // 执行取消标记
        caseService.markTypical(caseId, dto);
        
        // 验证取消标记结果
        CaseInfo unmarked = caseInfoMapper.selectById(caseId);
        assertNotNull(unmarked);
        assertEquals(0, unmarked.getIsTypical());
    }
    
    @Test
    void testListCases_TypicalOnly() {
        // 准备测试数据
        CaseInfo case1 = createTestCase("EX20240301001", "P123456");
        case1.setIsTypical(1);
        case1.setTypicalTags("经典肺炎");
        caseInfoMapper.updateById(case1);
        
        CaseInfo case2 = createTestCase("EX20240301002", "P123457");
        case2.setIsTypical(0);
        caseInfoMapper.updateById(case2);
        
        // 执行查询 - 只查询典型病例
        CaseQueryDTO query = new CaseQueryDTO();
        query.setIsTypical(1);
        
        PageResult<CaseVO> result = caseService.listCases(query);
        
        // 验证结果
        assertNotNull(result);
        assertTrue(result.getList().stream()
                .allMatch(vo -> vo.getIsTypical() == 1));
    }
    
    /**
     * 创建测试病例
     */
    private CaseInfo createTestCase(String examNo, String patientAnonId) {
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setExamNo(examNo);
        caseInfo.setPatientAnonId(patientAnonId);
        caseInfo.setGender("M");
        caseInfo.setAge(45);
        caseInfo.setExamTime(LocalDateTime.now());
        caseInfo.setBodyPart("胸部");
        caseInfo.setDepartment("放射科");
        caseInfo.setReportStatus("NONE");
        caseInfo.setIsTypical(0);
        caseInfo.setCreatedAt(LocalDateTime.now());
        caseInfo.setUpdatedAt(LocalDateTime.now());
        
        caseInfoMapper.insert(caseInfo);
        return caseInfo;
    }
}
