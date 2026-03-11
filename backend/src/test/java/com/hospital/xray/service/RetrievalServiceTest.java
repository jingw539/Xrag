package com.hospital.xray.service;

import com.hospital.xray.dto.RetrievalResultVO;
import com.hospital.xray.dto.SimilarCaseVO;
import com.hospital.xray.entity.CaseInfo;
import com.hospital.xray.entity.ImageInfo;
import com.hospital.xray.entity.ReportInfo;
import com.hospital.xray.entity.RetrievalLog;
import com.hospital.xray.exception.BusinessException;
import com.hospital.xray.mapper.CaseInfoMapper;
import com.hospital.xray.mapper.ImageInfoMapper;
import com.hospital.xray.mapper.ReportInfoMapper;
import com.hospital.xray.mapper.RetrievalLogMapper;
import com.hospital.xray.service.impl.RetrievalServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetrievalServiceTest {

    @Mock
    private RetrievalLogMapper retrievalLogMapper;

    @Mock
    private ImageInfoMapper imageInfoMapper;

    @Mock
    private CaseInfoMapper caseInfoMapper;

    @Mock
    private ReportInfoMapper reportInfoMapper;

    @InjectMocks
    private RetrievalServiceImpl retrievalService;

    @Test
    void testSearch_Success() {
        // Arrange
        Long caseId = 1L;
        Long imageId = 100L;
        Integer topK = 2;

        ImageInfo mockImage = new ImageInfo();
        mockImage.setImageId(imageId);
        when(imageInfoMapper.selectById(imageId)).thenReturn(mockImage);

        CaseInfo typicalCase1 = new CaseInfo();
        typicalCase1.setCaseId(2L);
        typicalCase1.setExamNo("EX001");

        CaseInfo typicalCase2 = new CaseInfo();
        typicalCase2.setCaseId(3L);
        typicalCase2.setExamNo("EX002");

        when(caseInfoMapper.selectList(any())).thenReturn(Arrays.asList(typicalCase1, typicalCase2));

        ReportInfo report1 = new ReportInfo();
        report1.setFinalFindings("Findings 1");
        report1.setFinalImpression("Impression 1");
        when(reportInfoMapper.selectLatestByCaseId(2L)).thenReturn(report1);

        ReportInfo report2 = new ReportInfo();
        report2.setAiFindings("AI Findings 2");
        report2.setAiImpression("AI Impression 2");
        when(reportInfoMapper.selectLatestByCaseId(3L)).thenReturn(report2);

        when(retrievalLogMapper.insert(any(RetrievalLog.class))).thenAnswer(invocation -> {
            RetrievalLog log = invocation.getArgument(0);
            log.setRetrievalId(10L);
            return 1;
        });

        // Act
        RetrievalResultVO result = retrievalService.search(caseId, imageId, topK);

        // Assert
        assertNotNull(result);
        assertEquals(10L, result.getRetrievalId());
        assertEquals(caseId, result.getCaseId());
        assertEquals(topK, result.getTopK());
        assertTrue(result.getAllAboveThreshold());
        assertEquals(2, result.getSimilarCases().size());

        SimilarCaseVO sc1 = result.getSimilarCases().get(0);
        assertEquals(2L, sc1.getCaseId());
        assertEquals("EX001", sc1.getExamNo());
        assertEquals(new BigDecimal("1.00"), sc1.getSimilarityScore());
        assertEquals("Findings 1", sc1.getFindings());
        assertEquals("Impression 1", sc1.getImpression());

        SimilarCaseVO sc2 = result.getSimilarCases().get(1);
        assertEquals(3L, sc2.getCaseId());
        assertEquals("EX002", sc2.getExamNo());
        assertEquals(new BigDecimal("1.00"), sc2.getSimilarityScore());
        assertEquals("AI Findings 2", sc2.getFindings());
        assertEquals("AI Impression 2", sc2.getImpression());

        verify(retrievalLogMapper, times(1)).insert(any(RetrievalLog.class));
    }

    @Test
    void testSearch_ImageNotFound() {
        Long caseId = 1L;
        Long imageId = 100L;
        Integer topK = 2;

        when(imageInfoMapper.selectById(imageId)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            retrievalService.search(caseId, imageId, topK);
        });

        assertEquals("影像不存在", exception.getMessage());
        assertEquals(404, exception.getCode());
    }

    @Test
    void testSearch_NoTypicalCases() {
        Long caseId = 1L;
        Long imageId = 100L;
        Integer topK = 2;

        ImageInfo mockImage = new ImageInfo();
        mockImage.setImageId(imageId);
        when(imageInfoMapper.selectById(imageId)).thenReturn(mockImage);

        when(caseInfoMapper.selectList(any())).thenReturn(Collections.emptyList());

        when(retrievalLogMapper.insert(any(RetrievalLog.class))).thenAnswer(invocation -> {
            RetrievalLog log = invocation.getArgument(0);
            log.setRetrievalId(11L);
            return 1;
        });

        RetrievalResultVO result = retrievalService.search(caseId, imageId, topK);

        assertNotNull(result);
        assertEquals(11L, result.getRetrievalId());
        assertTrue(result.getSimilarCases().isEmpty());
    }

    @Test
    void testGetById_Success() {
        Long retrievalId = 10L;
        RetrievalLog log = new RetrievalLog();
        log.setRetrievalId(retrievalId);
        log.setCaseId(1L);
        log.setTopK(2);
        log.setElapsedMs(100);
        log.setAllAboveThreshold(1);
        log.setSimilarCaseIds("2,3");
        log.setSimilarityScores("0.95,0.85");

        when(retrievalLogMapper.selectById(retrievalId)).thenReturn(log);

        CaseInfo case2 = new CaseInfo();
        case2.setCaseId(2L);
        case2.setExamNo("EX002");
        when(caseInfoMapper.selectById(2L)).thenReturn(case2);

        CaseInfo case3 = new CaseInfo();
        case3.setCaseId(3L);
        case3.setExamNo("EX003");
        when(caseInfoMapper.selectById(3L)).thenReturn(case3);

        ReportInfo report2 = new ReportInfo();
        report2.setFinalFindings("F2");
        when(reportInfoMapper.selectLatestByCaseId(2L)).thenReturn(report2);

        when(reportInfoMapper.selectLatestByCaseId(3L)).thenReturn(null);

        RetrievalResultVO result = retrievalService.getById(retrievalId);

        assertNotNull(result);
        assertEquals(retrievalId, result.getRetrievalId());
        assertEquals(1L, result.getCaseId());
        assertEquals(2, result.getTopK());
        assertEquals(100, result.getElapsedMs());
        assertTrue(result.getAllAboveThreshold());

        assertEquals(2, result.getSimilarCases().size());
        assertEquals(2L, result.getSimilarCases().get(0).getCaseId());
        assertEquals("EX002", result.getSimilarCases().get(0).getExamNo());
        assertEquals(new BigDecimal("0.95"), result.getSimilarCases().get(0).getSimilarityScore());
        assertEquals("F2", result.getSimilarCases().get(0).getFindings());

        assertEquals(3L, result.getSimilarCases().get(1).getCaseId());
        assertEquals("EX003", result.getSimilarCases().get(1).getExamNo());
        assertEquals(new BigDecimal("0.85"), result.getSimilarCases().get(1).getSimilarityScore());
        assertNull(result.getSimilarCases().get(1).getFindings());
    }

    @Test
    void testGetById_NotFound() {
        Long retrievalId = 10L;
        when(retrievalLogMapper.selectById(retrievalId)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            retrievalService.getById(retrievalId);
        });

        assertEquals("检索记录不存在", exception.getMessage());
        assertEquals(404, exception.getCode());
    }

    @Test
    void testGetById_BlankSimilarCaseIds() {
        Long retrievalId = 10L;
        RetrievalLog log = new RetrievalLog();
        log.setRetrievalId(retrievalId);
        log.setSimilarCaseIds("");
        log.setSimilarityScores("");
        log.setAllAboveThreshold(1);

        when(retrievalLogMapper.selectById(retrievalId)).thenReturn(log);

        RetrievalResultVO result = retrievalService.getById(retrievalId);

        assertNotNull(result);
        assertTrue(result.getSimilarCases().isEmpty());
    }

    @Test
    void testGetById_ParseErrorInSimilarCaseIds() {
        Long retrievalId = 10L;
        RetrievalLog log = new RetrievalLog();
        log.setRetrievalId(retrievalId);
        log.setSimilarCaseIds("2,invalid,3");
        log.setSimilarityScores("0.95,0.90,0.85");
        log.setAllAboveThreshold(1);

        when(retrievalLogMapper.selectById(retrievalId)).thenReturn(log);
        when(caseInfoMapper.selectById(anyLong())).thenReturn(new CaseInfo());
        when(reportInfoMapper.selectLatestByCaseId(anyLong())).thenReturn(new ReportInfo());

        RetrievalResultVO result = retrievalService.getById(retrievalId);

        assertNotNull(result);
        assertEquals(2, result.getSimilarCases().size());
    }

    @Test
    void testListByCaseId_Success() {
        Long caseId = 1L;

        RetrievalLog log1 = new RetrievalLog();
        log1.setRetrievalId(10L);
        log1.setCaseId(caseId);
        log1.setSimilarCaseIds("2");
        log1.setSimilarityScores("0.99");
        log1.setAllAboveThreshold(1);
        log1.setRetrievalTime(LocalDateTime.now());

        RetrievalLog log2 = new RetrievalLog();
        log2.setRetrievalId(11L);
        log2.setCaseId(caseId);
        log2.setSimilarCaseIds("");
        log2.setSimilarityScores("");
        log2.setAllAboveThreshold(0);
        log2.setRetrievalTime(LocalDateTime.now().minusDays(1));

        when(retrievalLogMapper.selectList(any())).thenReturn(Arrays.asList(log1, log2));

        CaseInfo case2 = new CaseInfo();
        case2.setCaseId(2L);
        when(caseInfoMapper.selectById(2L)).thenReturn(case2);
        when(reportInfoMapper.selectLatestByCaseId(2L)).thenReturn(null);

        List<RetrievalResultVO> results = retrievalService.listByCaseId(caseId);

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals(10L, results.get(0).getRetrievalId());
        assertEquals(11L, results.get(1).getRetrievalId());
        assertEquals(1, results.get(0).getSimilarCases().size());
        assertEquals(0, results.get(1).getSimilarCases().size());
    }
}
