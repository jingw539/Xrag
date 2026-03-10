-- Remove legacy eval artifacts and unused alert tables
DELETE FROM ai_model_info
WHERE model_type = 'LABEL_EVAL'
   OR LOWER(model_name) = 'label_eval'
   OR model_id = 1;

DROP TABLE IF EXISTS eval_result CASCADE;
DROP TABLE IF EXISTS critical_alert CASCADE;
