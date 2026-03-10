package com.hospital.xray.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)
public class OrphanDataCleanup implements ApplicationRunner {

    private static final String ORPHAN_REPORT_CTE = """
            WITH orphan_reports AS (
                SELECT r.report_id
                FROM report_info r
                WHERE NOT EXISTS (
                          SELECT 1 FROM case_info c WHERE c.case_id = r.case_id
                      )
                   OR (
                          r.retrieval_log_id IS NOT NULL
                      AND NOT EXISTS (
                          SELECT 1 FROM retrieval_log rl WHERE rl.retrieval_id = r.retrieval_log_id
                      ))
                   OR EXISTS (
                          SELECT 1
                          FROM retrieval_log rl
                          LEFT JOIN image_info i ON i.image_id = rl.query_image_id
                          WHERE rl.retrieval_id = r.retrieval_log_id
                            AND i.image_id IS NULL
                      )
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int cleanedEval = cleanupOrphanEvalResults();
            int cleanedTerms = cleanupOrphanTermCorrections();
            int cleanedHistory = cleanupOrphanEditHistory();
            int cleanedAlerts = cleanupOrphanCriticalAlerts();
            int cleanedReportAnnotations = cleanupOrphanReportAnnotations();
            int cleanedReports = cleanupOrphanReports();
            int cleanedImageAnnotations = cleanupOrphanImageAnnotations();
            int cleanedCaseAlerts = cleanupOrphanCaseAlerts();

            int total = cleanedReports + cleanedEval + cleanedTerms + cleanedHistory
                    + cleanedAlerts + cleanedReportAnnotations + cleanedImageAnnotations + cleanedCaseAlerts;

            if (total > 0) {
                log.info("[OrphanDataCleanup] cleanup finished, total={} report={} eval={} term={} history={} reportAnno={} alert={} imageAnno={} caseAlert={}",
                        total, cleanedReports, cleanedEval, cleanedTerms, cleanedHistory,
                        cleanedReportAnnotations, cleanedAlerts, cleanedImageAnnotations, cleanedCaseAlerts);
            } else {
                log.info("[OrphanDataCleanup] no orphan data found");
            }
        } catch (Exception e) {
            log.error("[OrphanDataCleanup] cleanup failed: {}", e.getMessage(), e);
        }
    }

    private int cleanupOrphanEvalResults() {
        return jdbcTemplate.update(ORPHAN_REPORT_CTE + """
                DELETE FROM eval_result e
                WHERE EXISTS (
                    SELECT 1 FROM orphan_reports o WHERE o.report_id = e.report_id
                )
                """);
    }

    private int cleanupOrphanTermCorrections() {
        return jdbcTemplate.update(ORPHAN_REPORT_CTE + """
                DELETE FROM term_correction t
                WHERE EXISTS (
                    SELECT 1 FROM orphan_reports o WHERE o.report_id = t.report_id
                )
                """);
    }

    private int cleanupOrphanEditHistory() {
        return jdbcTemplate.update(ORPHAN_REPORT_CTE + """
                DELETE FROM report_edit_history h
                WHERE EXISTS (
                    SELECT 1 FROM orphan_reports o WHERE o.report_id = h.report_id
                )
                """);
    }

    private int cleanupOrphanCriticalAlerts() {
        return jdbcTemplate.update(ORPHAN_REPORT_CTE + """
                DELETE FROM critical_alert a
                WHERE a.report_id IS NOT NULL
                  AND EXISTS (
                      SELECT 1 FROM orphan_reports o WHERE o.report_id = a.report_id
                  )
                """);
    }

    private int cleanupOrphanReportAnnotations() {
        return jdbcTemplate.update(ORPHAN_REPORT_CTE + """
                DELETE FROM image_annotation a
                WHERE a.report_id IS NOT NULL
                  AND EXISTS (
                      SELECT 1 FROM orphan_reports o WHERE o.report_id = a.report_id
                  )
                """);
    }

    private int cleanupOrphanReports() {
        return jdbcTemplate.update(ORPHAN_REPORT_CTE + """
                DELETE FROM report_info r
                WHERE EXISTS (
                    SELECT 1 FROM orphan_reports o WHERE o.report_id = r.report_id
                )
                """);
    }

    private int cleanupOrphanImageAnnotations() {
        return jdbcTemplate.update("""
                DELETE FROM image_annotation a
                WHERE NOT EXISTS (
                    SELECT 1 FROM image_info i WHERE i.image_id = a.image_id
                )
                """);
    }

    private int cleanupOrphanCaseAlerts() {
        return jdbcTemplate.update("""
                DELETE FROM critical_alert a
                WHERE NOT EXISTS (
                    SELECT 1 FROM case_info c WHERE c.case_id = a.case_id
                )
                """);
    }
}
