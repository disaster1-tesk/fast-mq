package io.github.fastmq.infrastructure.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


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

    public void submitTask(Runnable runnable) {
        getThreadPool().submit(runnable);
    }


    public boolean close() {
        getThreadPool().shutdown();
        boolean isClose;
        while (!getThreadPool().isTerminated()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }
        }
        isClose = true;
        return isClose;
    }


}
