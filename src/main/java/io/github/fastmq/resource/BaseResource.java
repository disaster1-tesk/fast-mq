package io.github.fastmq.resource;

import io.github.fastmq.infrastructure.constant.FastMQConstant;

public class BaseResource {
    protected String appendPrefix(String stream){
        return FastMQConstant.FAST_MQ_STREAM_PREFIX + stream;
    }
}
