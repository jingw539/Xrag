package com.hospital.xray.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.xray.dto.SimilarCaseVO;
import com.hospital.xray.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiServiceClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.qwen.api-key}")
    private String qwenApiKey;

    @Value("${ai.qwen.base-url}")
    private String qwenBaseUrl;

    @Value("${ai.qwen.model:qwen-vl-plus}")
    private String qwenModel;

    @Value("${ai.qwen.timeout:120000}")
    private int qwenTimeout;

    @Value("${ai.deepseek.api-key}")
    private String deepseekApiKey;

    @Value("${ai.deepseek.base-url}")
    private String deepseekBaseUrl;

    @Value("${ai.deepseek.timeout:60000}")
    private int deepseekTimeout;

    @Value("${ai.local.base-url:}")
    private String localBaseUrl;

    @Value("${ai.local.timeout:120000}")
    private int localTimeout;

    @Value("${minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;

    @Value("${minio.bucket-name:cxr-images}")
    private String minioBucket;

    public boolean isLocalEnabled() {
        return StringUtils.hasText(localBaseUrl);
    }

    /**
     * Generate chest X-ray report. Prefer local self-trained model if configured.
     */
    public Map<String, Object> generateReport(String imagePath, List<Map<String, Object>> similarCases) {
        String imageUrl = buildImageUrl(imagePath);

        Map<String, Object> localResult = null;
        String localFindings = "";
        String localImpression = "";
        boolean hasLocalDraft = false;
        if (StringUtils.hasText(localBaseUrl)) {
            try {
                localResult = callLocalReportService(imageUrl, similarCases);
                localFindings = localResult.get("findings") != null ? localResult.get("findings").toString() : "";
                localImpression = localResult.get("impression") != null ? localResult.get("impression").toString() : "";
                hasLocalDraft = StringUtils.hasText(localFindings) || StringUtils.hasText(localImpression);
            } catch (Exception e) {
                log.warn("Local AI failed, fallback to Qwen: {}", e.getMessage());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are a senior thoracic radiologist. Analyze this chest X-ray and generate a clinically usable, standardized radiology report.
                Do not produce vague text. The report must be specific, structured, cautious, and suitable for clinical review.
                In findings, prioritize these structures when visible: lung fields, lung markings, hilar regions, trachea, mediastinum,
                cardiac silhouette, aortic arch, diaphragms, costophrenic angles, pleura, and visible thoracic bony structures.
                If the study is essentially normal, still describe key normal structures instead of writing only a generic normal statement.
                If an abnormality is present, describe side, location, extent, density or lucency change, morphology, and associated findings.
                If cardiac size can be reasonably estimated, you may mention an approximate cardiothoracic ratio; if not reliable, do not fabricate a number.
                Do not invent lesions that are not supported by the image. Use cautious wording when certainty is limited.
                The findings should be one coherent paragraph in Chinese medical style. The impression should be 1-3 concise Chinese clinical conclusions.
                """.stripIndent());

        if (similarCases != null && !similarCases.isEmpty()) {
            sb.append("\nReference reports from similar cases are provided below for style and structure only. Do not copy them. The current image always takes priority.\n");
            for (int i = 0; i < similarCases.size(); i++) {
                Map<String, Object> c = similarCases.get(i);
                sb.append("\nReference case ").append(i + 1).append(":\n");
                if (c.get("findings") != null) sb.append("Findings: ").append(c.get("findings")).append("\n");
                if (c.get("impression") != null) sb.append("Impression: ").append(c.get("impression")).append("\n");
            }
        }
        if (hasLocalDraft) {
            sb.append("\nA draft report from a local model (may be English and may contain errors) is provided below. Use it as reference only; verify with the image and correct any mistakes.\n");
            if (StringUtils.hasText(localFindings)) {
                sb.append("\nLocal draft findings:\n").append(localFindings).append("\n");
            }
            if (StringUtils.hasText(localImpression)) {
                sb.append("\nLocal draft impression:\n").append(localImpression).append("\n");
            }
        }

        sb.append("""

                Output rules:
                1. Output JSON only. No markdown, no commentary, no extra text.
                2. findings must be a standardized Chinese chest X-ray findings paragraph with sufficient anatomic detail.
                3. impression must be a standardized Chinese chest X-ray impression, concise and clinically usable.
                4. Avoid overly generic findings such as only saying 'cardiopulmonary silhouette is unremarkable' without elaboration.
                5. When normal, explicitly mention as many key normal structures as reasonably visible.
                6. When abnormal, describe the abnormality in a clinically meaningful way.

                JSON format:
                {
                  "findings": "Standardized detailed Chinese findings",
                  "impression": "Standardized concise Chinese impression",
                  "confidence": 0.85
                }
                """.stripIndent());

        Map<String, Object> qwenResult;
        try {
            qwenResult = callQwenVL(sb.toString(), imageUrl);
        } catch (Exception e) {
            if (localResult != null) {
                log.warn("Qwen failed, fallback to local draft: {}", e.getMessage());
                return localResult;
            }
            throw e;
        }
        qwenResult.putIfAbsent("prompt", sb.toString());
        return qwenResult;
    }

    /**
     * Polish doctor draft with LLM.
     */
    public Map<String, Object> polishReport(String findings, String impression) {
        StringBuilder polishBuilder = new StringBuilder();
        polishBuilder.append("""
                你是一名资深放射科医师，请对胸片报告草稿进行润色，使其更规范、清晰、临床可用。
                要求：
                1. 必须使用中文医学表述，禁止输出英文。
                2. 保持语义一致，不虚构不存在的病灶。
                3. 影像所见为一段完整中文描述；影像印象为1-3条简洁结论。
                4. 建议项请输出为中文数组。

                草稿：
                """.stripIndent());
        polishBuilder.append("""
                Additional constraints (strict):
                - Do NOT change correct medical terms into more generic or shorter ones.
                - Do NOT change measurements, sizes, locations, laterality, or counts.
                - Do NOT add new findings that are not in the original draft.
                - Do NOT remove existing findings unless they are clearly duplicated.
                - If unsure, keep the original wording.
                """.stripIndent());

        polishBuilder.append("影像所见：").append(findings != null ? findings : "").append("\n");
        polishBuilder.append("影像印象：").append(impression != null ? impression : "").append("\n\n");
        polishBuilder.append("""
                仅返回 JSON，格式如下：
                {
                  "polished_findings": "润色后的影像所见（中文）",
                  "polished_impression": "润色后的影像印象（中文）",
                  "changes_summary": "改动摘要（中文）",
                  "suggestions": ["建议1","建议2"]
                }
                """.stripIndent());

        String prompt = polishBuilder.toString();
        Map<String, Object> result = callDeepSeek(prompt);
        return normalizePolishResult(result);
    }

    /**
     * Review advice for signed report.
     */
    public Map<String, Object> getReviewAdvice(String findings, String impression,
                                               String qualityGrade, Double f1Score,
                                               List<String> missingLabels, List<String> extraLabels) {
        StringBuilder reviewBuilder = new StringBuilder();
        reviewBuilder.append("""
                你是一名资深放射科医师，请针对已签发报告给出简洁、可执行的中文审核建议。
                要求：
                1. 必须使用中文医学表述，禁止输出英文。
                2. key_issues 和 check_points 必须是中文数组。
                3. suggested_findings / suggested_impression 为中文参考文本。

                报告：
                """.stripIndent());
        reviewBuilder.append("影像所见：").append(findings != null ? findings : "").append("\n");
        reviewBuilder.append("影像印象：").append(impression != null ? impression : "").append("\n\n");
        reviewBuilder.append("""
                仅返回 JSON，字段如下：
                {
                  "overall_assessment": "...",
                  "key_issues": ["..."],
                  "check_points": ["..."],
                  "suggested_findings": "...",
                  "suggested_impression": "...",
                  "priority": "low|medium|high"
                }
                """.stripIndent());

        String prompt = reviewBuilder.toString();
        Map<String, Object> result = callDeepSeek(prompt);
        return normalizeReviewAdvice(result);
    }

    /**
     * Terminology normalization.
     */
    public Map<String, Object> analyzeTerms(String reportText) {
        StringBuilder termBuilder = new StringBuilder();
        termBuilder.append("""
                You are a medical terminology expert for chest X-ray reports.
                Identify only clearly incorrect, non-standard, or outdated terms and suggest standardized alternatives.
                Do NOT generalize or shorten precise clinical phrases. Do NOT replace detailed terms with more generic ones.
                Do NOT replace with abbreviations unless the abbreviation is the standard form.
                If you are not sure a term is wrong, do NOT suggest a correction.
                Prefer corrections that are more specific or equally specific in meaning.

                Report text:
                """.stripIndent());
        termBuilder.append(reportText != null ? reportText : "").append("\n\n");
        termBuilder.append("Return JSON only: {\"corrections\":[{\"original_term\":\"...\",\"suggested_term\":\"...\",\"context\":\"...\"}]} or an empty corrections list.\n");

        String prompt = termBuilder.toString();
        return callDeepSeek(prompt);
    }

    private Map<String, Object> callLocalReportService(String imageUrl, List<Map<String, Object>> similarCases) {
        String base = normalizeLocalBaseUrl();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);

        Map<String, Object> body = new HashMap<>();
        body.put("image_url", imageUrl);
        if (similarCases != null) body.put("similar_cases", similarCases);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(base + "/report/generate", entity, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new BusinessException(500, "Local AI service returned error: " + response.getStatusCode());
            }
            return response.getBody();
        } catch (ResourceAccessException e) {
            throw new BusinessException(503, "Local AI service unavailable: " + e.getMessage());
        } catch (Exception e) {
            throw new BusinessException(500, "Local AI service call failed: " + e.getMessage());
        }
    }

    public List<SimilarCaseVO> searchSimilarCasesByImage(String imagePath, Long caseId, int topK) {
        if (!isLocalEnabled() || imagePath == null || imagePath.isBlank()) {
            return List.of();
        }
        String imageUrl = buildImageUrl(imagePath);
        String base = normalizeLocalBaseUrl();

        Map<String, Object> body = new HashMap<>();
        body.put("image_url", imageUrl);
        body.put("top_k", topK);
        if (caseId != null) body.put("case_id", caseId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(base + "/retrieval/search", entity, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return List.of();
            }
            Object resultsObj = response.getBody().get("similar_cases");
            if (!(resultsObj instanceof List<?> results)) {
                return List.of();
            }
            List<SimilarCaseVO> out = new ArrayList<>();
            for (Object r : results) {
                if (!(r instanceof Map<?, ?> row)) continue;
                SimilarCaseVO vo = new SimilarCaseVO();
                Object cid = row.get("case_id");
                if (cid != null) vo.setCaseId(Long.parseLong(cid.toString()));
                Object examNo = row.get("exam_no");
                if (examNo != null) vo.setExamNo(examNo.toString());
                Object findings = row.get("findings");
                if (findings != null) vo.setFindings(findings.toString());
                Object impression = row.get("impression");
                if (impression != null) vo.setImpression(impression.toString());
                Object score = row.get("similarity_score");
                if (score != null) {
                    try {
                        vo.setSimilarityScore(new BigDecimal(score.toString()));
                    } catch (NumberFormatException ignored) {}
                }
                out.add(vo);
            }
            return out;
        } catch (Exception e) {
            log.warn("Local image retrieval failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildImageUrl(String imagePath) {
        if (imagePath == null || imagePath.startsWith("http") || imagePath.startsWith("data:")) return imagePath;
        if (imagePath.startsWith("LOCAL:")) return imagePath.substring("LOCAL:".length());
        return minioEndpoint + "/" + minioBucket + "/" + imagePath;
    }

    private String normalizeLocalBaseUrl() {
        String base = localBaseUrl != null ? localBaseUrl.trim() : "";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callQwenVL(String prompt, String imageUrl) {
        Map<String, Object> textContent = Map.of("type", "text", "text", prompt);
        List<Map<String, Object>> contentList;
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Map<String, Object> imageContent = Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", imageUrl)
            );
            contentList = List.of(imageContent, textContent);
        } else {
            contentList = List.of(textContent);
        }
        Map<String, Object> message = Map.of(
                "role", "user",
                "content", contentList
        );
        Map<String, Object> body = Map.of(
                "model", qwenModel,
                "messages", List.of(message)
        );

        String content = callOpenAICompatible(qwenBaseUrl, qwenApiKey, body, "Qwen-VL");
        return parseJson(content, Map.of(
                "findings", "AI service unavailable - please write manually.",
                "impression", "",
                "confidence", 0.0
        ));
    }

    private void checkDeepSeekConfigured() {
        if (!StringUtils.hasText(deepseekApiKey)
                || deepseekApiKey.startsWith("YOUR_")
                || deepseekApiKey.equals("sk-placeholder")) {
            throw new BusinessException(503, "DeepSeek API key is not configured");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callDeepSeek(String prompt) {
        checkDeepSeekConfigured();
        Map<String, Object> message = Map.of("role", "user", "content", prompt);
        Map<String, Object> body = new HashMap<>();
        body.put("model", "deepseek-chat");
        body.put("messages", List.of(message));
        body.put("response_format", Map.of("type", "json_object"));

        String content = callOpenAICompatible(deepseekBaseUrl, deepseekApiKey, body, "DeepSeek");
        return parseJson(content, new HashMap<>());
    }

    @SuppressWarnings("unchecked")
    private String callOpenAICompatible(String baseUrl, String apiKey, Map<String, Object> body, String provider) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            log.info("{} API call started", provider);
            long startTime = System.currentTimeMillis();

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/chat/completions", entity, Map.class);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("{} API call completed in {}ms", provider, elapsed);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new BusinessException(500, provider + " API returned error: " + response.getStatusCode());
            }
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new BusinessException(500, provider + " API returned empty response");
            }
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            return (String) msg.get("content");
        } catch (ResourceAccessException e) {
            log.error("{} API call failed [{}]: {}", provider, baseUrl, e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                throw new BusinessException(504, provider + " timeout");
            }
            throw new BusinessException(503, provider + " unavailable");
        } catch (Exception e) {
            log.error("{} API call error", provider, e);
            throw new BusinessException(500, provider + " error: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String content, Map<String, Object> fallback) {
        if (content == null || content.isBlank()) return fallback;
        try {
            String cleaned = content.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("(?s)^```[a-z]*\n?", "").replaceAll("```$", "").trim();
            }
            return objectMapper.readValue(cleaned, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse AI response JSON: {}", content);
            return fallback;
        }
    }

    private Map<String, Object> normalizePolishResult(Map<String, Object> result) {
        if (result == null) return new HashMap<>();
        Object suggestions = result.get("suggestions");
        if (suggestions instanceof String s) {
            result.put("suggestions", splitSuggestions(s));
        } else if (suggestions instanceof List<?>) {
            // keep as is
        } else if (suggestions != null) {
            result.put("suggestions", List.of(suggestions.toString()));
        }
        return result;
    }

    private Map<String, Object> normalizeReviewAdvice(Map<String, Object> result) {
        if (result == null) return new HashMap<>();
        normalizeListField(result, "key_issues");
        normalizeListField(result, "check_points");
        return result;
    }

    private void normalizeListField(Map<String, Object> result, String key) {
        Object val = result.get(key);
        if (val instanceof String s) {
            result.put(key, splitSuggestions(s));
        } else if (val instanceof List<?>) {
            // ok
        } else if (val != null) {
            result.put(key, List.of(val.toString()));
        }
    }

    private List<String> splitSuggestions(String input) {
        if (input == null) return List.of();
        String text = input.trim();
        if (text.isBlank()) return List.of();
        List<String> parts = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");
        if (lines.length > 1) {
            for (String line : lines) {
                String s = cleanupBullet(line);
                if (!s.isBlank()) parts.add(s);
            }
            if (!parts.isEmpty()) return parts;
        }
        if (text.contains("；") || text.contains(";")) {
            String[] segs = text.split("[；;]");
            for (String seg : segs) {
                String s = cleanupBullet(seg);
                if (!s.isBlank()) parts.add(s);
            }
            if (!parts.isEmpty()) return parts;
        }
        if (text.matches(".*\\d+[\\.、].*")) {
            String[] segs = text.split("(?=\\d+[\\.、])");
            for (String seg : segs) {
                String s = cleanupBullet(seg);
                if (!s.isBlank()) parts.add(s);
            }
            if (!parts.isEmpty()) return parts;
        }
        return List.of(text);
    }

    private String cleanupBullet(String s) {
        if (s == null) return "";
        return s.replaceAll("^\\s*[\\-•]\\s*", "")
                .replaceAll("^\\s*\\d+[\\.、]\\s*", "")
                .trim();
    }
}
