package com.hospital.xray.service;

import com.hospital.xray.dto.SimilarCaseVO;
import com.hospital.xray.entity.CaseInfo;
import com.hospital.xray.entity.ReportInfo;
import com.hospital.xray.mapper.CaseInfoMapper;
import com.hospital.xray.mapper.ReportInfoMapper;
import com.hospital.xray.service.impl.RetrievalServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RetrievalServiceBenchmark {

    @Mock
    private CaseInfoMapper caseInfoMapper;

    @Mock
    private ReportInfoMapper reportInfoMapper;

    @InjectMocks
    private RetrievalServiceImpl retrievalService;

    @BeforeEach
    void setUp() {
        // Setup mock data for topK = 100
        List<CaseInfo> mockCases = new ArrayList<>();
        List<ReportInfo> mockReports = new ArrayList<>();
        for (long i = 1; i <= 100; i++) {
            CaseInfo c = new CaseInfo();
            c.setCaseId(i);
            c.setExamNo("E" + i);
            mockCases.add(c);

            ReportInfo r = new ReportInfo();
            r.setCaseId(i);
            r.setFinalFindings("Findings " + i);
            r.setFinalImpression("Impression " + i);
            mockReports.add(r);
        }

        when(caseInfoMapper.selectList(any())).thenReturn(mockCases);

        // Mock the optimized query response, simulating a single DB call latency (e.g., 5ms)
        when(reportInfoMapper.selectList(any())).thenAnswer(invocation -> {
            Thread.sleep(5);
            return mockReports;
        });
    }

    @Test
    void benchmarkRetrieveTypicalCases() throws Exception {
        System.out.println("Starting Optimized Benchmark for retrieveTypicalCases...");
        long startTime = System.currentTimeMillis();

        // Invoke private method
        List<SimilarCaseVO> results = ReflectionTestUtils.invokeMethod(retrievalService, "retrieveTypicalCases", 0L, 100);

        long endTime = System.currentTimeMillis();
        System.out.println("Benchmark Finished.");
        System.out.println("Time taken: " + (endTime - startTime) + " ms");
        System.out.println("Number of results: " + (results != null ? results.size() : 0));
    }
}
