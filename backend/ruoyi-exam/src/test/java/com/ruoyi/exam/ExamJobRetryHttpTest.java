package com.ruoyi.exam;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.exam.controller.*;
import com.ruoyi.exam.service.*;
import com.ruoyi.exam.support.ExamJson;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExamJobRetryHttpTest {
    @Configuration(proxyBeanMethods=false) @EnableMethodSecurity(proxyTargetClass=true) static class Security { }
    AnnotationConfigApplicationContext context; ExamJobRetries retries; ExamJobRetryController controller; MockMvc mvc;
    @BeforeEach void setup() {
        retries=mock(ExamJobRetries.class); var workflow=mock(ExamWorkflow.class);
        context=new AnnotationConfigApplicationContext(); context.register(Security.class);
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("exam-test",Map.of("exam.enabled","true")));
        context.registerBean("ss",ExamHttpContractTest.TestPermissions.class);
        context.registerBean(ExamJobRetryController.class,()->new ExamJobRetryController(retries,workflow)); context.refresh();
        controller=context.getBean(ExamJobRetryController.class); mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ExamExceptionHandler()).build();
    }
    void login(String... permissions) {
        var user=new LoginUser(); user.setUserId(7L); user.setPermissions(Set.of(permissions));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,"",List.of()));
    }
    @AfterEach void close() { SecurityContextHolder.clearContext(); context.close(); }
    @Test void taskListPermissionAloneCannotPreviewOrSubmitRetry() {
        login("exam:task:list");
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->controller.preview(5));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->controller.retry(5,"request_0123456789",ExamJson.object()));
        verifyNoInteractions(retries);
    }
    @Test void anonymousRetryIsRejected() { assertThrows(RuntimeException.class,()->controller.preview(5)); verifyNoInteractions(retries); }
    @Test void previewReturnsOnlyPlanAndRetryRequiresIdempotencyHeader() throws Exception {
        login("exam:task:retry"); when(retries.preview(any(),eq(5L))).thenReturn(ExamJson.object().put("ready",true).put("expectedRevision",3).put("planFingerprint","synthetic"));
        mvc.perform(get("/exam/jobs/5/retry-preview")).andExpect(status().isOk()).andExpect(jsonPath("$.data.planFingerprint").value("synthetic"));
        mvc.perform(post("/exam/jobs/5/retry").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        verify(retries,never()).retry(any(),anyLong(),anyString(),any());
    }
}
