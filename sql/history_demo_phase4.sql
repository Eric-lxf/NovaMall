-- 阶段 4 样例：从安史之乱事件生成的学习单元与路径（需先执行 history_demo_seed.sql）

INSERT INTO history_learning_unit (id, title, event_id, period_id, one_liner, objectives, prerequisites, content_json, audit_status, status, create_by)
VALUES
(1, '安史之乱爆发', 1, 2, '安禄山于范阳起兵反唐',
 '理解安史之乱爆发的背景、过程与影响',
 '建议先浏览隋唐时间线',
 JSON_OBJECT(
   'timePlace', '天宝十四载十一月 · 范阳',
   'background', '玄宗晚年边镇节度使权力膨胀，中央控制力下降。',
   'keyPeople', '安禄山、唐玄宗',
   'process', '安禄山以讨杨国忠为名起兵，迅速南下。',
   'causeAnalysis', '藩镇坐大、中央空虚与政治腐败多重叠加。',
   'impact', '唐朝由盛转衰，北方残破，藩镇格局长期化。',
   'sourcesAndViews', '请结合资料原文核对结论',
   'practiceHint', '完成本单元后可进入测验巩固关键时间与因果',
   'furtherReading', '可在时间线中查看同期事件与人物'
 ),
 'PUBLISHED', '0', 'admin'),
(2, '长安陷落', 2, 2, '叛军攻入长安，玄宗西逃',
 '理解长安陷落前后的战略转折',
 '建议先完成「安史之乱爆发」单元',
 JSON_OBJECT(
   'timePlace', '至德元载 · 长安',
   'background', '潼关失守后长安无险可守。',
   'keyPeople', '哥舒翰、唐玄宗、肃宗',
   'process', '哥舒翰兵败潼关，玄宗幸蜀，肃宗即位于灵武。',
   'causeAnalysis', '战略失误与边军主力内调不足。',
   'impact', '政治中心暂时转移，安史之乱进入长期拉锯。',
   'sourcesAndViews', '请结合资料原文核对结论',
   'practiceHint', '关注时间顺序：起兵→潼关→长安',
   'furtherReading', '可对照事件详情页'
 ),
 'PUBLISHED', '0', 'admin')
ON DUPLICATE KEY UPDATE title = VALUES(title), audit_status = VALUES(audit_status);

INSERT INTO history_learning_path (id, title, summary, period_id, difficulty, estimated_days, audit_status, status, create_by)
VALUES
(1, '从零理解安史之乱', '用两个核心单元串起安史之乱的爆发与长安陷落', 2, 'BEGINNER', 3, 'PUBLISHED', '0', 'admin')
ON DUPLICATE KEY UPDATE title = VALUES(title), audit_status = VALUES(audit_status);

INSERT INTO history_learning_path_unit (path_id, unit_id, sort)
VALUES (1, 1, 0), (1, 2, 1)
ON DUPLICATE KEY UPDATE sort = VALUES(sort);
