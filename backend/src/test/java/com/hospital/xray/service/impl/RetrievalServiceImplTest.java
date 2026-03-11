package com.hospital.xray.service.impl;

import com.hospital.xray.dto.RetrievalResultVO;
import com.hospital.xray.dto.SimilarCaseVO;
import com.hospital.xray.entity.CaseInfo;
import com.hospital.xray.entity.ReportInfo;
import com.hospital.xray.entity.RetrievalLog;
import com.hospital.xray.mapper.CaseInfoMapper;
import com.hospital.xray.mapper.ImageInfoMapper;
import com.hospital.xray.mapper.ReportInfoMapper;
import com.hospital.xray.mapper.RetrievalLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrievalServiceImplTest {

    @Mock
    private RetrievalLogMapper retrievalLogMapper;

    @Mock
    private CaseInfoMapper caseInfoMapper;

    @Mock
    private ReportInfoMapper reportInfoMapper;

    @Mock
    private ImageInfoMapper imageInfoMapper;

    @InjectMocks
    private RetrievalServiceImpl retrievalService;

    private RetrievalLog createLog(String caseIdsStr, String scoresStr) {
        RetrievalLog log = new RetrievalLog();
        log.setRetrievalId(1L);
        log.setCaseId(100L);
        log.setTopK(5);
        log.setElapsedMs(100);
        log.setAllAboveThreshold(1);
        log.setSimilarCaseIds(caseIdsStr);
        log.setSimilarityScores(scoresStr);
        return log;
    }

    @Test
    void testRebuildSimilarCases_NullCaseIds() {
        when(retrievalLogMapper.selectById(1L)).thenReturn(createLog(null, "0.9"));
        RetrievalResultVO result = retrievalService.getById(1L);
        assertTrue(result.getSimilarCases().isEmpty());
    }

    @Test
    void testRebuildSimilarCases_BlankCaseIds() {
        when(retrievalLogMapper.selectById(1L)).thenReturn(createLog("   ", "0.9"));
        RetrievalResultVO result = retrievalService.getById(1L);
        assertTrue(result.getSimilarCases().isEmpty());
    }

    @Test
    void testRebuildSimilarCases_Normal() {
        when(retrievalLogMapper.selectById(1L)).thenReturn(createLog("101,102", "0.95,0.85"));

        CaseInfo c1 = new CaseInfo(); c1.setExamNo("EX1");
        CaseInfo c2 = new CaseInfo(); c2.setExamNo("EX2");
        when(caseInfoMapper.selectById(101L)).thenReturn(c1);
        when(caseInfoMapper.selectById(102L)).thenReturn(c2);

        ReportInfo r1 = new ReportInfo(); r1.setFinalFindings("F1");
        ReportInfo r2 = new ReportInfo(); r2.setAiFindings("F2");
        when(reportInfoMapper.selectLatestByCaseId(101L)).thenReturn(r1);
        when(reportInfoMapper.selectLatestByCaseId(102L)).thenReturn(r2);

        RetrievalResultVO result = retrievalService.getById(1L);
        List<SimilarCaseVO> cases = result.getSimilarCases();

        assertEquals(2, cases.size());

        assertEquals(101L, cases.get(0).getCaseId());
        assertEquals("EX1", cases.get(0).getExamNo());
        assertEquals(new BigDecimal("0.95"), cases.get(0).getSimilarityScore());
        assertEquals("F1", cases.get(0).getFindings());

        assertEquals(102L, cases.get(1).getCaseId());
        assertEquals("EX2", cases.get(1).getExamNo());
        assertEquals(new BigDecimal("0.85"), cases.get(1).getSimilarityScore());
        assertEquals("F2", cases.get(1).getFindings());
    }

    @Test
    void testRebuildSimilarCases_MissingScores() {
        when(retrievalLogMapper.selectById(1L)).thenReturn(createLog("101,102", "0.95"));
        when(caseInfoMapper.selectById(anyLong())).thenReturn(new CaseInfo());
        when(reportInfoMapper.selectLatestByCaseId(anyLong())).thenReturn(null);

        RetrievalResultVO result = retrievalService.getById(1L);
        List<SimilarCaseVO> cases = result.getSimilarCases();

        assertEquals(2, cases.size());
        assertEquals(new BigDecimal("0.95"), cases.get(0).getSimilarityScore());
        assertEquals(BigDecimal.ZERO, cases.get(1).getSimilarityScore());
    }

    @Test
    void testRebuildSimilarCases_TrailingCommasAndEmptyElements() {
        when(retrievalLogMapper.selectById(1L)).thenReturn(createLog("101, ,102,", "0.95, ,0.85"));
        when(caseInfoMapper.selectById(anyLong())).thenReturn(new CaseInfo());
        when(reportInfoMapper.selectLatestByCaseId(anyLong())).thenReturn(null);

        RetrievalResultVO result = retrievalService.getById(1L);
        List<SimilarCaseVO> cases = result.getSimilarCases();

        assertEquals(2, cases.size());
        assertEquals(101L, cases.get(0).getCaseId());
        assertEquals(102L, cases.get(1).getCaseId());

        assertEquals(new BigDecimal("0.95"), cases.get(0).getSimilarityScore());
        assertEquals(new BigDecimal("0.85"), cases.get(1).getSimilarityScore());
    }

    @Test
    void testRebuildSimilarCases_NullScores() {
        when(retrievalLogMapper.selectById(1L)).thenReturn(createLog("101", null));
        when(caseInfoMapper.selectById(anyLong())).thenReturn(new CaseInfo());
        when(reportInfoMapper.selectLatestByCaseId(anyLong())).thenReturn(null);

        RetrievalResultVO result = retrievalService.getById(1L);
        List<SimilarCaseVO> cases = result.getSimilarCases();

        assertEquals(1, cases.size());
        assertEquals(BigDecimal.ZERO, cases.get(0).getSimilarityScore());
    }

    @Test
    void testRebuildSimilarCases_InvalidNumberIgnored() {
        when(retrievalLogMapper.selectById(1L)).thenReturn(createLog("101,abc,102", "0.95,0.90,0.85"));
        when(caseInfoMapper.selectById(anyLong())).thenReturn(new CaseInfo());
        when(reportInfoMapper.selectLatestByCaseId(anyLong())).thenReturn(null);

        RetrievalResultVO result = retrievalService.getById(1L);
        List<SimilarCaseVO> cases = result.getSimilarCases();

        assertEquals(2, cases.size());
        assertEquals(101L, cases.get(0).getCaseId());
        assertEquals(new BigDecimal("0.95"), cases.get(0).getSimilarityScore());

        assertEquals(102L, cases.get(1).getCaseId());
        assertEquals(new BigDecimal("0.85"), cases.get(1).getSimilarityScore());
    }
}
