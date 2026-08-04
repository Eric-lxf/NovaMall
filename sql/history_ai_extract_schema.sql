SET NAMES utf8mb4;
USE nova_mall;

-- 阶段3：历史抽取 Prompt + 模块编码白名单扩展

INSERT INTO `ai_prompt_template` (`template_name`, `scene_type`, `system_prompt`, `model_name`, `temperature`, `is_active`)
SELECT '历史资料抽取', 'HISTORY_EXTRACT',
'你是严谨的中国史资料整理助手。只能依据用户提供的资料片段抽取信息，禁止编造。
输出必须是合法 JSON（不要 Markdown 代码围栏），结构：
{
  "events":[{"tempId":"e1","title":"","startYear":null,"endYear":null,"originalDateText":"","calendarType":"中国传统纪年","summary":"","background":"","process":"","causeAnalysis":"","impact":"","uncertaintyNote":"","placeName":"","fragmentSeqNos":[1]}],
  "persons":[{"tempId":"p1","name":"","alias":"","birthYear":null,"deathYear":null,"summary":"","uncertaintyNote":"","fragmentSeqNos":[1]}],
  "places":[{"tempId":"l1","name":"","alias":"","modernName":"","region":"","summary":"","fragmentSeqNos":[1]}],
  "relations":[{"fromTempId":"e1","toTempId":"e2","relationType":"LEADS_TO","description":"","fragmentSeqNos":[1]}]
}
规则：
1. fragmentSeqNos 必须引用用户给出的片段序号；每条重要结论至少绑定一个片段。
2. 不确定处写入 uncertaintyNote，不要伪装成确定事实。
3. relationType 仅用：CAUSES/LEADS_TO/PART_OF/HAPPENS_BEFORE/INFLUENCES/CONFLICTS_WITH/SIMILAR_TO。
4. 年份用整数，公元前为负数；缺省填 null。
5. 若资料不足，对应数组可为空。',
'deepseek-chat', 0.20, 1
WHERE NOT EXISTS (SELECT 1 FROM `ai_prompt_template` WHERE `scene_type` = 'HISTORY_EXTRACT' LIMIT 1);

-- 扩展 ai_module_config 模块编码约束（已有库升级）
SET @chk_exists := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ai_module_config'
    AND CONSTRAINT_NAME = 'chk_ai_module_config_module_code'
    AND CONSTRAINT_TYPE = 'CHECK'
);
SET @sql := IF(@chk_exists > 0,
  'ALTER TABLE ai_module_config DROP CHECK chk_ai_module_config_module_code',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE ai_module_config
  ADD CONSTRAINT chk_ai_module_config_module_code
  CHECK (`module_code` IN ('editor', 'write', 'optimize', 'comment_moderate', 'bill_vision', 'bill_advice', 'history_extract'));
