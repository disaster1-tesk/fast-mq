package io.github.fastmq.application;

import io.github.fastmq.infrastructure.constant.FastMQConstant;
import io.github.fastmq.infrastructure.http.HttpResult;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class StreamApplicationService {
    @Autowired
    private RedissonClient client;

    public HttpResult queryDeadStreamInfo(){
        RStream<Object, Object> stream = client.getStream(FastMQConstant.DEFAULT_DEAD_STREAM);
        StreamInfo<Object, Object> info = null;
        try {
            info = stream.getInfo();
        } catch (Exception e) {
            return HttpResult.success(null);
        }
        return HttpResult.success(info);
    }

    public HttpResult queryDelayStreamInfo(){
        RStream<Object, Object> stream = client.getStream(FastMQConstant.DEFAULT_DElAY_QUEUE);
        StreamInfo<Object, Object> info = null;
        try {
            info = stream.getInfo();
        } catch (Exception e) {
            return HttpResult.success(null);
        }
        return HttpResult.success(info);
    }
}
