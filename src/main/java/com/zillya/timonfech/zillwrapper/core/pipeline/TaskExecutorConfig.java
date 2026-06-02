package com.zillya.timonfech.zillwrapper.core.pipeline;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Executor;

@Configuration
public class TaskExecutorConfig {

    @Bean(name = "pipelineTaskExecutor")
    public Executor pipelineTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("Pipeline-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "enrichmentTaskExecutor", destroyMethod = "close")
    public ExecutorService enrichmentTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
