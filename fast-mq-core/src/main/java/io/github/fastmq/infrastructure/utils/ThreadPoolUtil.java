package io.github.fastmq.infrastructure.utils;

import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;


public enum ThreadPoolUtil {
    QUEUE, DELAY;
    private Logger log = LoggerFactory.getLogger(ThreadPoolUtil.class);

    private ExecutorService executor = null;

    public void init(ExecutorService service) {
        executor = service;
    }

    public ExecutorService getThreadPool() {
        return executor;
    }

    public void run(Runnable runnable) {
        executor.execute(runnable);
    }

    public <T> void submit(Callable<T> task){
        executor.submit(task);
    }

    public void submitTask(Runnable runnable) {
        getThreadPool().submit(runnable);
    }

    @SneakyThrows
    public boolean close() {
        Thread.sleep(1000);
        getThreadPool().shutdownNow();
        return getThreadPool().isShutdown();
    }


}
