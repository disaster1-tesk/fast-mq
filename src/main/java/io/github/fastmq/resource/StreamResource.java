package io.github.fastmq.resource;

import io.github.fastmq.infrastructure.http.HttpResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fast/mq")
public class StreamResource {

    @GetMapping("/stream/query/stream")
    public HttpResult query(@PathVariable("condition") int condition) {
        return HttpResult.success("test");
    }

    @GetMapping("/group/query/group")
    public HttpResult query() {
        return HttpResult.success("test");
    }
}
