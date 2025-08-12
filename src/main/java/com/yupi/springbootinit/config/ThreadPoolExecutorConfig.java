package com.yupi.springbootinit.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ThreadPoolExecutorConfig {
    @Bean
    public ThreadPoolExecutor threadPoolExecutor() {
        // 创建一个线程工厂
        ThreadFactory threadFactory = new ThreadFactory() {
            // 初始化计数器为 1
            private final AtomicInteger count = new AtomicInteger(1);

            @Override
            // 每当线程池需要创建新线程时，就会调用newThread方法
            // @NotNull Runnable r 表示方法参数 r 应该永远不为null，
            // 如果这个方法被调用的时候传递了一个null参数，就会报错
            public Thread newThread(@NotNull Runnable r) {
                // 创建一个新的线程
                Thread thread = new Thread(r);
                // 给新线程设置一个名称，名称中包含线程数的当前值
                thread.setName("线程" + count.getAndIncrement());
                // 返回新创建的线程
                return thread;
            }
        };
        // 创建一个新的线程池，线程池核心大小为2，最大线程数为4，在任务队列满后才会创建核心线程以外的线程
        // 非核心线程空闲时间为100秒，任务队列为阻塞队列，长度为4，使用自定义的线程工厂创建线程
        //拒绝策略AbortPolicy(抛异常交给异常处理器)、CallerRunsPolicy(任务交给调用者线程执行)
        //      DiscardPolicy(静默丢弃新任务)、DiscardOldestPolicy(丢弃最旧任务，重试当前提交)
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(2, 4, 100, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(4), threadFactory);
        // 返回创建的线程池
        return threadPoolExecutor;
    }
}
