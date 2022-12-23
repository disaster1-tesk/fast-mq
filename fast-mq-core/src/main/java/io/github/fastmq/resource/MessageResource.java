package io.github.fastmq.resource;

import io.github.fastmq.domain.producer.delay.FastMQDelayTemplate;
import io.github.fastmq.domain.producer.instantaneous.FastMQTemplate;
import io.github.fastmq.infrastructure.http.HttpResult;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/fast/mq/message")
public class MessageResource extends BaseResource {
    @Autowired
    private FastMQTemplate fastMQTemplate;
    @Autowired
    private FastMQDelayTemplate fastMQDelayTemplate;
    @Autowired
    private RedissonClient client;


    @PostMapping("/addMsg")
    public HttpResult addMsg(@RequestParam("topic") String topic, @RequestBody Map msg) {
        fastMQTemplate.sendMsgAsync(appendPrefix(topic), msg);
        return HttpResult.success("发送成功");
    }


    @PostMapping("/addDelayMsg")
    public HttpResult addDelayMsg(@RequestParam("topic") String topic, @RequestParam("delayTime") long delayTime, @RequestBody Map msg) {
        fastMQDelayTemplate.sendMsg(msg, delayTime, appendPrefix(topic), TimeUnit.SECONDS);
        return HttpResult.success("发送成功");
    }



    @PutMapping("/delete/{topic}/{msg}")
    public HttpResult delete(@PathVariable("topic") String topic, @PathVariable("msg") String msg) {
        String delLua = "return redis.call('xdel',KEYS[1],ARGV[1]) ";
        RScript script = client.getScript(StringCodec.INSTANCE);
        Long eval = script.eval(RScript.Mode.READ_ONLY, delLua, RScript.ReturnType.INTEGER, Arrays.asList(appendPrefix(topic)), msg);
        return HttpResult.success(eval == 1l ? "删除成功" : "删除失败");
    }

}
