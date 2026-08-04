package com.ruoyi.history.constant;

/**
 * 历史学习平台常量。
 */
public final class HistoryConstants
{
    private HistoryConstants()
    {
    }

    public static final String STATUS_NORMAL = "0";
    public static final String STATUS_DISABLE = "1";

    public static final String AUDIT_DRAFT = "DRAFT";
    public static final String AUDIT_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String AUDIT_PUBLISHED = "PUBLISHED";
    public static final String AUDIT_REJECTED = "REJECTED";

    public static final String DATE_PRECISION_YEAR = "YEAR";
    public static final String DATE_PRECISION_CENTURY = "CENTURY";
    public static final String DATE_PRECISION_APPROXIMATE = "APPROXIMATE";
    public static final String DATE_PRECISION_PERIOD = "PERIOD";

    public static final String TASK_QUEUED = "QUEUED";
    public static final String TASK_PROCESSING = "PROCESSING";
    public static final String TASK_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String TASK_SUCCEEDED = "SUCCEEDED";
    public static final String TASK_FAILED = "FAILED";
    public static final String TASK_RETRYING = "RETRYING";

    public static final String TASK_TYPE_IMPORT_PARSE = "IMPORT_PARSE";
    public static final String TASK_TYPE_EXTRACT = "EXTRACT";
    public static final String FILE_TYPE_TEXT = "TEXT";
    public static final String FILE_TYPE_MARKDOWN = "MARKDOWN";
    public static final String FILE_TYPE_PDF = "PDF";

    public static final String CLAIM_TYPE_EVENT = "EVENT";
    public static final String CLAIM_TYPE_PERSON = "PERSON";
    public static final String CLAIM_TYPE_PLACE = "PLACE";
    public static final String CLAIM_TYPE_RELATION = "RELATION";
    public static final String CLAIM_TYPE_FACT = "FACT";

    public static final String TARGET_EVENT = "EVENT";
    public static final String TARGET_PERSON = "PERSON";
    public static final String TARGET_PLACE = "PLACE";
    public static final String TARGET_RELATION = "RELATION";

    public static final String SCENE_HISTORY_EXTRACT = "HISTORY_EXTRACT";

    /** 送入抽取模型的片段总字符上限 */
    public static final int EXTRACT_PROMPT_MAX_CHARS = 18000;
    /** 单次抽取最多使用的片段数 */
    public static final int EXTRACT_MAX_FRAGMENTS = 40;

    /** 文本切片目标长度（字符） */
    public static final int FRAGMENT_TARGET_CHARS = 1000;
    /** 单片段硬上限 */
    public static final int FRAGMENT_MAX_CHARS = 1500;
    /** 单文档最大片段数，防止异常大文件撑爆 */
    public static final int FRAGMENT_MAX_COUNT = 2000;
    /** 任务最大重试次数 */
    public static final int TASK_MAX_RETRY = 3;
}
