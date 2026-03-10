package com.hospital.xray.client;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    /**
     * Generate chest X-ray report. Prefer local self-trained model if configured.
     */
    public Map<String, Object> generateReport(String imagePath, List<Map<String, Object>> similarCases) {
        String imageUrl = buildImageUrl(imagePath);

        if (StringUtils.hasText(localBaseUrl)) {
            return callLocalReportService(imageUrl, similarCases);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are a senior thoracic radiologist. Analyze this chest X-ray and generate a clinically usable, standardized radiology report.
                Do not produce vague text. The report must be specific, structured, cautious, and suitable for clinical review.
                In findings, prioritize these structures when visible: lung fields, lung markings, hilar regions, trachea, mediastinum, cardiac silhouette, aortic arch, diaphragms, costophrenic angles, pleura, and visible thoracic bony structures.
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

        return callQwenVL(sb.toString(), imageUrl);
    }

    /**
     * Polish doctor draft with LLM.
     */
    public Map<String, Object> polishReport(String findings, String impression) {
        StringBuilder polishBuilder = new StringBuilder();
        polishBuilder.append("""
                You are a senior radiologist. Polish the draft CXR report to be clearer, standardized, and clinically precise.

                Draft report:
                """.stripIndent());
        polishBuilder.append("Findings: ").append(findings != null ? findings : "").append("\n");
        polishBuilder.append("Impression: ").append(impression != null ? impression : "").append("\n\n");
        polishBuilder.append("Output JSON only with fields: polished_findings, polished_impression, changes_summary, suggestions.\n");

        String prompt = polishBuilder.toString();
        return callDeepSeek(prompt);
    }

    /**
     * Review advice for signed report.
     */
    public Map<String, Object> getReviewAdvice(String findings, String impression,
                                               String qualityGrade, Double f1Score,
                                               List<String> missingLabels, List<String> extraLabels) {
        StringBuilder reviewBuilder = new StringBuilder();
        reviewBuilder.append("""
                You are a senior radiologist and quality reviewer. Provide concise, actionable review advice for the signed report.

                Report:
                """.stripIndent());
        reviewBuilder.append("Findings: ").append(findings != null ? findings : "").append("\n");
        reviewBuilder.append("Impression: ").append(impression != null ? impression : "").append("\n\n");
        reviewBuilder.append("Return JSON only with: overall_assessment, key_issues, check_points, suggested_findings, suggested_impression, priority.\n");

        String prompt = reviewBuilder.toString();
        return callDeepSeek(prompt);
    }

    /**
     * Terminology normalization.
     */
    public Map<String, Object> analyzeTerms(String reportText) {
        StringBuilder termBuilder = new StringBuilder();
        termBuilder.append("""
                You are a medical terminology expert. Identify non-standard or outdated terms in the CXR report and suggest standardized alternatives.

                Report text:
                """.stripIndent());
        termBuilder.append(reportText != null ? reportText : "").append("\n\n");
        termBuilder.append("Return JSON only: {\"corrections\":[{\"original_term\":\"...\",\"suggested_term\":\"...\",\"context\":\"...\"}]} or an empty corrections list.\n");

        String prompt = termBuilder.toString();
        return callDeepSeek(prompt);
    }

    private Map<String, Object> callLocalReportService(String imageUrl, List<Map<String, Object>> similarCases) {
        String base = localBaseUrl != null ? localBaseUrl.trim() : "";
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

    private String buildImageUrl(String imagePath) {
        if (imagePath == null || imagePath.startsWith("http") || imagePath.startsWith("data:")) return imagePath;
        return minioEndpoint + "/" + minioBucket + "/" + imagePath;
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
}
