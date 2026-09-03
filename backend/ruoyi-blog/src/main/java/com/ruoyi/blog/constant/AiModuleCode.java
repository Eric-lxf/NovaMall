package com.ruoyi.blog.constant;

import java.util.List;

public final class AiModuleCode
{
    public static final String EDITOR = "editor";
    public static final String WRITE = "write";
    public static final String OPTIMIZE = "optimize";
    public static final String COMMENT_MODERATE = "comment_moderate";
    public static final String BILL_VISION = "bill_vision";
    public static final String BILL_ADVICE = "bill_advice";
    public static final String HISTORY_EXTRACT = "history_extract";
    public static final String EXAM_GENERATE = "exam_generate";
    public static final String EXAM_VERIFY = "exam_verify";

    private static final List<String> ALL = List.of(
            EDITOR, WRITE, OPTIMIZE, COMMENT_MODERATE, BILL_VISION, BILL_ADVICE, HISTORY_EXTRACT, EXAM_GENERATE, EXAM_VERIFY);

    private AiModuleCode()
    {
    }

    public static boolean isSupported(String moduleCode)
    {
        return moduleCode != null && ALL.contains(moduleCode);
    }

    public static List<String> all()
    {
        return ALL;
    }
}
