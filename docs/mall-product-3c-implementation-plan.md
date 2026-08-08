# NovaMall Product 模块 3C 商品化执行计划

## 1. 目标与范围

当前 `ruoyi-mall-product` 已具备 SPU、SKU、类目、品牌、属性、图片和基础库存能力，但仍偏向“通用商品 + 简单库存”。本计划将其逐步增强为支持手机、平板、笔记本电脑等 3C 商品的商品中心。

本轮暂不包含：

- 自动化测试
- 前端/后端 Lint
- CI 质量门禁
- 复杂营销活动
- 完整审核工作流

本轮重点：

- SKU 规格唯一性
- 商品编辑与库存解耦
- 可售/锁定/实际库存
- 3C 商品主数据
- 属性类型、单位和筛选能力
- SKU 图片
- 品牌与类目治理
- 商品详情 HTML 安全
- 基础价格体系

## 2. 实施原则

1. 先处理会造成库存错误和订单错误的数据问题，再扩展页面能力。
2. 新字段和新表优先兼容旧数据，避免一次性破坏现有商品。
3. 商品资料、价格、库存分别建模，不继续把不同业务混在 SPU/SKU 主表中。
4. 重要业务字段使用 ID 关联，不依赖属性名称或自由文本。
5. 数据库迁移脚本按版本追加，不修改已执行的历史脚本。

## 3. 阶段一：SKU 规格模型改造

预计：1 周。

### 3.1 数据库改造

在 `mall_sku` 增加规范化规格键：

```sql
spec_key varchar(512) not null comment '规范化规格组合'
```

规格键统一使用属性和选项 ID，例如：

```text
attr:10=option:101|attr:11=option:203|attr:12=option:305
```

要求：

- 按 `attr_id` 升序排列。
- 使用 `option_id`，不使用属性名称。
- 同一个 SPU 内规格键唯一。

增加唯一索引：

```sql
unique key uk_mall_sku_spu_spec_key (spu_id, spec_key)
```

新增结构化规格表：

```sql
mall_sku_spec
-------------
id
sku_id
attr_id
option_id
value
create_time
```

约束：

```sql
unique(sku_id, attr_id)
index(attr_id, option_id)
```

### 3.2 后端改造

重点修改：

- `MallSku.java`
- `MallSkuSaveRequest.java`
- `MallSpuServiceImpl.saveSkus()`
- `MallSpuServiceImpl.validateSaleSpecsJson()`
- `MallSkuMapper.java`

保存流程：

1. 读取当前类目的销售属性模板。
2. 将请求中的规格转换为 `attr_id -> option_id/value`。
3. 校验销售属性是否完整。
4. 校验是否存在未定义属性。
5. 校验枚举值是否合法。
6. 生成 canonical `spec_key`。
7. 检查同一 SPU 内是否重复。
8. 保存 `mall_sku` 和 `mall_sku_spec`。

### 3.3 前端改造

修改：

```text
frontend/src/views/mall/admin/spu/index.vue
```

要求：

- SKU 规格通过属性选择器生成。
- 不允许管理员手工输入不受控的规格 JSON。
- 规格变化后自动生成 SKU 组合。
- 保存前提示重复规格。

### 3.4 阶段验收

- 同一个 SPU 下不能存在重复规格 SKU。
- JSON 属性顺序不同不能产生两个 SKU。
- 属性名称修改后，历史 SKU 仍能正确解析。
- 手机的颜色、内存、存储可作为标准 SKU 维度。

## 4. 阶段二：商品编辑与库存解耦

预计：1 周。

### 4.1 规则调整

商品保存接口不再修改库存字段。后台商品编辑页中的库存改为只读，库存单独调整。

新增接口：

```text
POST /mall/inventory/adjust
```

请求示例：

```json
{
  "skuId": 1001,
  "quantity": 20,
  "reason": "采购入库"
}
```

### 4.2 后端改造

新增：

- `MallInventoryController`
- `MallInventoryService`
- 库存调整 DTO
- 库存调整 VO

修改：

- `MallSkuStockService.java`
- `MallSkuStockServiceImpl.java`
- `MallSkuMapper.java`
- `MallSpuServiceImpl.saveSkus()`

库存调整原因：

```text
PURCHASE_IN
MANUAL_IN
MANUAL_OUT
DAMAGE
RETURN
CORRECTION
```

### 4.3 业务规则

- 商品编辑不能改变实际库存。
- 已被订单引用的 SKU 不允许删除。
- 已上架商品不能直接删除，只能下架。
- 库存调整必须有原因和操作人。

## 5. 阶段三：库存字段和库存流水

预计：1～1.5 周。

### 5.1 SKU 库存字段

在 `mall_sku` 增加：

```sql
stock_total int not null default 0 comment '实际库存'
stock_locked int not null default 0 comment '锁定库存'
stock_available int not null default 0 comment '可售库存'
stock_warning int not null default 0 comment '库存预警值'
```

历史数据迁移：

```text
stock_total = 原 stock
stock_locked = 0
stock_available = 原 stock
```

短期保留旧 `stock` 字段兼容旧代码，稳定后再移除。

### 5.2 库存流水表

新增：

```sql
mall_inventory_log
------------------
id
sku_id
change_type
quantity
before_total
after_total
before_locked
after_locked
before_available
after_available
biz_type
biz_id
operator
remark
create_time
```

变更类型：

```text
PURCHASE_IN
MANUAL_ADJUST
ORDER_LOCK
ORDER_UNLOCK
ORDER_DEDUCT
ORDER_CANCEL
REFUND_RETURN
DAMAGE
```

### 5.3 库存流转

下单：

```text
stock_available -= quantity
stock_locked += quantity
```

支付成功：

```text
stock_locked -= quantity
stock_total -= quantity
```

订单取消：

```text
stock_locked -= quantity
stock_available += quantity
```

退货入库：

```text
stock_total += quantity
stock_available += quantity
```

### 5.4 订单模块改造

重点修改：

- `MallOrderServiceImpl`
- 订单创建
- 支付成功处理
- 订单取消
- 超时关闭
- 退款/退货处理

库存回补接口必须绑定业务单号，避免同一个取消订单重复恢复库存。

## 6. 阶段四：补齐 3C 商品主数据

预计：1 周。

在 `mall_spu` 增加：

```sql
model_no varchar(128) comment '商品型号'
manufacturer_model varchar(128) comment '厂商型号'
barcode varchar(64) comment '商品条码'
condition_type varchar(16) default 'NEW' comment 'NEW/USED/REFURBISHED'
warranty_months int default 0 comment '保修月数'
warranty_type varchar(32) comment '官方/店保/无保'
origin_country varchar(64)
weight decimal(10,3)
length decimal(10,2)
width decimal(10,2)
height decimal(10,2)
shipping_template_id bigint
```

后台商品页分为：

### 基本信息

- 商品名称
- 品牌
- 后台类目
- 型号
- 厂商型号
- 条码

### 售后信息

- 成色
- 保修类型
- 保修期限

### 物流信息

- 重量
- 长度、宽度、高度
- 运费模板

## 7. 阶段五：完善 3C 属性体系

预计：1 周。

在 `mall_attr` 增加：

```sql
data_type varchar(16)
unit varchar(32)
min_value decimal(12,4)
max_value decimal(12,4)
searchable char(1)
filterable char(1)
comparable char(1)
allow_custom char(1)
```

属性类型：

```text
TEXT
INTEGER
DECIMAL
BOOLEAN
ENUM
MULTI_ENUM
```

### 手机属性

描述属性：

```text
操作系统、屏幕尺寸、屏幕刷新率、处理器、电池容量、后置摄像头、前置摄像头、网络制式、NFC、无线充电
```

销售属性：

```text
颜色、运行内存、存储容量、网络版本
```

### 平板属性

描述属性：

```text
操作系统、屏幕尺寸、处理器、电池容量、手写笔支持、键盘支持
```

销售属性：

```text
颜色、存储容量、网络版本
```

### 电脑属性

描述属性：

```text
处理器型号、核心数、内存类型、硬盘类型、显卡型号、显存、屏幕尺寸、分辨率、刷新率、操作系统
```

销售属性：

```text
内存容量、硬盘容量、显卡、颜色
```

## 8. 阶段六：SKU 图片能力

预计：3～4 天。

在 `mall_spu_image` 增加：

```sql
image_type varchar(16) default 'GALLERY'
sku_id bigint null
alt_text varchar(255)
```

图片类型：

```text
MAIN
GALLERY
DETAIL
PARAMETER
PACKAGE
CERTIFICATION
```

后台增加：

- 主图
- 轮播图
- 详情图
- 参数图
- 包装图
- SKU 图片

前台切换颜色或其他 SKU 属性时：

1. 找到当前 SKU。
2. 优先加载 SKU 图片。
3. 没有 SKU 图片时回退到 SPU 轮播图。

## 9. 阶段七：品牌-类目治理

预计：3～4 天。

新增：

```sql
mall_category_brand
-------------------
id
category_id
brand_id
status
sort
create_time
```

商品保存时校验：

- 类目必须存在且启用。
- 品牌必须存在且启用。
- 品牌必须允许在当前类目使用。
- 类目必须是叶子类目。
- 停用属性不能用于新商品。

## 10. 阶段八：商品详情 HTML 安全处理

预计：2～3 天。

保存 `detailHtml` 前使用白名单过滤。

允许标签：

```text
p、br、img、strong、em、ul、ol、li、table、tr、td、h1-h6
```

禁止：

```text
script、iframe、form、object、embed、onclick、onerror、javascript:
```

修改：

```text
MallSpuServiceImpl.save()
```

公开详情输出时再做一次防御性过滤。前端编辑页提示只支持商品图文内容，不支持脚本、表单和外部网页嵌入。

## 11. 阶段九：基础价格体系

预计：4～5 天。

第一版在 `mall_sku` 增加：

```sql
sale_price decimal(12,2)
market_price decimal(12,2)
cost_price decimal(12,2)
member_price decimal(12,2)
```

历史迁移：

```text
旧 price -> sale_price
```

上架规则：

```text
sale_price > 0
market_price >= sale_price
member_price <= sale_price
```

订单创建时必须从后端重新读取 SKU 价格，不能信任前台价格。订单明细保存成交价格快照。

本阶段暂不实现优惠券、促销活动和复杂营销价格规则。

## 12. 阶段十：商品状态和运营管理

预计：3～5 天。

统一 SPU 状态：

```text
DRAFT
ON_SALE
OFF_SALE
SOLD_OUT
ARCHIVED
```

统一 SKU 状态：

```text
ENABLED
DISABLED
SOLD_OUT
DELETED
```

规则：

- 草稿可以没有完整 SKU。
- 上架至少需要一个启用 SKU。
- 所有启用 SKU 售罄时，SPU 显示售罄。
- 已有订单的 SKU 不允许删除。
- 下架商品不能被公开接口返回。
- 停用 SKU 不参与前台价格和库存计算。

商品操作记录可以先复用现有操作日志，不单独建设完整审核系统。

## 13. 数据库迁移组织方式

建议不要继续增加无版本的零散修复 SQL，改为按版本追加：

```text
V2.0.0__product_baseline.sql
V2.1.0__sku_spec_key.sql
V2.2.0__sku_spec_normalized.sql
V2.3.0__inventory_fields.sql
V2.4.0__inventory_log.sql
V2.4.2__payment_active_order_guard.sql
V2.5.0__product_3c_attr_seed.sql
V2.6.0__inventory_log_idempotency.sql
V2.7.0__sku_images.sql
V2.8.0__category_brand.sql
V2.9.0__price_history.sql
```

如果暂时不引入 Flyway，也要保证：

- 每个脚本有唯一版本号。
- 已执行脚本不修改。
- 新环境和旧环境使用相同顺序。
- 每个迁移脚本说明依赖和回滚/修复方式。

## 14. 推荐实施顺序

```text
1. SKU 规格规范化
2. 商品编辑与库存解耦
3. 库存字段和库存流水
4. 修改订单库存流程
5. 补齐 3C 商品字段
6. 完善属性类型和单位
7. SKU 图片
8. 品牌-类目关系
9. HTML 清洗
10. 基础价格体系
11. 商品状态优化
12. 前台属性筛选
```

## 15. 本轮最终交付标准

- 同一 SPU 不存在重复规格 SKU。
- SKU 规格不依赖属性名称。
- 商品编辑不会修改实际库存。
- 库存区分实际、锁定和可售数量。
- 库存变更有业务来源和操作原因。
- 手机、平板、电脑都有对应属性模板。
- SKU 支持颜色、内存、存储等销售属性。
- SKU 支持独立图片。
- 商品支持型号、条码、保修和成色。
- 品牌和类目关系可配置。
- 商品详情 HTML 不允许脚本执行。
- 基础价格字段和订单价格快照分离。
- 上下架、售罄和停用状态语义统一。

## 16. 最高优先级的前三项

如果需要进一步压缩范围，优先完成：

1. SKU 规格规范化和唯一约束。
2. 商品编辑与库存修改彻底分离。
3. 库存升级为可售、锁定、实际库存，并改造订单流程。

