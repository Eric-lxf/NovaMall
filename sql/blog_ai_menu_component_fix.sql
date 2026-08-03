SET NAMES utf8mb4;
USE nova_mall;

-- 已部署环境：校正 AI 智写 / 文章优化 / 模型配置 菜单组件路径，避免管理端白屏
UPDATE sys_menu SET path = 'ai/write', component = 'blog/ai/write', route_name = 'BlogAiWrite'
WHERE menu_id = 2003;

UPDATE sys_menu SET path = 'ai/optimize', component = 'blog/ai/optimize', route_name = 'BlogAiOptimize'
WHERE menu_id = 2004;

UPDATE sys_menu SET path = 'ai/provider', component = 'blog/ai/provider/index', route_name = 'BlogAiProvider'
WHERE menu_id = 2430;
