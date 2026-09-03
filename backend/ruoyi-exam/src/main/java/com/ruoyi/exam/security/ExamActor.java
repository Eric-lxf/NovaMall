package com.ruoyi.exam.security;

import com.ruoyi.common.utils.SecurityUtils;

/** Created from the authenticated server session, never request parameters. */
public record ExamActor(long userId, boolean administrator, boolean reviewer) {
    public ExamActor(long userId, boolean administrator) { this(userId, administrator, false); }
    public ExamActor {
        if (userId <= 0) throw new IllegalArgumentException("Authenticated user ID required");
    }

    public static ExamActor current() {
        long userId = SecurityUtils.getUserId();
        return new ExamActor(userId, SecurityUtils.isAdmin(userId) || SecurityUtils.hasPermi("exam:admin:manage"),
                SecurityUtils.hasPermi("exam:review:list") || SecurityUtils.hasPermi("exam:review:approve"));
    }
}
