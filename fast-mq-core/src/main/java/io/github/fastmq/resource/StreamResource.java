package io.github.fastmq.resource;

import io.github.fastmq.application.StreamApplicationService;
import io.github.fastmq.infrastructure.constant.FastMQConstant;
import io.github.fastmq.infrastructure.http.HttpResult;
import org.redisson.api.*;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@RequestMapping("/fast/mq/stream")
@RestController
public class StreamResource extends BaseResource {

    @Autowired
    private StreamApplicationService streamApplicationService;

    @Autowired
    private RedissonClient client;


    @GetMapping("/query/{condition}")
    public HttpResult query(@PathVariable("condition") @NonNull String condition) {
        if (condition.equals("dead")) {
            return streamApplicationService.queryDeadStreamInfo();
        } else if (condition.equals("delay")) {
            return streamApplicationService.queryDelayStreamInfo();
        } else {
            RStream<Object, Object> stream = client.getStream(appendPrefix(condition));
            StreamInfo<Object, Object> info = stream.getInfo();
            return HttpResult.success(info);
        }
    }

    @GetMapping("/query/detail/{topic}")
    public HttpResult detail(@PathVariable("topic") @NonNull String topic) {
        String detailLua = "return redis.call('XINFO',KEYS[1],KEYS[2],KEYS[3])";
        RScript script = client.getScript(StringCodec.INSTANCE);
        Object eval = script.eval(RScript.Mode.READ_ONLY, detailLua, RScript.ReturnType.MULTI, Arrays.asList("STREAM", appendPrefix(topic), "FULL"));
        return HttpResult.success(eval);
    }


    @PostMapping("/createTopic")
    public HttpResult add(@RequestParam("name") @NonNull String name) {
        RStream<Object, Object> stream = client.getStream(appendPrefix(name));
        stream.createGroupAsync(FastMQConstant.DEFAULT_CONSUMER_GROUP);
        return HttpResult.success("创建成功");
    }


    @PutMapping("/delete/{name}")
    public HttpResult delete(@PathVariable("name") @NonNull String name) {
        RStream<Object, Object> stream = client.getStream(appendPrefix(name));
        boolean delete = stream.delete();
        return delete ? HttpResult.success("删除成功!") : HttpResult.fail(String.format("删除失败，流 %s 不存在", appendPrefix(name)));
    }


    @PutMapping("/update/claim/{stream}/{group}/{consumer}/{streamId}/{idleTime}")
    public HttpResult claim(@PathVariable("stream") @NonNull String stream, @PathVariable("group") @NonNull String group, @PathVariable("consumer") @NonNull String consumer, @PathVariable("streamId") @NonNull String streamId, @PathVariable("idleTime") @NonNull String idleTime) {
        String[] split = streamId.split("-");
        client.getStream(appendPrefix(stream)).claimAsync(group, consumer, Long.valueOf(idleTime), TimeUnit.SECONDS, new StreamMessageId(Long.valueOf(split[0]), Long.valueOf(split[1])));
        return HttpResult.success("处理成功！！");
    }


}
