package com.hospital.xray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.hospital.xray.service.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalServiceImpl implements RetrievalService {

    private final RetrievalLogMapper retrievalLogMapper;
    private final ImageInfoMapper imageInfoMapper;
    private final CaseInfoMapper caseInfoMapper;
    private final ReportInfoMapper reportInfoMapper;

    @Override
    @Transactional
    public RetrievalResultVO search(Long caseId, Long imageId, Integer topK) {
        ImageInfo image = imageInfoMapper.selectById(imageId);
        if (image == null) {
            throw new BusinessException(404, "影像不存在");
        }

        long startMs = System.currentTimeMillis();
        List<SimilarCaseVO> similarCases = retrieveTypicalCases(caseId, topK);
        int elapsedMs = (int) (System.currentTimeMillis() - startMs);

        String caseIds = similarCases.stream()
                .map(v -> String.valueOf(v.getCaseId()))
                .collect(Collectors.joining(","));
        String scores = similarCases.stream()
                .map(v -> v.getSimilarityScore().toPlainString())
                .collect(Collectors.joining(","));

        RetrievalLog retrievalLog = new RetrievalLog();
        retrievalLog.setCaseId(caseId);
        retrievalLog.setQueryImageId(imageId);
        retrievalLog.setTopK(topK);
        retrievalLog.setSimilarCaseIds(caseIds);
        retrievalLog.setSimilarityScores(scores);
        retrievalLog.setAllAboveThreshold(1);
        retrievalLog.setElapsedMs(elapsedMs);
        retrievalLog.setRetrievalTime(LocalDateTime.now());
        retrievalLogMapper.insert(retrievalLog);

        log.info("RAG检索完成: caseId={}, topK={}, 命中典型病例={}, 耗时={}ms",
                caseId, topK, similarCases.size(), elapsedMs);

        RetrievalResultVO result = new RetrievalResultVO();
        result.setRetrievalId(retrievalLog.getRetrievalId());
        result.setCaseId(caseId);
        result.setTopK(topK);
        result.setElapsedMs(elapsedMs);
        result.setAllAboveThreshold(true);
        result.setSimilarCases(similarCases);
        return result;
    }

    @Override
    public RetrievalResultVO getById(Long retrievalId) {
        RetrievalLog retrievalLog = retrievalLogMapper.selectById(retrievalId);
        if (retrievalLog == null) {
            throw new BusinessException(404, "检索记录不存在");
        }
        return toVO(retrievalLog);
    }

    @Override
    public List<RetrievalResultVO> listByCaseId(Long caseId) {
        return retrievalLogMapper.selectList(
                        new LambdaQueryWrapper<RetrievalLog>()
                                .eq(RetrievalLog::getCaseId, caseId)
                                .orderByDesc(RetrievalLog::getRetrievalTime))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 从数据库检索典型病例作为 RAG 上下文
     * 优先取 is_typical=1 且有已签发报告的病例，按检查时间倒序取 topK 条
     */
    private List<SimilarCaseVO> retrieveTypicalCases(Long excludeCaseId, int topK) {
        List<CaseInfo> typicalCases = caseInfoMapper.selectList(
                new LambdaQueryWrapper<CaseInfo>()
                        .eq(CaseInfo::getIsTypical, 1)
                        .ne(CaseInfo::getCaseId, excludeCaseId)
                        .orderByDesc(CaseInfo::getExamTime)
                        .last("LIMIT " + topK));

        if (typicalCases.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> caseIds = typicalCases.stream()
                .map(CaseInfo::getCaseId)
                .collect(Collectors.toList());

        List<ReportInfo> reports = reportInfoMapper.selectList(
                new LambdaQueryWrapper<ReportInfo>()
                        .in(ReportInfo::getCaseId, caseIds)
                        .orderByDesc(ReportInfo::getCreatedAt)
        );

        Map<Long, ReportInfo> latestReportsMap = reports.stream()
                .collect(Collectors.toMap(
                        ReportInfo::getCaseId,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));

        List<SimilarCaseVO> result = new ArrayList<>();
        for (CaseInfo tc : typicalCases) {
            ReportInfo report = latestReportsMap.get(tc.getCaseId());
            SimilarCaseVO vo = new SimilarCaseVO();
            vo.setCaseId(tc.getCaseId());
            vo.setExamNo(tc.getExamNo());
            vo.setSimilarityScore(new BigDecimal("1.00"));
            if (report != null) {
                vo.setFindings(report.getFinalFindings() != null
                        ? report.getFinalFindings() : report.getAiFindings());
                vo.setImpression(report.getFinalImpression() != null
                        ? report.getFinalImpression() : report.getAiImpression());
            }
            result.add(vo);
        }
        return result;
    }

    private RetrievalResultVO toVO(RetrievalLog retrievalLog) {
        RetrievalResultVO vo = new RetrievalResultVO();
        vo.setRetrievalId(retrievalLog.getRetrievalId());
        vo.setCaseId(retrievalLog.getCaseId());
        vo.setTopK(retrievalLog.getTopK());
        vo.setElapsedMs(retrievalLog.getElapsedMs());
        vo.setAllAboveThreshold(retrievalLog.getAllAboveThreshold() == 1);
        vo.setSimilarCases(rebuildSimilarCases(
                retrievalLog.getSimilarCaseIds(),
                retrievalLog.getSimilarityScores()));
        return vo;
    }

    private List<SimilarCaseVO> rebuildSimilarCases(String caseIdsStr, String scoresStr) {
        if (caseIdsStr == null || caseIdsStr.isBlank()) return new ArrayList<>();
        String[] ids    = caseIdsStr.split(",");
        String[] scores = scoresStr != null ? scoresStr.split(",") : new String[0];

        List<Long> parsedCaseIds = new ArrayList<>();
        for (String idStr : ids) {
            String trimmed = idStr.trim();
            if (!trimmed.isEmpty()) {
                try {
                    parsedCaseIds.add(Long.parseLong(trimmed));
                } catch (NumberFormatException ignored) {}
            }
        }

        if (parsedCaseIds.isEmpty()) return new ArrayList<>();

        Map<Long, CaseInfo> caseMap = caseInfoMapper.selectBatchIds(parsedCaseIds).stream()
                .collect(Collectors.toMap(CaseInfo::getCaseId, Function.identity()));

        List<ReportInfo> reports = reportInfoMapper.selectList(
                new LambdaQueryWrapper<ReportInfo>()
                        .in(ReportInfo::getCaseId, parsedCaseIds)
                        .orderByDesc(ReportInfo::getCreatedAt)
        );
        Map<Long, ReportInfo> latestReportsMap = reports.stream()
                .collect(Collectors.toMap(
                        ReportInfo::getCaseId,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));

        List<SimilarCaseVO> result = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            String idStr = ids[i].trim();
            if (idStr.isEmpty()) continue;

            try {
                Long caseId = Long.parseLong(idStr);
                CaseInfo c  = caseMap.get(caseId);
                ReportInfo r = latestReportsMap.get(caseId);
                SimilarCaseVO vo = new SimilarCaseVO();
                vo.setCaseId(caseId);
                vo.setExamNo(c != null ? c.getExamNo() : null);
                BigDecimal score = i < scores.length
                        ? new BigDecimal(scores[i].trim()) : BigDecimal.ZERO;
                vo.setSimilarityScore(score);
                if (r != null) {
                    vo.setFindings(r.getFinalFindings() != null
                            ? r.getFinalFindings() : r.getAiFindings());
                    vo.setImpression(r.getFinalImpression() != null
                            ? r.getFinalImpression() : r.getAiImpression());
                }
                result.add(vo);
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }
}
