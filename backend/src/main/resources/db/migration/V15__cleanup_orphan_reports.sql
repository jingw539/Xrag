-- Cleanup orphaned records without valid parents
DELETE FROM report_info
WHERE case_id NOT IN (SELECT case_id FROM case_info)
   OR retrieval_log_id NOT IN (SELECT retrieval_id FROM retrieval_log)
   OR (retrieval_log_id IN (
       SELECT r.retrieval_id FROM retrieval_log r
       LEFT JOIN image_info i ON r.query_image_id = i.image_id
       WHERE i.image_id IS NULL
   ));

DELETE FROM term_correction
WHERE report_id NOT IN (SELECT report_id FROM report_info);

DELETE FROM image_annotation
WHERE image_id NOT IN (SELECT image_id FROM image_info)
   OR (report_id IS NOT NULL AND report_id NOT IN (SELECT report_id FROM report_info));

DELETE FROM report_edit_history
WHERE report_id NOT IN (SELECT report_id FROM report_info);
