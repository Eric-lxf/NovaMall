package com.ruoyi.history.config;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class HistoryExecutorConfig
{
    private final AtomicInteger threadSeq = new AtomicInteger(1);

    @Bean(name = "historyTaskExecutor")
    public Executor historyTaskExecutor()
    {
        int cores = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        return new ThreadPoolExecutor(
                cores,
                Math.max(cores, 4),
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("history-task-" + threadSeq.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
