SET NAMES utf8mb4;
USE nova_mall;

-- 首批样例：隋唐 / 安史之乱（便于联调时间线与后台 CRUD）

INSERT INTO history_period (id, name, alias, start_year, end_year, date_precision, original_date_text, calendar_type, is_approximate, summary, sort, status, create_by)
VALUES
(1, '秦汉', '秦→西汉→东汉', -221, 220, 'PERIOD', '秦王政二十六年至东汉献帝', '中国传统纪年', 0, '统一帝国形成与崩溃的长时段', 10, '0', 'admin'),
(2, '隋唐', '隋→唐', 581, 907, 'PERIOD', '开皇元年至天祐四年', '中国传统纪年', 0, '再统一与盛唐、安史之乱后的转型', 40, '0', 'admin')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO history_place (id, name, alias, modern_name, region, summary, audit_status, status, create_by)
VALUES
(1, '长安', '西京', '西安', '关中', '隋唐首都', 'PUBLISHED', '0', 'admin'),
(2, '范阳', '幽州', '北京一带', '河北', '安史之乱起兵地', 'PUBLISHED', '0', 'admin')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO history_person (id, name, alias, period_id, birth_year, death_year, date_precision, summary, audit_status, status, create_by)
VALUES
(1, '安禄山', NULL, 2, 703, 757, 'YEAR', '范阳等三镇节度使，发动安史之乱', 'PUBLISHED', '0', 'admin'),
(2, '唐玄宗', '李隆基', 2, 685, 762, 'YEAR', '唐朝皇帝，开元盛世至安史之乱', 'PUBLISHED', '0', 'admin')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO history_event (id, title, period_id, place_id, start_year, end_year, date_precision, original_date_text, calendar_type, is_approximate, summary, background, process, cause_analysis, impact, audit_status, status, create_by)
VALUES
(1, '安史之乱爆发', 2, 2, 755, 755, 'YEAR', '天宝十四载十一月', '中国传统纪年', 0,
 '安禄山于范阳起兵反唐',
 '玄宗晚年边镇节度使权力膨胀，中央控制力下降。',
 '安禄山以讨杨国忠为名起兵，迅速南下。',
 '藩镇坐大、中央空虚与政治腐败多重叠加。',
 '唐朝由盛转衰，北方残破，藩镇格局长期化。',
 'PUBLISHED', '0', 'admin'),
(2, '长安陷落', 2, 1, 756, 756, 'YEAR', '至德元载', '中国传统纪年', 0,
 '叛军攻入长安，玄宗西逃',
 '潼关失守后长安无险可守。',
 '哥舒翰兵败潼关，玄宗幸蜀，肃宗即位于灵武。',
 '战略失误与边军主力内调不足。',
 '政治中心暂时转移，安史之乱进入长期拉锯。',
 'PUBLISHED', '0', 'admin')
ON DUPLICATE KEY UPDATE title = VALUES(title);
