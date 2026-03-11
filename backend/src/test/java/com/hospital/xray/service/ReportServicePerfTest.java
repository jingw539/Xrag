package com.hospital.xray.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.xray.dto.ReportEditHistoryVO;
import com.hospital.xray.entity.ReportEditHistory;
import com.hospital.xray.entity.ReportInfo;
import com.hospital.xray.entity.SysUser;
import com.hospital.xray.mapper.ReportEditHistoryMapper;
import com.hospital.xray.mapper.ReportInfoMapper;
import com.hospital.xray.mapper.SysUserMapper;
import com.hospital.xray.service.impl.ReportServiceImpl;
import com.hospital.xray.util.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReportServicePerfTest {

    @Mock
    private ReportInfoMapper reportInfoMapper;

    @Mock
    private ReportEditHistoryMapper editHistoryMapper;

    @Mock
    private SysUserMapper sysUserMapper;

    @InjectMocks
    private ReportServiceImpl reportService;

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    public void testGetEditHistoryPerformance() {
        Long reportId = 1L;
        Long caseId = 1L;

        // Mock ReportInfo
        ReportInfo reportInfo = new ReportInfo();
        reportInfo.setReportId(reportId);
        reportInfo.setCaseId(caseId);
        when(reportInfoMapper.selectById(reportId)).thenReturn(reportInfo);

        // Mock ReportEditHistory list (large list to show N+1)
        int numHistories = 100;
        List<ReportEditHistory> histories = new ArrayList<>();
        for (int i = 0; i < numHistories; i++) {
            ReportEditHistory history = new ReportEditHistory();
            history.setHistoryId((long) i);
            history.setReportId(reportId);
            history.setEditorId((long) (i % 5)); // 5 distinct editors
            histories.add(history);
        }

        when(editHistoryMapper.selectList(any())).thenReturn(histories);

        AtomicInteger selectBatchIdsCalls = new AtomicInteger(0);
        when(sysUserMapper.selectBatchIds(any())).thenAnswer(invocation -> {
            selectBatchIdsCalls.incrementAndGet();
            List<Long> ids = invocation.getArgument(0);
            List<SysUser> users = new ArrayList<>();
            for (Long id : ids) {
                SysUser user = new SysUser();
                user.setUserId(id);
                user.setRealName("User " + id);
                users.add(user);
            }
            return users;
        });

        long startTime = System.currentTimeMillis();
        List<ReportEditHistoryVO> result = reportService.getEditHistory(reportId);
        long endTime = System.currentTimeMillis();

        System.out.println("Execution time: " + (endTime - startTime) + "ms");
        System.out.println("Result size: " + result.size());

        System.out.println("sysUserMapper.selectBatchIds calls: " + selectBatchIdsCalls.get());

        // assert that batch fetch was used exactly once
        assertEquals(1, selectBatchIdsCalls.get());

        // ensure correct mapping
        assertEquals("User 0", result.get(0).getEditorName());
    }
}
