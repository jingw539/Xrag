-- 清理无效报告：关联的 case_id 或 retrieval_log_id 不存在，或者关联的影像已删除
DELETE FROM report_info
WHERE case_id NOT IN (SELECT case_id FROM case_info)
   OR retrieval_log_id NOT IN (SELECT retrieval_id FROM retrieval_log)
   OR (retrieval_log_id IN (
       SELECT r.retrieval_id FROM retrieval_log r
       LEFT JOIN image_info i ON r.query_image_id = i.image_id
       WHERE i.image_id IS NULL
   ));

-- 清理无效的评测记录：关联的 report_id 不存在
DELETE FROM eval_result
WHERE report_id NOT IN (SELECT report_id FROM report_info);

-- 清理无效的术语纠正：关联的 report_id 不存在
DELETE FROM term_correction
WHERE report_id NOT IN (SELECT report_id FROM report_info);

-- 清理无效的标注：关联的 image_id 或 report_id 不存在
DELETE FROM image_annotation
WHERE image_id NOT IN (SELECT image_id FROM image_info)
   OR (report_id IS NOT NULL AND report_id NOT IN (SELECT report_id FROM report_info));

-- 清理无效的报告编辑历史：关联的 report_id 不存在
DELETE FROM report_edit_history
WHERE report_id NOT IN (SELECT report_id FROM report_info);

-- 清理无效的关键警情：关联的 case_id 或 report_id 不存在
DELETE FROM critical_alert
WHERE case_id NOT IN (SELECT case_id FROM case_info)
   OR (report_id IS NOT NULL AND report_id NOT IN (SELECT report_id FROM report_info));
