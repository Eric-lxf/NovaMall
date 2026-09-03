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
import com.ruoyi.exam.service.ExamWorkflow;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real method-security proxies with H2 services. These tests are not a substitute for production JWT + MySQL smoke tests. */
class ExamWorkflowHttpTest extends ExamWorkflowIntegrationTest {
    private AnnotationConfigApplicationContext context; private ExamWorkflowController controller; private MockMvc mvc;
    @Configuration(proxyBeanMethods=false) @EnableMethodSecurity(proxyTargetClass=true) static class Security { }
    @BeforeEach void http() {
        context=new AnnotationConfigApplicationContext(); context.register(Security.class);
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("exam-test",Map.of("exam.enabled","true")));
        context.registerBean("ss",ExamHttpContractTest.TestPermissions.class);
        context.registerBean(ExamWorkflowController.class,()->new ExamWorkflowController(new ExamWorkflow(db,null),sources,knowledge,blueprints,questions,papers));
        context.refresh(); controller=context.getBean(ExamWorkflowController.class);
        mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ExamExceptionHandler()).build();
    }
    @AfterEach void closeHttp() { SecurityContextHolder.clearContext(); if(context!=null) context.close(); }
    private void login(long userId,String... permissions) {
        var user=new LoginUser(); user.setUserId(userId); user.setPermissions(Set.of(permissions));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,"",List.of()));
    }
    @Test void studentPreviewCannotReachTeacherAnswers() throws Exception {
        var p=paper(approve(create(0))); long version=p.path("currentVersionId").asLong(); login(7,"exam:paper:list");
        mvc.perform(get("/exam/paper-versions/"+version+"/student")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].content.stem").exists())
                .andExpect(jsonPath("$.data.items[0].content.correctOptionIds").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].content.analysis").doesNotExist());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->controller.teacher(version));
        login(7,"exam:paper:answers"); assertNotNull(controller.teacher(version).get("data"));
    }
    @Test void sourceListingPermissionIsNotOriginalDownloadPermission() {
        login(7,"exam:source:list"); assertNotNull(controller.source(id(source)));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->controller.sourceDownload(Long.parseLong(sourceVersionId)));
    }
    @Test void reviewListPermissionCannotApproveOrEditAnotherOwnersData() {
        var q=create(0); var submitted=questions.submit(owner,q.path("currentVersionId").asLong(),reviewRequest(q));
        login(9,"exam:review:list"); assertNotNull(controller.reviews(null));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->controller.review(submitted.path("currentVersionId").asLong(),
                reviewRequest(submitted).put("decision","APPROVED").put("reason","check").put("manualVerification",true)));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->controller.source(id(source)));
    }
    @Test void downloadedOriginalHasPrivateCacheAndAttachmentHeaders() throws Exception {
        login(7,"exam:source:download");
        mvc.perform(get("/exam/source-versions/"+sourceVersionId+"/download")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().string("X-Content-Type-Options","nosniff"))
                .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment;")));
        login(8,"exam:source:download"); mvc.perform(get("/exam/source-versions/"+sourceVersionId+"/download"))
                .andExpect(jsonPath("$.errorCode").value("EXAM_RESOURCE_NOT_FOUND"));
    }
}
