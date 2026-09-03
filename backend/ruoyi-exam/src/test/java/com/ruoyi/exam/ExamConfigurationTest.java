package com.ruoyi.exam;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.exam.config.ExamConfiguration;
import com.ruoyi.exam.config.ExamWorkflowConfiguration;
import com.ruoyi.exam.repository.ExamTaskRepository;
import com.ruoyi.exam.security.DatabaseExamExecutionAuthorizer;
import com.ruoyi.exam.task.ExamTaskWorker;
import com.ruoyi.system.service.ISysMenuService;
import com.ruoyi.system.service.ISysUserService;
import java.util.Set;

class ExamConfigurationTest {
    @Test void defaultDisabledStartsWithoutDatabaseServicesOrWorker() {
        new ApplicationContextRunner().withUserConfiguration(ExamConfiguration.class, ExamWorkflowConfiguration.class).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(0, context.getBeansOfType(ExamTaskRepository.class).size());
            assertEquals(0, context.getBeansOfType(ExamTaskWorker.class).size());
        });
    }

    @Test void enabledWithoutWorkerCanStartAgainstUnmigratedDatabase() throws Exception {
        var db = new ExamTestDatabase(); db.jdbc.execute("drop table exam_task");
        new ApplicationContextRunner().withUserConfiguration(ExamConfiguration.class, ExamWorkflowConfiguration.class)
                .withPropertyValues("exam.enabled=true", "exam.worker-enabled=false")
                .withBean(javax.sql.DataSource.class, () -> db.dataSource)
                .withBean(ISysUserService.class, () -> mock(ISysUserService.class))
                .withBean(com.ruoyi.blog.service.AiProviderService.class, () -> mock(com.ruoyi.blog.service.AiProviderService.class))
                .withBean(ISysMenuService.class, () -> mock(ISysMenuService.class)).run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(1, context.getBeansOfType(ExamTaskRepository.class).size());
                    assertEquals(0, context.getBeansOfType(ExamTaskWorker.class).size());
                    assertEquals(2, context.getBeansOfType(com.ruoyi.exam.task.ExamJobHandler.class).size());
                });
    }

    @Test void executionAuthorizationChecksLiveUserAndMenuPermission() {
        var users = mock(ISysUserService.class); var menus = mock(ISysMenuService.class);
        var authorizer = new DatabaseExamExecutionAuthorizer(users, menus);
        assertFalse(authorizer.mayExecute(20));
        var user = new SysUser(); user.setStatus("0"); user.setDelFlag("0");
        when(users.selectUserById(20L)).thenReturn(user);
        when(menus.selectMenuPermsByUserId(20L)).thenReturn(Set.of("exam:task:list"));
        assertFalse(authorizer.mayExecute(20));
        when(menus.selectMenuPermsByUserId(20L)).thenReturn(Set.of("exam:task:create"));
        assertTrue(authorizer.mayExecute(20));
        user.setStatus("1"); assertFalse(authorizer.mayExecute(20));
        user.setStatus("0"); user.setDelFlag("2"); assertFalse(authorizer.mayExecute(20));
    }
}
