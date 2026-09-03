package com.ruoyi.exam.config;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import com.ruoyi.exam.repository.ExamDataStore;
import com.ruoyi.exam.service.*;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="exam",name="enabled",havingValue="true")
public class ExamWorkflowConfiguration {
    @Bean public ExamDataStore examDataStore(DataSource source,@Qualifier("examClock") Clock clock) { return new ExamDataStore(source,clock); }
    @Bean public ExamFiles examFiles(ExamDataStore db,ExamProperties props,
            @Value("${ruoyi.profile:D:/ruoyi/uploadPath}") String profile,@Value("${blog.file.upload-dir:./uploads}") String uploads) {
        return new ExamFiles(db,props,List.of(Path.of(profile).toAbsolutePath(),Path.of(uploads).toAbsolutePath()));
    }
    @Bean public ExamSources examSources(ExamDataStore db,ExamFiles files) { return new ExamSources(db,files); }
    @Bean public ExamKnowledge examKnowledge(ExamDataStore db,ExamSources sources) { return new ExamKnowledge(db,sources); }
    @Bean public ExamBlueprints examBlueprints(ExamDataStore db,ExamSources sources,ExamKnowledge knowledge) { return new ExamBlueprints(db,sources,knowledge); }
    @Bean public ExamQuestions examQuestions(ExamDataStore db,ExamSources sources,ExamBlueprints blueprints) { return new ExamQuestions(db,sources,blueprints); }
    @Bean public ExamPapers examPapers(ExamDataStore db,ExamQuestions questions) { return new ExamPapers(db,questions); }
    @Bean public ExamWorkflow examWorkflow(ExamDataStore db,ExamFiles files) { return new ExamWorkflow(db,files); }
    @Bean public ExamJobs examJobs(ExamDataStore db,com.ruoyi.exam.repository.ExamTaskRepository tasks) { return new ExamJobs(db,tasks); }
    @Bean public com.ruoyi.exam.document.ExamDocumentRunner examDocumentRunner(ExamProperties props) { return new com.ruoyi.exam.document.DockerExamDocumentRunner(props); }
    @Bean public com.ruoyi.exam.document.ExamDocumentJobs examDocumentJobs(ExamDataStore db,ExamJobs jobs,com.ruoyi.exam.document.ExamDocumentRunner runner,
            com.ruoyi.exam.security.ExamExecutionAuthorizer authorizer,ExamSources sources,ExamFiles files,ExamPapers papers) {
        return new com.ruoyi.exam.document.ExamDocumentJobs(db,jobs,runner,authorizer,sources,files,papers);
    }
    @Bean public com.ruoyi.exam.ai.ExamModelGateway examModelGateway(com.ruoyi.blog.service.AiProviderService providers,ExamProperties properties) {
        return new com.ruoyi.exam.ai.ProviderExamModelGateway(providers,properties);
    }
    @Bean public com.ruoyi.exam.ai.ExamAiJobs examAiJobs(ExamDataStore db,ExamJobs jobs,com.ruoyi.exam.ai.ExamModelGateway gateway,
            com.ruoyi.exam.security.ExamExecutionAuthorizer authorizer,ExamSources sources,ExamKnowledge knowledge,ExamBlueprints blueprints,ExamQuestions questions) {
        return new com.ruoyi.exam.ai.ExamAiJobs(db,jobs,gateway,authorizer,sources,knowledge,blueprints,questions);
    }
    @Bean public ExamJobRetries examJobRetries(ExamDataStore db,com.ruoyi.exam.repository.ExamTaskRepository tasks,ExamJobs jobs,
            com.ruoyi.exam.ai.ExamAiJobs ai,com.ruoyi.exam.document.ExamDocumentJobs documents,com.ruoyi.exam.security.ExamExecutionAuthorizer authorizer) {
        return new ExamJobRetries(db,tasks,jobs,ai,documents,authorizer);
    }
}
