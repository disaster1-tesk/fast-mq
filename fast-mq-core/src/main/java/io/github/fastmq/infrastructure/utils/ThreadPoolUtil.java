package io.github.fastmq.infrastructure.utils;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ThreadPoolUtil {
    private static ScheduledExecutorService service = null;

    public static void init(ScheduledExecutorService service) {
        ThreadPoolUtil.service = service;
    }

    public static ScheduledExecutorService getService() {
        return service;
    }

    public static void run(Runnable runnable) {
        service.schedule(runnable, 0, TimeUnit.SECONDS);
    }

}
