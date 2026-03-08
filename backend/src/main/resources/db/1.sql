-- =============================================================
-- 胸部X光智能辅助诊断系统 (XRAG) 数据库初始化脚本
-- 数据库：PostgreSQL
-- 版本：v2.0
-- 模块：用户认证、病例管理、影像管理、RAG检索、
--        结构化报告生成、CheXbert临床一致性评测、
--        危急值预警、术语规范化、质控统计
-- =============================================================


-- ============================================================
--  一、角色与用户认证
-- ============================================================

-- 角色表
CREATE TABLE sys_role (
    role_id   BIGINT       PRIMARY KEY,
    role_code VARCHAR(32)  NOT NULL UNIQUE,
    role_name VARCHAR(64)  NOT NULL,
    remark    VARCHAR(256)
);

CREATE UNIQUE INDEX idx_role_code ON sys_role(role_code);

INSERT INTO sys_role (role_id, role_code, role_name, remark) VALUES
    (1, 'DOCTOR', '放射科医生', '负责查看病例、AI报告审核、编辑和签发报告'),
    (2, 'QC',     '管理与质控', '负责质控统计、典型病例管理、CheXbert评测查看'),
    (3, 'ADMIN',  '系统管理员', '负责系统配置、用户管理、操作日志查阅');

COMMENT ON TABLE  sys_role           IS '角色表';
COMMENT ON COLUMN sys_role.role_id   IS '角色ID';
COMMENT ON COLUMN sys_role.role_code IS '角色编码：DOCTOR/QC/ADMIN';
COMMENT ON COLUMN sys_role.role_name IS '角色名称';
COMMENT ON COLUMN sys_role.remark    IS '角色说明';

-- 用户表
CREATE TABLE sys_user (
    user_id       BIGINT       PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    real_name     VARCHAR(64),
    role_id       BIGINT       NOT NULL,
    department    VARCHAR(64),
    status        SMALLINT     NOT NULL DEFAULT 1,
    last_login_at TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES sys_role(role_id)
);

CREATE UNIQUE INDEX idx_username    ON sys_user(username);
CREATE INDEX       idx_user_role_id ON sys_user(role_id);
CREATE INDEX       idx_user_status  ON sys_user(status);

COMMENT ON TABLE  sys_user               IS '用户表';
COMMENT ON COLUMN sys_user.user_id       IS '用户ID，雪花算法生成';
COMMENT ON COLUMN sys_user.username      IS '登录账号';
COMMENT ON COLUMN sys_user.password_hash IS '密码哈希值（BCrypt）';
COMMENT ON COLUMN sys_user.real_name     IS '真实姓名';
COMMENT ON COLUMN sys_user.role_id       IS '所属角色ID';
COMMENT ON COLUMN sys_user.department    IS '所属科室';
COMMENT ON COLUMN sys_user.status        IS '账号状态：1=启用，0=禁用';
COMMENT ON COLUMN sys_user.last_login_at IS '最后登录时间';
COMMENT ON COLUMN sys_user.created_at    IS '创建时间';
COMMENT ON COLUMN sys_user.updated_at    IS '更新时间';

-- 用户角色关联表（支持多角色扩展）
CREATE TABLE sys_user_role (
    id         BIGINT    PRIMARY KEY,
    user_id    BIGINT    NOT NULL,
    role_id    BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(role_id),
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id)
);

CREATE INDEX idx_user_role_user_id ON sys_user_role(user_id);
CREATE INDEX idx_user_role_role_id ON sys_user_role(role_id);

COMMENT ON TABLE  sys_user_role            IS '用户角色关联表';
COMMENT ON COLUMN sys_user_role.id         IS '主键ID';
COMMENT ON COLUMN sys_user_role.user_id    IS '用户ID';
COMMENT ON COLUMN sys_user_role.role_id    IS '角色ID';
COMMENT ON COLUMN sys_user_role.created_at IS '绑定时间';

-- JWT刷新令牌表
CREATE TABLE sys_refresh_token (
    token_id    BIGINT       PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    token_hash  VARCHAR(128) NOT NULL UNIQUE,
    device_info VARCHAR(256),
    client_ip   VARCHAR(64),
    expires_at  TIMESTAMP    NOT NULL,
    revoked     SMALLINT     NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_token_user FOREIGN KEY (user_id) REFERENCES sys_user(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_token_user_id ON sys_refresh_token(user_id);
CREATE INDEX idx_token_hash    ON sys_refresh_token(token_hash);
CREATE INDEX idx_token_expires ON sys_refresh_token(expires_at);

COMMENT ON TABLE  sys_refresh_token             IS 'JWT刷新令牌表';
COMMENT ON COLUMN sys_refresh_token.token_id    IS '令牌ID';
COMMENT ON COLUMN sys_refresh_token.user_id     IS '所属用户ID';
COMMENT ON COLUMN sys_refresh_token.token_hash  IS '令牌哈希值（SHA-256）';
COMMENT ON COLUMN sys_refresh_token.device_info IS '设备信息（User-Agent）';
COMMENT ON COLUMN sys_refresh_token.client_ip   IS '客户端IP';
COMMENT ON COLUMN sys_refresh_token.expires_at  IS '过期时间';
COMMENT ON COLUMN sys_refresh_token.revoked     IS '是否已吊销：0=有效，1=已吊销';
COMMENT ON COLUMN sys_refresh_token.created_at  IS '签发时间';

-- ============================================================
--  二、系统支撑表
-- ============================================================

-- 操作日志表
CREATE TABLE sys_operation_log (
    log_id         BIGINT       PRIMARY KEY,
    user_id        BIGINT,
    operation_type VARCHAR(32)  NOT NULL,
    target_type    VARCHAR(32),
    target_id      VARCHAR(64),
    detail         TEXT,
    client_ip      VARCHAR(64),
    api_path       VARCHAR(256),
    elapsed_ms     INT,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_oplog_user_id    ON sys_operation_log(user_id);
CREATE INDEX idx_oplog_type       ON sys_operation_log(operation_type);
CREATE INDEX idx_oplog_created_at ON sys_operation_log(created_at DESC);
CREATE INDEX idx_oplog_user_time  ON sys_operation_log(user_id, created_at DESC);
CREATE INDEX idx_oplog_type_time  ON sys_operation_log(operation_type, created_at DESC);

COMMENT ON TABLE  sys_operation_log                IS '系统操作日志表（满足审核留痕合规要求）';
COMMENT ON COLUMN sys_operation_log.log_id         IS '日志ID';
COMMENT ON COLUMN sys_operation_log.user_id        IS '操作用户ID';
COMMENT ON COLUMN sys_operation_log.operation_type IS '操作类型：LOGIN/LOGOUT/CASE_VIEW/CASE_CREATE/CASE_UPDATE/CASE_DELETE/IMAGE_UPLOAD/IMAGE_DELETE/REPORT_GENERATE/REPORT_EDIT/REPORT_SIGN/REPORT_REGENERATE/RETRIEVAL/EVAL_TRIGGER/ALERT_ACK/TYPICAL_MARK/CONFIG_UPDATE/USER_CREATE/USER_UPDATE';
COMMENT ON COLUMN sys_operation_log.target_type    IS '目标资源类型：CASE/IMAGE/REPORT/USER/CONFIG/ALERT';
COMMENT ON COLUMN sys_operation_log.target_id      IS '操作目标ID（如病例ID、影像ID）';
COMMENT ON COLUMN sys_operation_log.detail         IS '操作详情描述';
COMMENT ON COLUMN sys_operation_log.client_ip      IS '客户端IP地址';
COMMENT ON COLUMN sys_operation_log.api_path       IS 'API请求路径';
COMMENT ON COLUMN sys_operation_log.elapsed_ms     IS '操作耗时（毫秒）';
COMMENT ON COLUMN sys_operation_log.created_at     IS '操作时间';

-- 系统配置表
CREATE TABLE sys_config (
    config_id    BIGINT       PRIMARY KEY,
    config_key   VARCHAR(64)  NOT NULL UNIQUE,
    config_value VARCHAR(512) NOT NULL,
    description  VARCHAR(256),
    updated_by   BIGINT,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_config_user FOREIGN KEY (updated_by) REFERENCES sys_user(user_id)
);

CREATE UNIQUE INDEX idx_config_key        ON sys_config(config_key);
CREATE INDEX       idx_config_updated_by  ON sys_config(updated_by);

INSERT INTO sys_config (config_id, config_key, config_value, description) VALUES
    (1,  'retrieval_top_k',       '3',                             '检索返回相似病例数量（Top-K），调研建议值为3'),
    (2,  'similarity_threshold',  '0.85',                          '相似度硬阈值（余弦相似度），低于此值提示人工书写'),
    (3,  'critical_labels',       'Pneumothorax,Pleural Effusion', '危急值病理标签列表（逗号分隔，CheXbert标签名）'),
    (4,  'critical_label_prob',   '0.7',                           '危急值标签触发预警的概率阈值'),
    (5,  'ai_report_timeout',     '30',                            'AI报告生成超时秒数（目标10s，超时上限30s）'),
    (6,  'report_f1_grade_a',     '0.80',                          '报告质量A级（优秀）的CheXbert F1阈值'),
    (7,  'report_f1_grade_b',     '0.70',                          '报告质量B级（良好）的CheXbert F1阈值'),
    (8,  'report_f1_grade_c',     '0.50',                          '报告质量C级（一般）的CheXbert F1阈值'),
    (9,  'term_norm_threshold',   '0.9',                           '术语规范率低于此值时提示人工审核'),
    (10, 'temp_file_ttl_days',    '7',                             '临时上传文件保留天数'),
    (11, 'log_retention_days',    '30',                            '操作日志保留天数'),
    (12, 'faiss_index_path',      '/data/faiss/index.bin',         'FAISS向量索引文件路径'),
    (13, 'medclip_model_path',    '/models/medclip',               'MedCLIP图像编码器模型路径'),
    (14, 'llava_model_path',      '/models/llava-med',             'LLaVA-Med-LoRA报告生成模型路径'),
    (15, 'chexbert_model_path',   '/models/chexbert',              'CheXbert评测模型路径'),
    (16, 'pacs_shared_dir',       '/data/pacs',                    'PACS影像共享目录路径（预留）'),
    (17, 'his_api_url',           'http://his.hospital.com/api',   'HIS接口URL（预留）');

COMMENT ON TABLE  sys_config              IS '系统配置表';
COMMENT ON COLUMN sys_config.config_id   IS '配置ID';
COMMENT ON COLUMN sys_config.config_key  IS '配置键';
COMMENT ON COLUMN sys_config.config_value IS '配置值';
COMMENT ON COLUMN sys_config.description IS '配置描述';
COMMENT ON COLUMN sys_config.updated_by  IS '最后更新人ID';
COMMENT ON COLUMN sys_config.updated_at  IS '更新时间';

-- 数据字典表
CREATE TABLE sys_dict (
    dict_id   BIGINT      PRIMARY KEY,
    dict_code VARCHAR(64) NOT NULL UNIQUE,
    dict_name VARCHAR(64) NOT NULL
);

CREATE UNIQUE INDEX idx_dict_code ON sys_dict(dict_code);

INSERT INTO sys_dict (dict_id, dict_code, dict_name) VALUES
    (1, 'REPORT_STATUS',   '报告状态'),
    (2, 'PATHOLOGY_LABEL', '病理标签（CheXbert 14类）'),
    (3, 'VIEW_POSITION',   '投照体位'),
    (4, 'FILE_TYPE',       '影像文件类型'),
    (5, 'QUALITY_GRADE',   '报告质量评级'),
    (6, 'ALERT_STATUS',    '危急值预警状态'),
    (7, 'ALERT_ACTION',    '危急值处理动作');

COMMENT ON TABLE  sys_dict           IS '数据字典表';
COMMENT ON COLUMN sys_dict.dict_id   IS '字典ID';
COMMENT ON COLUMN sys_dict.dict_code IS '字典编码';
COMMENT ON COLUMN sys_dict.dict_name IS '字典名称';

-- 数据字典项表
CREATE TABLE sys_dict_item (
    item_id    BIGINT       PRIMARY KEY,
    dict_id    BIGINT       NOT NULL,
    item_code  VARCHAR(64)  NOT NULL,
    item_name  VARCHAR(128) NOT NULL,
    sort_order INT,
    CONSTRAINT fk_dict_item_dict FOREIGN KEY (dict_id) REFERENCES sys_dict(dict_id)
);

CREATE INDEX idx_dict_item_dict_id ON sys_dict_item(dict_id);
CREATE INDEX idx_dict_item_code    ON sys_dict_item(item_code);

-- 报告状态
INSERT INTO sys_dict_item (item_id, dict_id, item_code, item_name, sort_order) VALUES
    (1, 1, 'NONE',     '未生成', 1),
    (2, 1, 'AI_DRAFT', 'AI草稿', 2),
    (3, 1, 'EDITING',  '编辑中', 3),
    (4, 1, 'SIGNED',   '已签发', 4);

-- CheXbert 14类病理标签
INSERT INTO sys_dict_item (item_id, dict_id, item_code, item_name, sort_order) VALUES
    (11, 2, 'Atelectasis',               '肺不张',       1),
    (12, 2, 'Cardiomegaly',              '心脏肥大',     2),
    (13, 2, 'Consolidation',             '实变',         3),
    (14, 2, 'Edema',                     '水肿',         4),
    (15, 2, 'Enlarged Cardiomediastinum','心纵隔增大',   5),
    (16, 2, 'Fracture',                  '骨折',         6),
    (17, 2, 'Lung Lesion',               '肺部病变',     7),
    (18, 2, 'Lung Opacity',              '肺部阴影',     8),
    (19, 2, 'No Finding',                '无异常发现',   9),
    (20, 2, 'Pleural Effusion',          '胸腔积液',     10),
    (21, 2, 'Pleural Other',             '其他胸膜病变', 11),
    (22, 2, 'Pneumonia',                 '肺炎',         12),
    (23, 2, 'Pneumothorax',              '气胸',         13),
    (24, 2, 'Support Devices',           '支持设备',     14);

-- 投照体位
INSERT INTO sys_dict_item (item_id, dict_id, item_code, item_name, sort_order) VALUES
    (31, 3, 'PA',      '后前位', 1),
    (32, 3, 'AP',      '前后位', 2),
    (33, 3, 'LATERAL', '侧位',   3),
    (34, 3, 'OBLIQUE', '斜位',   4);

-- 文件类型
INSERT INTO sys_dict_item (item_id, dict_id, item_code, item_name, sort_order) VALUES
    (41, 4, 'JPG', 'JPEG图像',  1),
    (42, 4, 'PNG', 'PNG图像',   2),
    (43, 4, 'DCM', 'DICOM文件', 3),
    (44, 4, 'BMP', 'BMP图像',   4);

-- 报告质量评级（对应CheXbert F1阈值）
INSERT INTO sys_dict_item (item_id, dict_id, item_code, item_name, sort_order) VALUES
    (51, 5, 'A', '优秀（F1≥0.80，可直接签发）',           1),
    (52, 5, 'B', '良好（F1≥0.70，建议快速浏览后签发）',   2),
    (53, 5, 'C', '一般（F1≥0.50，建议详细审核后签发）',   3),
    (54, 5, 'D', '待改进（F1<0.50，建议重新生成或人工书写）', 4);

-- 危急值预警状态
INSERT INTO sys_dict_item (item_id, dict_id, item_code, item_name, sort_order) VALUES
    (61, 6, 'PENDING',      '待处理', 1),
    (62, 6, 'ACKNOWLEDGED', '已确认', 2),
    (63, 6, 'ESCALATED',    '已上转', 3),
    (64, 6, 'DISMISSED',    '已驳回', 4);

-- 危急值处理动作
INSERT INTO sys_dict_item (item_id, dict_id, item_code, item_name, sort_order) VALUES
    (71, 7, 'ACKNOWLEDGED', '确认知晓', 1),
    (72, 7, 'ESCALATED',    '上转处理', 2),
    (73, 7, 'DISMISSED',    '误报驳回', 3);

COMMENT ON TABLE  sys_dict_item            IS '数据字典项表';
COMMENT ON COLUMN sys_dict_item.item_id    IS '字典项ID';
COMMENT ON COLUMN sys_dict_item.dict_id    IS '所属字典ID';
COMMENT ON COLUMN sys_dict_item.item_code  IS '字典项编码';
COMMENT ON COLUMN sys_dict_item.item_name  IS '字典项名称';
COMMENT ON COLUMN sys_dict_item.sort_order IS '排序序号';

-- ============================================================
--  三、AI模型注册表
-- ============================================================

CREATE TABLE ai_model_info (
    model_id    BIGINT       PRIMARY KEY,
    model_name  VARCHAR(128) NOT NULL,
    model_type  VARCHAR(64)  NOT NULL,
    version     VARCHAR(64)  NOT NULL,
    description TEXT,
    is_active   SMALLINT     NOT NULL DEFAULT 1,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_model_name_version UNIQUE (model_name, version)
);

CREATE INDEX idx_model_type ON ai_model_info(model_type);

INSERT INTO ai_model_info (model_id, model_name, model_type, version, description, is_active) VALUES
    (1, 'CheXbert',       'CHEXBERT',   'v1.0', 'CheXbert：14类病理标签自动提取与临床一致性评测（EMNLP 2020）',           1),
    (2, 'MedCLIP',        'RETRIEVER',  'v1.0', 'MedCLIP：解耦对比学习医学影像编码器，用于FAISS向量检索（EMNLP 2022）',   1),
    (3, 'LLaVA-Med-LoRA', 'REPORT_GEN', 'v1.0', 'LLaVA-Med-7B + LoRA微调（rank=16,alpha=32），结构化报告生成模型',        1);

COMMENT ON TABLE  ai_model_info             IS 'AI模型注册表';
COMMENT ON COLUMN ai_model_info.model_id    IS '模型ID';
COMMENT ON COLUMN ai_model_info.model_name  IS '模型名称';
COMMENT ON COLUMN ai_model_info.model_type  IS '模型类型：REPORT_GEN=报告生成，RETRIEVER=检索编码，CHEXBERT=临床评测';
COMMENT ON COLUMN ai_model_info.version     IS '模型版本';
COMMENT ON COLUMN ai_model_info.description IS '模型描述（含论文来源）';
COMMENT ON COLUMN ai_model_info.is_active   IS '是否当前激活版本：1=激活，0=停用';
COMMENT ON COLUMN ai_model_info.created_at  IS '注册时间';

-- ============================================================
--  四、病例与影像管理
-- ============================================================

-- 病例表
CREATE TABLE case_info (
    case_id         BIGINT      PRIMARY KEY,
    exam_no         VARCHAR(64) NOT NULL UNIQUE,
    patient_anon_id VARCHAR(64) NOT NULL,
    gender          CHAR(1),
    age             INT,
    exam_time       TIMESTAMP   NOT NULL,
    body_part       VARCHAR(64) NOT NULL,
    department      VARCHAR(64),
    report_status   VARCHAR(16) NOT NULL DEFAULT 'NONE',
    is_typical      SMALLINT    NOT NULL DEFAULT 0,
    typical_tags    VARCHAR(256),
    typical_remark  TEXT,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_case_exam_no       ON case_info(exam_no);
CREATE INDEX idx_case_exam_time     ON case_info(exam_time DESC);
CREATE INDEX idx_case_patient       ON case_info(patient_anon_id);
CREATE INDEX idx_case_report_status ON case_info(report_status);
CREATE INDEX idx_case_is_typical    ON case_info(is_typical);
CREATE INDEX idx_case_time_status   ON case_info(exam_time DESC, report_status);
CREATE INDEX idx_case_dept_time     ON case_info(department, exam_time DESC);

COMMENT ON TABLE  case_info                 IS '病例信息表';
COMMENT ON COLUMN case_info.case_id         IS '病例唯一标识，雪花算法生成';
COMMENT ON COLUMN case_info.exam_no         IS '检查号，与HIS/RIS对接的关键字段';
COMMENT ON COLUMN case_info.patient_anon_id IS '患者匿名ID，DICOM脱敏后的哈希ID';
COMMENT ON COLUMN case_info.gender          IS '性别：M=男，F=女';
COMMENT ON COLUMN case_info.age             IS '年龄';
COMMENT ON COLUMN case_info.exam_time       IS '检查时间';
COMMENT ON COLUMN case_info.body_part       IS '检查部位（默认：胸部）';
COMMENT ON COLUMN case_info.department      IS '科室';
COMMENT ON COLUMN case_info.report_status   IS '报告状态：NONE=未生成，AI_DRAFT=AI草稿，EDITING=编辑中，SIGNED=已签发';
COMMENT ON COLUMN case_info.is_typical      IS '是否典型病例：0=否，1=是（可作为RAG检索库候选）';
COMMENT ON COLUMN case_info.typical_tags    IS '典型病例标签，多标签逗号分隔（如"气胸,经典案例"）';
COMMENT ON COLUMN case_info.typical_remark  IS '典型病例备注说明';
COMMENT ON COLUMN case_info.created_at      IS '创建时间';
COMMENT ON COLUMN case_info.updated_at      IS '更新时间';

-- 影像表
CREATE TABLE image_info (
    image_id      BIGINT       PRIMARY KEY,
    case_id       BIGINT       NOT NULL,
    file_path     VARCHAR(512) NOT NULL,
    file_name     VARCHAR(256) NOT NULL,
    file_type     VARCHAR(16)  NOT NULL,
    file_size     BIGINT       NOT NULL,
    view_position VARCHAR(32),
    img_width     INT,
    img_height    INT,
    shoot_time    TIMESTAMP,
    dicom_uid     VARCHAR(128),
    study_uid     VARCHAR(128),
    series_uid    VARCHAR(128),
    instance_uid  VARCHAR(128),
    pixel_spacing VARCHAR(64),
    window_center INT,
    window_width  INT,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_image_case FOREIGN KEY (case_id) REFERENCES case_info(case_id) ON DELETE CASCADE
);

CREATE INDEX idx_image_case_id   ON image_info(case_id);
CREATE INDEX idx_image_dicom_uid ON image_info(dicom_uid);
CREATE INDEX idx_image_study_uid ON image_info(study_uid);

COMMENT ON TABLE  image_info               IS '影像信息表';
COMMENT ON COLUMN image_info.image_id      IS '影像唯一标识';
COMMENT ON COLUMN image_info.case_id       IS '所属病例ID';
COMMENT ON COLUMN image_info.file_path     IS 'MinIO中的对象路径';
COMMENT ON COLUMN image_info.file_name     IS '原始文件名';
COMMENT ON COLUMN image_info.file_type     IS '文件类型：JPG/PNG/DCM';
COMMENT ON COLUMN image_info.file_size     IS '文件大小（字节）';
COMMENT ON COLUMN image_info.view_position IS '投照体位：PA/AP/LATERAL/OBLIQUE';
COMMENT ON COLUMN image_info.img_width     IS '图像宽度（像素）';
COMMENT ON COLUMN image_info.img_height    IS '图像高度（像素）';
COMMENT ON COLUMN image_info.shoot_time    IS '拍摄时间';
COMMENT ON COLUMN image_info.dicom_uid     IS 'DICOM SOP Instance UID，PACS对接';
COMMENT ON COLUMN image_info.study_uid     IS 'DICOM Study Instance UID，PACS对接';
COMMENT ON COLUMN image_info.series_uid    IS 'DICOM Series Instance UID，PACS对接';
COMMENT ON COLUMN image_info.instance_uid  IS 'DICOM SOP Instance UID（备用），PACS对接';
COMMENT ON COLUMN image_info.pixel_spacing IS '像素间距（DICOM Tag 0028,0030），如"0.143\\0.143"，用于空间定标';
COMMENT ON COLUMN image_info.window_center IS '窗位（DICOM Tag 0028,1050），影像浏览窗宽窗位调节';
COMMENT ON COLUMN image_info.window_width  IS '窗宽（DICOM Tag 0028,1051），影像浏览窗宽窗位调节';
COMMENT ON COLUMN image_info.created_at    IS '上传时间';

-- ============================================================
--  五、RAG检索模块
-- ============================================================

-- RAG检索日志表
CREATE TABLE retrieval_log (
    retrieval_id       BIGINT        PRIMARY KEY,
    case_id            BIGINT        NOT NULL,
    query_image_id     BIGINT,
    retriever_model_id BIGINT,
    top_k              INT           NOT NULL DEFAULT 3,
    similar_case_ids   TEXT,
    similarity_scores  TEXT,
    all_above_threshold SMALLINT    NOT NULL DEFAULT 1,
    elapsed_ms         INT,
    retrieval_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_retrieval_case  FOREIGN KEY (case_id)            REFERENCES case_info(case_id),
    CONSTRAINT fk_retrieval_image FOREIGN KEY (query_image_id)     REFERENCES image_info(image_id),
    CONSTRAINT fk_retrieval_model FOREIGN KEY (retriever_model_id) REFERENCES ai_model_info(model_id)
);

CREATE INDEX idx_retrieval_case_id ON retrieval_log(case_id);
CREATE INDEX idx_retrieval_time    ON retrieval_log(retrieval_time DESC);

COMMENT ON TABLE  retrieval_log                      IS 'RAG相似病例检索日志表';
COMMENT ON COLUMN retrieval_log.retrieval_id         IS '检索记录ID';
COMMENT ON COLUMN retrieval_log.case_id              IS '查询病例ID';
COMMENT ON COLUMN retrieval_log.query_image_id       IS '查询影像ID（用于MedCLIP编码）';
COMMENT ON COLUMN retrieval_log.retriever_model_id   IS '使用的检索模型ID（MedCLIP）';
COMMENT ON COLUMN retrieval_log.top_k                IS '检索数量K（来自sys_config.retrieval_top_k）';
COMMENT ON COLUMN retrieval_log.similar_case_ids     IS '相似病例ID列表（JSON数组，按相似度降序）';
COMMENT ON COLUMN retrieval_log.similarity_scores    IS '对应余弦相似度列表（JSON数组，0-1，>0.85触发可信检索）';
COMMENT ON COLUMN retrieval_log.all_above_threshold  IS '所有结果是否均超过相似度阈值：1=是，0=否（含不可信结果）';
COMMENT ON COLUMN retrieval_log.elapsed_ms           IS '检索耗时（毫秒，目标<500ms）';
COMMENT ON COLUMN retrieval_log.retrieval_time       IS '检索时间';

-- ============================================================
--  六、报告生成模块
-- ============================================================

-- 报告表
CREATE TABLE report_info (
    report_id        BIGINT       PRIMARY KEY,
    case_id          BIGINT       NOT NULL,
    report_status    VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    gen_model_id     BIGINT,
    retrieval_log_id BIGINT,
    ai_findings      TEXT,
    ai_impression    TEXT,
    ai_prompt        TEXT,
    final_findings   TEXT,
    final_impression TEXT,
    quality_grade    CHAR(1),
    model_confidence NUMERIC(5,4),
    doctor_id        BIGINT,
    sign_time        TIMESTAMP,
    ai_generate_time TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_report_case      FOREIGN KEY (case_id)          REFERENCES case_info(case_id),
    CONSTRAINT fk_report_gen_model FOREIGN KEY (gen_model_id)     REFERENCES ai_model_info(model_id),
    CONSTRAINT fk_report_retrieval FOREIGN KEY (retrieval_log_id) REFERENCES retrieval_log(retrieval_id),
    CONSTRAINT fk_report_doctor    FOREIGN KEY (doctor_id)        REFERENCES sys_user(user_id)
);

CREATE INDEX idx_report_case_id   ON report_info(case_id);
CREATE INDEX idx_report_doctor_id ON report_info(doctor_id);
CREATE INDEX idx_report_status    ON report_info(report_status);
CREATE INDEX idx_report_quality   ON report_info(quality_grade);
CREATE INDEX idx_report_sign_time ON report_info(sign_time DESC);
CREATE INDEX idx_report_gen_time  ON report_info(ai_generate_time DESC);

COMMENT ON TABLE  report_info                  IS '报告表';
COMMENT ON COLUMN report_info.report_id        IS '报告ID';
COMMENT ON COLUMN report_info.case_id          IS '所属病例ID';
COMMENT ON COLUMN report_info.report_status    IS '报告状态：NONE/AI_DRAFT/EDITING/SIGNED';
COMMENT ON COLUMN report_info.gen_model_id     IS '生成AI报告使用的模型ID（LLaVA-Med-LoRA）';
COMMENT ON COLUMN report_info.retrieval_log_id IS '关联的RAG检索记录ID，追溯生成时的参考病例';
COMMENT ON COLUMN report_info.ai_findings      IS 'AI生成的影像所见（Findings）';
COMMENT ON COLUMN report_info.ai_impression    IS 'AI生成的影像印象（Impression）';
COMMENT ON COLUMN report_info.ai_prompt        IS 'AI生成时使用的RAG增强Prompt（审计留存）';
COMMENT ON COLUMN report_info.final_findings   IS '医生审核后的最终影像所见';
COMMENT ON COLUMN report_info.final_impression IS '医生审核后的最终影像印象';
COMMENT ON COLUMN report_info.quality_grade    IS '报告质量评级：A=优秀(F1≥0.80)，B=良好，C=一般，D=待改进';
COMMENT ON COLUMN report_info.model_confidence IS 'AI生成置信度（基于输出概率分布，0-1）';
COMMENT ON COLUMN report_info.doctor_id        IS '签发医生ID';
COMMENT ON COLUMN report_info.sign_time        IS '签发时间';
COMMENT ON COLUMN report_info.ai_generate_time IS 'AI报告生成时间';
COMMENT ON COLUMN report_info.created_at       IS '创建时间';
COMMENT ON COLUMN report_info.updated_at       IS '更新时间';

-- 报告修改历史表（审核留痕，满足《放射科质控指标》合规要求）
CREATE TABLE report_edit_history (
    history_id        BIGINT       PRIMARY KEY,
    report_id         BIGINT       NOT NULL,
    editor_id         BIGINT       NOT NULL,
    findings_before   TEXT,
    findings_after    TEXT,
    impression_before TEXT,
    impression_after  TEXT,
    edit_note         VARCHAR(512),
    edit_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_hist_report FOREIGN KEY (report_id) REFERENCES report_info(report_id),
    CONSTRAINT fk_hist_editor FOREIGN KEY (editor_id) REFERENCES sys_user(user_id)
);

CREATE INDEX idx_hist_report_id ON report_edit_history(report_id);
CREATE INDEX idx_hist_editor_id ON report_edit_history(editor_id);
CREATE INDEX idx_hist_edit_time ON report_edit_history(edit_time DESC);

COMMENT ON TABLE  report_edit_history                   IS '报告修改历史表（满足审核留痕合规要求）';
COMMENT ON COLUMN report_edit_history.history_id        IS '历史记录ID';
COMMENT ON COLUMN report_edit_history.report_id         IS '所属报告ID';
COMMENT ON COLUMN report_edit_history.editor_id         IS '编辑人用户ID';
COMMENT ON COLUMN report_edit_history.findings_before   IS '修改前的影像所见';
COMMENT ON COLUMN report_edit_history.findings_after    IS '修改后的影像所见';
COMMENT ON COLUMN report_edit_history.impression_before IS '修改前的影像印象';
COMMENT ON COLUMN report_edit_history.impression_after  IS '修改后的影像印象';
COMMENT ON COLUMN report_edit_history.edit_note         IS '修改说明';
COMMENT ON COLUMN report_edit_history.edit_time         IS '修改时间';

-- ============================================================
--  七、CheXbert评测模块
-- ============================================================

-- 评测结果表
CREATE TABLE eval_result (
    eval_id         BIGINT       PRIMARY KEY,
    report_id       BIGINT       NOT NULL,
    model_id        BIGINT,
    model_version   VARCHAR(64),
    eval_type       VARCHAR(16)  NOT NULL DEFAULT 'AI',
    ai_labels       TEXT,
    ref_labels      TEXT,
    precision_score NUMERIC(5,4),
    recall_score    NUMERIC(5,4),
    f1_score        NUMERIC(5,4),
    per_label_f1    TEXT,
    bleu4_score     NUMERIC(5,4),
    rouge_l_score   NUMERIC(5,4),
    quality_grade   CHAR(1),
    missing_labels  TEXT,
    extra_labels    TEXT,
    eval_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_eval_report FOREIGN KEY (report_id) REFERENCES report_info(report_id),
    CONSTRAINT fk_eval_model  FOREIGN KEY (model_id)  REFERENCES ai_model_info(model_id)
);

CREATE INDEX idx_eval_report_id ON eval_result(report_id);
CREATE INDEX idx_eval_model_id  ON eval_result(model_id);
CREATE INDEX idx_eval_time      ON eval_result(eval_time DESC);
CREATE INDEX idx_eval_f1        ON eval_result(f1_score);
CREATE INDEX idx_eval_grade     ON eval_result(quality_grade);

COMMENT ON TABLE  eval_result                 IS 'CheXbert评测结果表';
COMMENT ON COLUMN eval_result.eval_id         IS '评测ID';
COMMENT ON COLUMN eval_result.report_id       IS '所属报告ID';
COMMENT ON COLUMN eval_result.model_id        IS '评测使用的模型ID（CheXbert）';
COMMENT ON COLUMN eval_result.model_version   IS '模型版本描述';
COMMENT ON COLUMN eval_result.eval_type       IS '评测对象：AI=评测AI草稿，FINAL=评测最终签发报告';
COMMENT ON COLUMN eval_result.ai_labels       IS 'CheXbert提取的AI报告标签（JSON，14类病理二值化结果）';
COMMENT ON COLUMN eval_result.ref_labels      IS '参考标签（MIMIC-CXR真实标签或医生签发报告标签，JSON）';
COMMENT ON COLUMN eval_result.precision_score IS '宏平均精确率（Macro Precision）';
COMMENT ON COLUMN eval_result.recall_score    IS '宏平均召回率（Macro Recall）';
COMMENT ON COLUMN eval_result.f1_score        IS '宏平均F1分数（目标≥0.75，优秀≥0.80）';
COMMENT ON COLUMN eval_result.per_label_f1    IS '各病理类别F1明细（JSON，14类）';
COMMENT ON COLUMN eval_result.bleu4_score     IS 'BLEU-4分数（传统NLP辅助指标）';
COMMENT ON COLUMN eval_result.rouge_l_score   IS 'ROUGE-L分数（传统NLP辅助指标）';
COMMENT ON COLUMN eval_result.quality_grade   IS '综合质量评级：A/B/C/D（与report_info同步）';
COMMENT ON COLUMN eval_result.missing_labels  IS '漏检标签列表（vs参考标签，JSON），用于遗漏征象提示';
COMMENT ON COLUMN eval_result.extra_labels    IS '多检标签列表（vs参考标签，JSON），用于幻觉内容检测';
COMMENT ON COLUMN eval_result.eval_time       IS '评测时间（单次推理目标<2s）';

-- ============================================================
--  八、危急值预警模块
-- ============================================================

-- 危急值预警表
CREATE TABLE critical_alert (
    alert_id        BIGINT       PRIMARY KEY,
    case_id         BIGINT       NOT NULL,
    report_id       BIGINT       NOT NULL,
    label_type      VARCHAR(64)  NOT NULL,
    label_prob      NUMERIC(5,4) NOT NULL,
    alert_status    VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    responder_id    BIGINT,
    response_action VARCHAR(16),
    response_time   TIMESTAMP,
    response_note   VARCHAR(512),
    alert_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_alert_case      FOREIGN KEY (case_id)      REFERENCES case_info(case_id),
    CONSTRAINT fk_alert_report    FOREIGN KEY (report_id)    REFERENCES report_info(report_id),
    CONSTRAINT fk_alert_responder FOREIGN KEY (responder_id) REFERENCES sys_user(user_id)
);

CREATE INDEX idx_alert_case_id    ON critical_alert(case_id);
CREATE INDEX idx_alert_report_id  ON critical_alert(report_id);
CREATE INDEX idx_alert_status     ON critical_alert(alert_status);
CREATE INDEX idx_alert_label_type ON critical_alert(label_type);
CREATE INDEX idx_alert_time       ON critical_alert(alert_time DESC);
CREATE INDEX idx_alert_responder  ON critical_alert(responder_id);

COMMENT ON TABLE  critical_alert                  IS '危急值预警表';
COMMENT ON COLUMN critical_alert.alert_id         IS '预警ID';
COMMENT ON COLUMN critical_alert.case_id          IS '所属病例ID';
COMMENT ON COLUMN critical_alert.report_id        IS '触发预警的报告ID';
COMMENT ON COLUMN critical_alert.label_type       IS '触发预警的CheXbert病理标签（如Pneumothorax、Pleural Effusion）';
COMMENT ON COLUMN critical_alert.label_prob       IS 'CheXbert输出的该标签概率（>0.7触发预警）';
COMMENT ON COLUMN critical_alert.alert_status     IS '预警状态：PENDING=待处理，ACKNOWLEDGED=已确认，ESCALATED=已上转，DISMISSED=已驳回';
COMMENT ON COLUMN critical_alert.responder_id     IS '响应医师ID';
COMMENT ON COLUMN critical_alert.response_action  IS '处理动作：ACKNOWLEDGED/ESCALATED/DISMISSED';
COMMENT ON COLUMN critical_alert.response_time    IS '医师响应时间（用于质控响应时效统计）';
COMMENT ON COLUMN critical_alert.response_note    IS '处理说明';
COMMENT ON COLUMN critical_alert.alert_time       IS '预警触发时间';

-- ============================================================
--  九、术语规范化模块
-- ============================================================

-- 术语校正记录表
CREATE TABLE term_correction (
    correction_id   BIGINT       PRIMARY KEY,
    report_id       BIGINT       NOT NULL,
    original_term   VARCHAR(128) NOT NULL,
    suggested_term  VARCHAR(128) NOT NULL,
    context_sentence TEXT,
    is_accepted     SMALLINT     NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_correction_report FOREIGN KEY (report_id) REFERENCES report_info(report_id)
);

CREATE INDEX idx_correction_report_id ON term_correction(report_id);
CREATE INDEX idx_correction_time      ON term_correction(created_at DESC);

COMMENT ON TABLE  term_correction                   IS '术语规范化校正记录表';
COMMENT ON COLUMN term_correction.correction_id     IS '校正记录ID';
COMMENT ON COLUMN term_correction.report_id         IS '所属报告ID';
COMMENT ON COLUMN term_correction.original_term     IS '原始非标准术语（如"渗出"）';
COMMENT ON COLUMN term_correction.suggested_term    IS '建议的标准放射学术语（如"实变（Consolidation）"）';
COMMENT ON COLUMN term_correction.context_sentence  IS '原始术语所在的上下文句子（便于医师判断）';
COMMENT ON COLUMN term_correction.is_accepted       IS '医师是否采纳建议：0=忽略/未操作，1=已采纳';
COMMENT ON COLUMN term_correction.created_at        IS '检测时间';

-- ============================================================
--  十、病灶标注表（AI自动标注 + 医生手动标注）
-- ============================================================
CREATE TABLE image_annotation (
    annotation_id  BIGINT        PRIMARY KEY,
    image_id       BIGINT        NOT NULL,
    report_id      BIGINT,
    source         VARCHAR(10)   NOT NULL DEFAULT 'DOCTOR',
    anno_type      VARCHAR(20)   NOT NULL DEFAULT 'RECTANGLE',
    label          VARCHAR(100),
    remark         TEXT,
    x              DECIMAL(7,4)  NOT NULL,
    y              DECIMAL(7,4)  NOT NULL,
    width          DECIMAL(7,4)  NOT NULL,
    height         DECIMAL(7,4)  NOT NULL,
    color          VARCHAR(20),
    confidence     DECIMAL(5,3),
    created_by     BIGINT,
    created_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_annotation_image  ON image_annotation(image_id);
CREATE INDEX idx_annotation_report ON image_annotation(report_id);

COMMENT ON TABLE  image_annotation               IS '病灶标注表（AI自动 + 医生手动）';
COMMENT ON COLUMN image_annotation.annotation_id IS '标注ID';
COMMENT ON COLUMN image_annotation.image_id      IS '所属影像ID';
COMMENT ON COLUMN image_annotation.report_id     IS '关联报告ID';
COMMENT ON COLUMN image_annotation.source        IS '标注来源：AI/DOCTOR';
COMMENT ON COLUMN image_annotation.anno_type     IS '标注类型：RECTANGLE/CIRCLE';
COMMENT ON COLUMN image_annotation.label         IS '标注标签';
COMMENT ON COLUMN image_annotation.x             IS '归一化左上角X（0~1）';
COMMENT ON COLUMN image_annotation.y             IS '归一化左上角Y（0~1）';
COMMENT ON COLUMN image_annotation.width         IS '归一化宽度';
COMMENT ON COLUMN image_annotation.height        IS '归一化高度';
COMMENT ON COLUMN image_annotation.color         IS '标注颜色';
COMMENT ON COLUMN image_annotation.confidence    IS 'AI标注置信度';
COMMENT ON COLUMN image_annotation.created_by    IS '创建者用户ID';
COMMENT ON COLUMN image_annotation.created_at    IS '创建时间';

