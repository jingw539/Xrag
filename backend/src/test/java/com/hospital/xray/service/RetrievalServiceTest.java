package com.hospital.xray.service;

import com.hospital.xray.dto.RetrievalResultVO;
import com.hospital.xray.entity.CaseInfo;
import com.hospital.xray.entity.RetrievalLog;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrievalServiceTest {

    @InjectMocks
    private RetrievalServiceImpl retrievalService;

    @Mock
    private RetrievalLogMapper retrievalLogMapper;

    @Mock
    private CaseInfoMapper caseInfoMapper;

    @Mock
    private ImageInfoMapper imageInfoMapper;

    @Mock
    private ReportInfoMapper reportInfoMapper;

    @Test
    void testGetById_WithInvalidSimilarCaseIds() {
        Long validCaseId = 2L;

        // Create a mock CaseInfo response
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setCaseId(validCaseId);
        caseInfo.setExamNo("EX_TEST_123");

        when(caseInfoMapper.selectById(validCaseId)).thenReturn(caseInfo);

        // Create a RetrievalLog with invalid ID format and one valid ID
        RetrievalLog log = new RetrievalLog();
        log.setRetrievalId(1L);
        log.setCaseId(100L);
        log.setQueryImageId(999L);
        log.setTopK(2);
        // Include an invalid string to trigger NumberFormatException in rebuildSimilarCases
        log.setSimilarCaseIds("invalid_id," + validCaseId);
        log.setSimilarityScores("0.95,0.88");
        log.setAllAboveThreshold(1);
        log.setElapsedMs(100);
        log.setRetrievalTime(LocalDateTime.now());

        when(retrievalLogMapper.selectById(1L)).thenReturn(log);

        // Call the service method
        RetrievalResultVO result = retrievalService.getById(1L);

        // Verify the result
        assertNotNull(result);
        assertEquals(1L, result.getRetrievalId());

        // Ensure similar cases were processed and the invalid one was skipped
        // while the valid one was added
        assertNotNull(result.getSimilarCases());
        assertEquals(1, result.getSimilarCases().size());
        assertEquals(validCaseId, result.getSimilarCases().get(0).getCaseId());
        assertEquals(new BigDecimal("0.88"), result.getSimilarCases().get(0).getSimilarityScore());
    }
}
