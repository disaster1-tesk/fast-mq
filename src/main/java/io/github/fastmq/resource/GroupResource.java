package io.github.fastmq.resource;

import io.github.fastmq.infrastructure.http.HttpResult;
import org.assertj.core.util.Lists;
import org.redisson.api.RScript;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamInfo;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

@RequestMapping("/fast/mq")
@RestController
public class GroupResource extends BaseResource {
    @Autowired
    private RedissonClient client;

    @GetMapping("/group/query/{stream}/{group}")
    public HttpResult query(@PathVariable("stream") @NonNull String stream, @PathVariable("group") @Nullable String group) {
        String str = "return redis.call('XINFO',KEYS[1],KEYS[2],ARGV[1])";
        RScript script = client.getScript(StringCodec.INSTANCE);
        if (StringUtils.hasText(stream)) {
            Object eval = script.eval(RScript.Mode.READ_ONLY, str, RScript.ReturnType.MULTI, Lists.list("consumers",appendPrefix(stream)), appendPrefix(group));
            return HttpResult.success(eval);
        } else {
            return HttpResult.success("");
        }
    }


}
