package io.github.mortogo321.recon.api.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Virtual threads for {@code @Async} work. The workload here is entirely blocking IO against two
 * databases, so a bounded platform-thread pool would be sized around a queue depth we would have
 * to guess; virtual threads remove the guess.
 *
 * <p>The uncaught handler is not optional: without it an exception from a void {@code @Async}
 * method is swallowed and the failure is invisible.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Override
    @Bean("applicationTaskExecutor")
    public Executor getAsyncExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
                log.error("Async method {} failed", method.getName(), throwable);
    }
}
