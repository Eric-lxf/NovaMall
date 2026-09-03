package com.ruoyi.exam.config;

import java.time.Clock;
import java.time.ZoneId;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ruoyi.exam.repository.ExamTaskRepository;
import com.ruoyi.exam.security.DatabaseExamExecutionAuthorizer;
import com.ruoyi.exam.security.ExamExecutionAuthorizer;
import com.ruoyi.exam.service.ExamReadiness;
import com.ruoyi.exam.service.ExamTaskService;
import com.ruoyi.exam.task.ExamTaskWorker;
import com.ruoyi.system.service.ISysMenuService;
import com.ruoyi.system.service.ISysUserService;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ExamProperties.class)
@Import(ExamConfiguration.Active.class)
public class ExamConfiguration {
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "exam", name = "enabled", havingValue = "true")
    public static class Active {
        @Bean public Clock examClock() { return Clock.system(ZoneId.of("Asia/Shanghai")); }

        @Bean public ExamTaskRepository examTaskRepository(DataSource dataSource) {
            var jdbc = new JdbcTemplate(dataSource);
            jdbc.setQueryTimeout(5);
            return new ExamTaskRepository(jdbc);
        }

        @Bean public ExamReadiness examReadiness(ExamTaskRepository repository) { return new ExamReadiness(repository); }

        @Bean public ExamTaskService examTaskService(ExamTaskRepository repository, @Qualifier("examClock") Clock clock) {
            return new ExamTaskService(repository, clock);
        }

        @Bean public ExamExecutionAuthorizer examExecutionAuthorizer(ISysUserService users, ISysMenuService menus) {
            return new DatabaseExamExecutionAuthorizer(users, menus);
        }

        @Bean(initMethod = "start", destroyMethod = "close")
        @ConditionalOnProperty(prefix = "exam", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
        public ExamTaskWorker examTaskWorker(ExamTaskRepository repository, ExamExecutionAuthorizer authorizer,
                ExamReadiness readiness, @Qualifier("examClock") Clock clock,
                org.springframework.beans.factory.ObjectProvider<com.ruoyi.exam.task.ExamJobHandler> handlers) {
            return new ExamTaskWorker(repository, authorizer, readiness, clock,handlers.orderedStream().toList());
        }
    }
}
