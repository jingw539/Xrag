package com.hospital.xray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.xray.client.AiServiceClient;
import com.hospital.xray.client.RagServiceClient;
import com.hospital.xray.dto.RetrievalResultVO;
import com.hospital.xray.dto.SimilarCaseVO;
import com.hospital.xray.entity.CaseInfo;
import com.hospital.xray.entity.ImageInfo;
import com.hospital.xray.entity.ImageAnnotation;
import com.hospital.xray.entity.ReportInfo;
import com.hospital.xray.entity.RetrievalLog;
import com.hospital.xray.exception.BusinessException;
import com.hospital.xray.mapper.CaseInfoMapper;
import com.hospital.xray.mapper.ImageInfoMapper;
import com.hospital.xray.mapper.ImageAnnotationMapper;
import com.hospital.xray.mapper.ReportInfoMapper;
import com.hospital.xray.mapper.RetrievalLogMapper;
import com.hospital.xray.service.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalServiceImpl implements RetrievalService {

    private final RetrievalLogMapper retrievalLogMapper;
    private final ImageInfoMapper imageInfoMapper;
    private final CaseInfoMapper caseInfoMapper;
    private final ReportInfoMapper reportInfoMapper;
    private final ImageAnnotationMapper imageAnnotationMapper;
    private final RagServiceClient ragServiceClient;
    private final AiServiceClient aiServiceClient;

    @Override
    @Transactional
    public RetrievalResultVO search(Long caseId, Long imageId, Integer topK) {
        ImageInfo image = imageInfoMapper.selectById(imageId);
        if (image == null) {
            throw new BusinessException(404, "影像不存在");
        }

        long startMs = System.currentTimeMillis();
        List<SimilarCaseVO> imageCases = retrieveByImage(caseId, image, topK);
        List<SimilarCaseVO> textCases = retrieveByRag(caseId, imageId, topK);
        List<SimilarCaseVO> similarCases = mergeSimilarCases(imageCases, textCases, topK);
        attachCaseDoctor(similarCases);
        enrichWithLatestReports(similarCases);
        if (similarCases.isEmpty()) {
            similarCases = retrieveTypicalCases(caseId, topK);
            attachCaseDoctor(similarCases);
        }
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
            typicalCases = caseInfoMapper.selectList(
                    new LambdaQueryWrapper<CaseInfo>()
                            .ne(CaseInfo::getCaseId, excludeCaseId)
                            .eq(CaseInfo::getReportStatus, "SIGNED")
                            .orderByDesc(CaseInfo::getExamTime)
                            .last("LIMIT " + topK));
        }

        List<Long> caseIds = typicalCases.stream()
                .map(CaseInfo::getCaseId)
                .collect(Collectors.toList());
        Map<Long, ReportInfo> latestReportMap = fetchLatestReports(caseIds);

        List<SimilarCaseVO> result = new ArrayList<>();
        for (CaseInfo tc : typicalCases) {
            ReportInfo report = latestReportMap.get(tc.getCaseId());
            SimilarCaseVO vo = new SimilarCaseVO();
            vo.setCaseId(tc.getCaseId());
            vo.setExamNo(tc.getExamNo());
            vo.setDoctorId(tc.getResponsibleDoctorId());
            // Typical fallback is not a similarity match; avoid misleading 100% scores.
            vo.setSimilarityScore(null);
            vo.setSource("TYPICAL");
            if (report != null) {
                vo.setFindings(pickText(report.getFinalFindings(), report.getAiFindings()));
                vo.setImpression(pickText(report.getFinalImpression(), report.getAiImpression()));
            }
            result.add(vo);
        }
        return result;
    }

    private List<SimilarCaseVO> retrieveByRag(Long caseId, Long imageId, int topK) {
        if (!ragServiceClient.isEnabled()) return List.of();
        String queryText = buildQueryText(caseId, imageId);
        if (queryText.isBlank()) return List.of();
        List<SimilarCaseVO> results = ragServiceClient.searchSimilarCases(queryText, topK);
        if (results.isEmpty()) return results;
        results.forEach(v -> {
            if (v != null) v.setSource("RAG");
        });
        List<Long> caseIds = results.stream()
                .map(SimilarCaseVO::getCaseId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, CaseInfo> caseMap = caseIds.isEmpty()
                ? Collections.emptyMap()
                : caseInfoMapper.selectBatchIds(caseIds).stream()
                .collect(Collectors.toMap(CaseInfo::getCaseId, c -> c));
        for (SimilarCaseVO vo : results) {
            if (vo.getCaseId() == null) continue;
            CaseInfo c = caseMap.get(vo.getCaseId());
            if (c != null) {
                vo.setExamNo(c.getExamNo());
                vo.setDoctorId(c.getResponsibleDoctorId());
            }
        }
        return results;
    }

    private List<SimilarCaseVO> retrieveByImage(Long caseId, ImageInfo image, int topK) {
        if (image == null || !aiServiceClient.isLocalEnabled()) return List.of();
        if (isDicomImage(image)) return List.of();
        List<SimilarCaseVO> results = aiServiceClient.searchSimilarCasesByImage(image.getFilePath(), caseId, topK);
        results.forEach(v -> {
            if (v != null) v.setSource("IMAGE");
        });
        return results.stream()
                .filter(v -> v.getCaseId() != null && !v.getCaseId().equals(caseId))
                .collect(Collectors.toList());
    }

    private boolean isDicomImage(ImageInfo image) {
        String type = image.getFileType() != null ? image.getFileType().toLowerCase() : "";
        if (type.equals("dcm") || type.equals("dicom")) return true;
        String name = image.getFileName() != null ? image.getFileName().toLowerCase() : "";
        return name.endsWith(".dcm");
    }

    private List<SimilarCaseVO> mergeSimilarCases(List<SimilarCaseVO> primary,
                                                  List<SimilarCaseVO> secondary,
                                                  int topK) {
        if ((primary == null || primary.isEmpty()) && (secondary == null || secondary.isEmpty())) {
            return new ArrayList<>();
        }
        List<SimilarCaseVO> merged = new ArrayList<>();
        if (primary != null) merged.addAll(primary);
        if (secondary != null) {
            for (SimilarCaseVO vo : secondary) {
                if (vo == null || vo.getCaseId() == null) continue;
                SimilarCaseVO existing = merged.stream()
                        .filter(m -> vo.getCaseId().equals(m.getCaseId()))
                        .findFirst().orElse(null);
                if (existing == null) {
                    merged.add(vo);
                } else {
                    if (existing.getSimilarityScore() == null && vo.getSimilarityScore() != null) {
                        existing.setSimilarityScore(vo.getSimilarityScore());
                    } else if (existing.getSimilarityScore() != null && vo.getSimilarityScore() != null) {
                        BigDecimal avg = existing.getSimilarityScore().add(vo.getSimilarityScore())
                                .divide(new BigDecimal("2"), 6, RoundingMode.HALF_UP);
                        existing.setSimilarityScore(avg);
                    }
                    if (existing.getSource() == null || existing.getSource().isBlank()) {
                        existing.setSource(vo.getSource());
                    } else if (vo.getSource() != null && !vo.getSource().isBlank()
                            && !Objects.equals(existing.getSource(), vo.getSource())) {
                        existing.setSource("MIXED");
                    }
                    if (existing.getFindings() == null || existing.getFindings().isBlank()) {
                        existing.setFindings(vo.getFindings());
                    }
                    if (existing.getImpression() == null || existing.getImpression().isBlank()) {
                        existing.setImpression(vo.getImpression());
                    }
                    if (existing.getExamNo() == null || existing.getExamNo().isBlank()) {
                        existing.setExamNo(vo.getExamNo());
                    }
                }
            }
        }
        merged.sort((a, b) -> {
            BigDecimal sa = a != null ? a.getSimilarityScore() : null;
            BigDecimal sb = b != null ? b.getSimilarityScore() : null;
            if (sa == null && sb == null) return 0;
            if (sa == null) return 1;
            if (sb == null) return -1;
            return sb.compareTo(sa);
        });
        if (merged.size() > topK) {
            return merged.subList(0, topK);
        }
        return merged;
    }

    private String buildQueryText(Long caseId, Long imageId) {
        if (caseId != null) {
            ReportInfo latest = reportInfoMapper.selectLatestByCaseId(caseId);
            if (latest != null) {
                String findings = latest.getFinalFindings() != null ? latest.getFinalFindings() : latest.getAiFindings();
                String impression = latest.getFinalImpression() != null ? latest.getFinalImpression() : latest.getAiImpression();
                String merged = (findings != null ? findings : "") + "\n" + (impression != null ? impression : "");
                if (!merged.isBlank()) return merged.trim();
            }
        }
        if (imageId != null) {
            List<ImageAnnotation> annos = imageAnnotationMapper.selectList(
                    new LambdaQueryWrapper<ImageAnnotation>()
                            .eq(ImageAnnotation::getImageId, imageId)
                            .eq(ImageAnnotation::getSource, "AI"));
            if (annos != null && !annos.isEmpty()) {
                String labels = annos.stream()
                        .map(ImageAnnotation::getLabel)
                        .filter(s -> s != null && !s.isBlank())
                        .distinct()
                        .collect(Collectors.joining(", "));
                if (!labels.isBlank()) return labels;
            }
        }
        StringBuilder sb = new StringBuilder();
        if (caseId != null) {
            CaseInfo caseInfo = caseInfoMapper.selectById(caseId);
            if (caseInfo != null) {
                if (StringUtils.hasText(caseInfo.getBodyPart())) sb.append(caseInfo.getBodyPart()).append(" ");
                if (StringUtils.hasText(caseInfo.getGender())) sb.append(caseInfo.getGender()).append(" ");
                if (caseInfo.getAge() != null) sb.append(caseInfo.getAge()).append("岁 ");
                if (StringUtils.hasText(caseInfo.getDepartment())) sb.append(caseInfo.getDepartment()).append(" ");
            }
        }
        if (imageId != null) {
            ImageInfo imageInfo = imageInfoMapper.selectById(imageId);
            if (imageInfo != null && StringUtils.hasText(imageInfo.getViewPosition())) {
                sb.append("体位 ").append(imageInfo.getViewPosition()).append(" ");
            }
        }
        if (!sb.isEmpty()) {
            sb.append("胸部X光 报告");
            return sb.toString().trim();
        }
        return "胸部X光 报告";
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
        List<Long> caseIds = new ArrayList<>();
        List<BigDecimal> scoreList = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            String idStr = ids[i].trim();
            if (idStr.isEmpty()) continue;
            try {
                Long caseId = Long.parseLong(idStr);
                BigDecimal score = i < scores.length
                        ? new BigDecimal(scores[i].trim()) : BigDecimal.ZERO;
                caseIds.add(caseId);
                scoreList.add(score);
            } catch (NumberFormatException e) {
                log.warn("Invalid similar case id: {}", idStr);
            }
        }
        if (caseIds.isEmpty()) return new ArrayList<>();
        Map<Long, CaseInfo> caseMap = caseInfoMapper.selectBatchIds(caseIds).stream()
                .collect(Collectors.toMap(CaseInfo::getCaseId, c -> c, (a, b) -> a));
        Map<Long, ReportInfo> latestReportMap = fetchLatestReports(caseIds);
        List<SimilarCaseVO> result = new ArrayList<>();
        for (int i = 0; i < caseIds.size(); i++) {
            Long caseId = caseIds.get(i);
            SimilarCaseVO vo = new SimilarCaseVO();
            vo.setCaseId(caseId);
            CaseInfo c = caseMap.get(caseId);
            if (c != null) {
                vo.setExamNo(c.getExamNo());
                vo.setDoctorId(c.getResponsibleDoctorId());
            }
            BigDecimal score = i < scoreList.size() ? scoreList.get(i) : BigDecimal.ZERO;
            vo.setSimilarityScore(score);
            ReportInfo r = latestReportMap.get(caseId);
            if (r != null) {
                vo.setFindings(pickText(r.getFinalFindings(), r.getAiFindings()));
                vo.setImpression(pickText(r.getFinalImpression(), r.getAiImpression()));
            }
            result.add(vo);
        }
        return result;
    }

    private String pickText(String primary, String fallback) {
        if (isUsefulText(primary)) return primary.trim();
        if (isUsefulText(fallback)) return fallback.trim();
        return "";
    }

    private boolean isUsefulText(String text) {
        if (!StringUtils.hasText(text)) return false;
        String trimmed = text.trim();
        if (trimmed.length() < 2) return false;
        String cleaned = trimmed.replaceAll("[\\p{P}\\p{S}\\s]", "");
        return cleaned.length() >= 2;
    }

    private Map<Long, ReportInfo> fetchLatestReports(List<Long> caseIds) {
        if (caseIds == null || caseIds.isEmpty()) return Collections.emptyMap();
        return reportInfoMapper.selectLatestByCaseIds(caseIds).stream()
                .collect(Collectors.toMap(ReportInfo::getCaseId, r -> r, (a, b) -> a));
    }

    private void enrichWithLatestReports(List<SimilarCaseVO> items) {
        if (items == null || items.isEmpty()) return;
        List<Long> caseIds = items.stream()
                .map(SimilarCaseVO::getCaseId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (caseIds.isEmpty()) return;
        Map<Long, ReportInfo> latestReportMap = fetchLatestReports(caseIds);
        for (SimilarCaseVO vo : items) {
            if (vo == null || vo.getCaseId() == null) continue;
            if (isUsefulText(vo.getFindings()) && isUsefulText(vo.getImpression())) continue;
            ReportInfo r = latestReportMap.get(vo.getCaseId());
            if (r == null) continue;
            if (!isUsefulText(vo.getFindings())) {
                vo.setFindings(pickText(r.getFinalFindings(), r.getAiFindings()));
            }
            if (!isUsefulText(vo.getImpression())) {
                vo.setImpression(pickText(r.getFinalImpression(), r.getAiImpression()));
            }
        }
    }

    private void attachCaseDoctor(List<SimilarCaseVO> items) {
        if (items == null || items.isEmpty()) return;
        List<Long> caseIds = items.stream()
                .map(SimilarCaseVO::getCaseId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (caseIds.isEmpty()) return;
        Map<Long, CaseInfo> caseMap = caseInfoMapper.selectBatchIds(caseIds).stream()
                .collect(Collectors.toMap(CaseInfo::getCaseId, c -> c, (a, b) -> a));
        for (SimilarCaseVO vo : items) {
            if (vo == null || vo.getCaseId() == null) continue;
            CaseInfo c = caseMap.get(vo.getCaseId());
            if (c != null) {
                if (!StringUtils.hasText(vo.getExamNo())) vo.setExamNo(c.getExamNo());
                if (vo.getDoctorId() == null) vo.setDoctorId(c.getResponsibleDoctorId());
            }
        }
    }

    // Note: read-only access is allowed for other doctors' cases at the UI layer.
}
