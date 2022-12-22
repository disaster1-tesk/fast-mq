package io.github.fastmq.resource;

import io.github.fastmq.application.StreamApplicationService;
import io.github.fastmq.infrastructure.constant.FastMQConstant;
import io.github.fastmq.infrastructure.http.HttpResult;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/fast/mq")
@RestController
public class StreamResource extends BaseResource {

    @Autowired
    private StreamApplicationService streamApplicationService;

    @Autowired
    private RedissonClient client;


    @GetMapping("/stream/query/{condition}")
    public HttpResult query(@PathVariable("condition")@NonNull String condition) {
        if (condition.equals("dead")) {
            return streamApplicationService.queryDeadStreamInfo();
        } else if (condition.equals("delay")) {
            return streamApplicationService.queryDelayStreamInfo();
        }else {
            RStream<Object, Object> stream = client.getStream(appendPrefix(condition));
            StreamInfo<Object, Object> info = stream.getInfo();
            return HttpResult.success(info);
        }
    }


    @PostMapping("/stream/createTopic")
    public HttpResult add(@RequestParam("name")@NonNull String name) {
        RStream<Object, Object> stream = client.getStream(appendPrefix(name));
        stream.createGroupAsync(FastMQConstant.DEFAULT_CONSUMER_GROUP);
        return HttpResult.success("创建成功");
    }


    @PutMapping("/stream/delete/{name}")
    public HttpResult delete(@PathVariable("name")@NonNull String name) {
        RStream<Object, Object> stream = client.getStream(appendPrefix(name));
        boolean delete = stream.delete();
        return delete ? HttpResult.success("删除成功!") : HttpResult.fail(String.format("删除失败，流 %s 不存在", appendPrefix(name)));
    }


}
