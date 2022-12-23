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
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@RequestMapping("/fast/mq/group")
@RestController
public class GroupResource extends BaseResource {
    @Autowired
    private RedissonClient client;

    @GetMapping("/query/{stream}")
    public HttpResult query(@PathVariable("stream") @NonNull String stream) {
        String str = "return redis.call('XINFO',KEYS[1],KEYS[2])";
        RScript script = client.getScript(StringCodec.INSTANCE);
        if (StringUtils.hasText(stream)) {
            Object eval = script.eval(RScript.Mode.READ_ONLY, str, RScript.ReturnType.MULTI, Lists.list("GROUPS", appendPrefix(stream)));
            return HttpResult.success(eval);
        } else {
            return HttpResult.success("");
        }
    }

    @PostMapping("/create")
    public HttpResult create(@RequestParam("topic") String topic, @RequestParam("group") String group) {
        client.getStream(appendPrefix(topic)).createGroupAsync(group);
        return HttpResult.success("创建成功");
    }


}
