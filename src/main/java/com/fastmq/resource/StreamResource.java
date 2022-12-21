package com.fastmq.resource;

import com.fastmq.infrastructure.http.HttpResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fast/mq")
public class StreamResource {

    @GetMapping("/stream/query/{condition}")
    public HttpResult query(@PathVariable("condition") int condition) {
        return HttpResult.success("test");
    }

    @GetMapping("/group/query/{condition}")
    public HttpResult query() {
        return HttpResult.success("test");
    }
}
