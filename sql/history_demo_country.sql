SET NAMES utf8mb4;
USE nova_mall;

-- 国家/朝代样例：中国（挂既有时期）、埃及、美国

INSERT INTO history_country (id, name, alias, region, period_label, summary, sort, status, create_by)
VALUES
(1, '中国', '中华', '东亚', '朝代', '东亚连续文明与王朝更迭的主线。', 10, '0', 'admin'),
(2, '埃及', 'Kemet', '北非', '王朝', '尼罗河流域古代文明，以王朝序列组织。', 20, '0', 'admin'),
(3, '美国', 'USA', '北美', '时期', '近代民族国家，按建国与重大转折分期。', 30, '0', 'admin')
ON DUPLICATE KEY UPDATE name = VALUES(name), period_label = VALUES(period_label);

-- 将既有秦汉/隋唐时期归入中国
UPDATE history_period SET country_id = 1 WHERE id IN (1, 2) AND (country_id IS NULL OR country_id = 0);

INSERT INTO history_period (id, name, alias, country_id, start_year, end_year, date_precision, original_date_text, calendar_type, is_approximate, summary, sort, status, create_by)
VALUES
(3, '新王国', '第十八至二十王朝', 2, -1550, -1077, 'PERIOD', '约前1550–前1077', '埃及王朝纪年', 1, '埃及帝国扩张与阿蒙神庙鼎盛时期', 20, '0', 'admin'),
(4, '内战与重建', 'Civil War & Reconstruction', 3, 1861, 1877, 'PERIOD', '1861–1877', '公历', 0, '南北战争与重建时期，联邦与奴隶制问题的转折', 40, '0', 'admin')
ON DUPLICATE KEY UPDATE name = VALUES(name), country_id = VALUES(country_id);

INSERT INTO history_event (id, title, period_id, place_id, start_year, end_year, date_precision, original_date_text, calendar_type, is_approximate, summary, background, process, cause_analysis, impact, audit_status, status, create_by)
VALUES
(3, '图特摩斯三世出征叙利亚', 3, NULL, -1457, -1457, 'YEAR', '约前1457', '埃及王朝纪年', 1,
 '新王国法老发动近东远征，巩固帝国边界',
 '希克索斯人被驱逐后埃及进入扩张阶段。',
 '图特摩斯三世率军北上，与米坦尼等势力交锋。',
 '控制商路与缓冲地带、巩固阿蒙神庙经济基础。',
 '埃及成为近东重要大国，王朝叙事进入帝国阶段。',
 'PUBLISHED', '0', 'admin'),
(4, '葛底斯堡战役', 4, NULL, 1863, 1863, 'YEAR', '1863年7月', '公历', 0,
 '南北战争关键转折战役',
 '联邦与邦联在宾夕法尼亚激烈交锋。',
 '三日激战后邦联撤退，林肯随后发表葛底斯堡演说。',
 '战略态势与舆论动员共同作用。',
 '联邦士气提升，战争转入消耗与重建前奏。',
 'PUBLISHED', '0', 'admin')
ON DUPLICATE KEY UPDATE title = VALUES(title), period_id = VALUES(period_id), audit_status = VALUES(audit_status);
