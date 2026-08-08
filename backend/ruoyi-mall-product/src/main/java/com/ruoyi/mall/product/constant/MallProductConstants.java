package com.ruoyi.mall.product.constant;

public final class MallProductConstants
{
    public static final String STATUS_NORMAL = "0";
    public static final String STATUS_DISABLED = "1";

    public static final String SPU_STATUS_DRAFT = "DRAFT";
    public static final String SPU_STATUS_ON = "ON";
    public static final String SPU_STATUS_OFF = "OFF";

    public static final String DEL_FLAG_NORMAL = "0";
    public static final String DEL_FLAG_DELETED = "2";

    public static final String ATTR_TYPE_SALE = "SALE";
    public static final String ATTR_TYPE_DESC = "DESC";

    public static final String INPUT_TYPE_TEXT = "text";
    public static final String INPUT_TYPE_SELECT = "select";
    public static final String INPUT_TYPE_MULTI = "multi";

    /** 单次库存变更上限，避免异常请求触发整数溢出或一次性巨量调整。 */
    public static final int MAX_INVENTORY_CHANGE_QUANTITY = 1_000_000;

    private MallProductConstants()
    {
    }
}
