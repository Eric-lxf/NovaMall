package com.ruoyi.exam;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.exam.config.ExamProperties;
import com.ruoyi.exam.controller.ExamExceptionHandler;
import com.ruoyi.exam.controller.ExamTaskController;
import com.ruoyi.exam.repository.ExamTaskRepository;
import com.ruoyi.exam.security.ExamActor;
import com.ruoyi.exam.service.ExamReadiness;
import com.ruoyi.exam.service.ExamTaskService;

/** MVC contract + real method-security proxy; not a production JWT/filter-chain test. */
class ExamHttpContractTest {
    private AnnotationConfigApplicationContext context;
    private MockMvc mvc;
    private ExamTestDatabase db;
    private ExamProperties properties;

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(proxyTargetClass = true)
    static class MethodSecurity { }

    public static class TestPermissions {
        public boolean hasPermi(String permission) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            return auth != null && auth.getPrincipal() instanceof LoginUser
                    && SecurityUtils.hasPermi(permission);
        }
    }

    @BeforeEach void setup() throws Exception {
        db = new ExamTestDatabase();
        properties = new ExamProperties(); properties.setEnabled(true);
        context = new AnnotationConfigApplicationContext();
        context.register(MethodSecurity.class);
        context.registerBean("ss", TestPermissions.class);
        context.registerBean(ExamProperties.class, () -> properties);
        context.registerBean(ExamTaskRepository.class, () -> db.repository);
        context.registerBean(ExamReadiness.class, () -> new ExamReadiness(db.repository));
        context.registerBean(ExamTaskService.class, () -> new ExamTaskService(db.repository, Clock.systemDefaultZone()));
        context.registerBean(ExamTaskController.class);
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(ExamTaskController.class))
                .setControllerAdvice(new ExamExceptionHandler()).build();
        login(20, Set.of("exam:task:list", "exam:task:create", "exam:task:cancel", "exam:task:retry"));
    }

    private void login(long userId, Set<String> permissions) {
        var user = new LoginUser(); user.setUserId(userId); user.setPermissions(permissions);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, "", java.util.List.of()));
    }

    @AfterEach void close() { SecurityContextHolder.clearContext(); context.close(); }

    @Test void missingPermissionPreventsTaskCreation() {
        login(20, Set.of("exam:task:list"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> context.getBean(ExamTaskController.class)
                .create("request_0123456789", new ExamTaskController.CheckRequest("check")));
        assertEquals(0, db.repository.count(20, null));
    }

    @Test void anonymousCannotInvokeProtectedCapabilities() {
        SecurityContextHolder.clearContext();
        assertThrows(RuntimeException.class, () -> context.getBean(ExamTaskController.class).capabilities());
    }

    @Test void createReturnsStringIdAndDoesNotExposeInternalMetadata() throws Exception {
        mvc.perform(post("/exam/tasks/check").header("Idempotency-Key", "request_0123456789")
                .contentType("application/json").content("{\"title\":\"check\",\"ownerUserId\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").isString()).andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.ownerUserId").doesNotExist())
                .andExpect(jsonPath("$.data.idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.data.requestHash").doesNotExist())
                .andExpect(jsonPath("$.data.leaseOwner").doesNotExist());
        assertEquals(1, db.repository.count(20, null));
        assertEquals(0, db.repository.count(999, null));
    }

    @Test void crossUserDetailsReturnStableGenericBusinessError() throws Exception {
        var task = context.getBean(ExamTaskService.class).createCheck(new ExamActor(21, false), "private", "request_0123456789");
        mvc.perform(get("/exam/tasks/" + task.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500)).andExpect(jsonPath("$.errorCode").value("EXAM_RESOURCE_NOT_FOUND"));
    }

    @Test void revisionRequiredAndPaginationBounded() throws Exception {
        mvc.perform(post("/exam/tasks/1/cancel").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/exam/tasks").param("pageSize", "1000"))
                .andExpect(jsonPath("$.errorCode").value("EXAM_INPUT_INVALID"));
        mvc.perform(get("/exam/tasks")).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.rows").isArray()).andExpect(jsonPath("$.total").value(0));
    }

    @Test void disabledEndpointsReturnExplicitStatusWithoutTableQueries() throws Exception {
        properties.setEnabled(false);
        db.jdbc.execute("drop table exam_task");
        mvc.perform(get("/exam/capabilities")).andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.aiEnabled").value(false)).andExpect(jsonPath("$.data.uploadEnabled").value(false));
        mvc.perform(get("/exam/tasks")).andExpect(jsonPath("$.errorCode").value("EXAM_DISABLED"));
    }
}
