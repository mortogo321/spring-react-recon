package io.github.mortogo321.recon.batch.config;

import java.util.concurrent.Executors;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

/**
 * Batch infrastructure, configured explicitly rather than left to Boot's auto-configuration.
 *
 * <p>With two data sources in play, "which database holds the batch metadata" must not be an
 * accident. It is pinned to the application's own MySQL {@code dataSource} — never the read-only
 * legacy Oracle pool — and to the JPA {@code transactionManager}, so a chunk's metadata update and
 * its business writes commit or roll back together. That single shared transaction is what makes
 * restart-after-failure land on a consistent restart point instead of a plausible-looking one.
 *
 * <p>Both executors are virtual-thread based: partition workers and job launches are almost
 * entirely blocked on database IO, which is precisely the workload virtual threads are for.
 */
@Configuration
@EnableBatchProcessing(taskExecutorRef = "batchTaskExecutor")
@EnableJdbcJobRepository(dataSourceRef = "dataSource", transactionManagerRef = "transactionManager")
@EnableConfigurationProperties(ReconBatchProperties.class)
public class BatchInfrastructureConfig {

    /** Used by the framework to launch jobs; keeps the HTTP thread free on an async trigger. */
    @Bean
    public AsyncTaskExecutor batchTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }

    /** Runs partition worker steps concurrently within one JVM. */
    @Bean
    public AsyncTaskExecutor partitionTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
