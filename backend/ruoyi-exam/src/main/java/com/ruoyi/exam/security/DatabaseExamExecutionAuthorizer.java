package com.ruoyi.exam.security;

import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.service.ISysMenuService;
import com.ruoyi.system.service.ISysUserService;

public class DatabaseExamExecutionAuthorizer implements ExamExecutionAuthorizer {
    private final ISysUserService users;
    private final ISysMenuService menus;

    public DatabaseExamExecutionAuthorizer(ISysUserService users, ISysMenuService menus) {
        this.users = users;
        this.menus = menus;
    }

    @Override public boolean mayExecute(long userId) {
        return mayExecute(userId,"exam:task:create");
    }
    @Override public boolean mayExecute(long userId,String permission) {
        var user = users.selectUserById(userId);
        if (user == null || !"0".equals(user.getStatus()) || !"0".equals(user.getDelFlag())) return false;
        return SecurityUtils.isAdmin(userId)
                || SecurityUtils.hasPermi(menus.selectMenuPermsByUserId(userId), permission);
    }
}
