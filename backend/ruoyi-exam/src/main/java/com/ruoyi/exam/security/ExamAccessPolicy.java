package com.ruoyi.exam.security;

import com.ruoyi.exam.support.ExamException;

public final class ExamAccessPolicy {
    private ExamAccessPolicy() { }

    public static void requireOwner(ExamActor actor, long ownerUserId) {
        if (actor == null || (actor.userId() != ownerUserId && !actor.administrator())) {
            throw ExamException.notFound();
        }
    }
}
