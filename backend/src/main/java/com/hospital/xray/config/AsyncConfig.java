package com.hospital.xray.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 异步任务配置
 * 启用 Spring 的异步方法执行能力
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    // 使用默认的异步执行器配置
    // 如需自定义线程池，可以在此配置 TaskExecutor Bean
}
