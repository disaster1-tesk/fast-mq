package io.github.fastmq.resource;

import io.github.fastmq.application.FastMQMessageApplicationService;
import io.github.fastmq.domain.AttachmentEntity;
import io.github.fastmq.domain.SearchEntity;
import io.github.fastmq.domain.SearchSyncEntity;
import io.github.fastmq.domain.producer.delay.FastMQDelayTemplate;
import io.github.fastmq.domain.producer.instantaneous.FastMQTemplate;
import io.github.fastmq.infrastructure.constant.FastMQConstant;
import org.assertj.core.util.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/fast/mq/msg")
public class FastMQMessageResource {
    @Autowired
    private FastMQMessageApplicationService fastMQMessageApplicationService;
    @Autowired
    private FastMQTemplate fastMQTemplate;
    @Autowired
    private FastMQDelayTemplate fastMQDelayTemplate;
    
    @GetMapping("/sendMsg")
    public String sendMsg(){
        AttachmentEntity test = AttachmentEntity.builder()
                .id("123")
                .name("test")
                .build();
        AttachmentEntity test1 = AttachmentEntity.builder()
                .id("123")
                .name("test")
                .build();
        List<AttachmentEntity> list = Lists.list(test, test1);
        SearchSyncEntity build = SearchSyncEntity.builder()
                .id(1232323l)
                .categoryName("测试数据")
                .categoryId(9493l)
                .channelName("BBS")
                .content("测试内容")
                .createTime(System.currentTimeMillis())
                .module("platform")
                .parentCategoryId(2828l)
                .parentCategoryName("测试父数据")
                .title("测试主题")
                .build();
        build.setAttachment(list);
        SearchSyncEntity build1 = SearchSyncEntity.builder()
                .id(123232233l)
                .categoryName("测试数据1")
                .categoryId(949443l)
                .channelName("BBS3")
                .content("测试内容22")
                .createTime(System.currentTimeMillis())
                .module("platform")
                .parentCategoryId(56887l)
                .parentCategoryName("测试父数据2")
                .title("测试主题2")
                .build();
        build1.setAttachment(list);
        SearchEntity build2 = SearchEntity.builder()
                .hasNext(true)
                .pageNo(1)
                .pageSize(10)
                .total(20)
                .result(Lists.list(build, build1))
                .build();
        build2.setId(100l);
        fastMQTemplate.sendMsgAsync(FastMQConstant.DEFAULT_TOPIC,build2);
        return "成功！！";
    }

    @GetMapping("/sendDelayMsg")
    public String sendDelayMsg(){
        AttachmentEntity test = AttachmentEntity.builder()
                .id("123")
                .name("test")
                .build();
        AttachmentEntity test1 = AttachmentEntity.builder()
                .id("123")
                .name("test")
                .build();
        List<AttachmentEntity> list = Lists.list(test, test1);
        SearchSyncEntity build = SearchSyncEntity.builder()
                .id(1232323l)
                .categoryName("测试数据")
                .categoryId(9493l)
                .channelName("BBS")
                .content("测试内容")
                .createTime(System.currentTimeMillis())
                .module("platform")
                .parentCategoryId(2828l)
                .parentCategoryName("测试父数据")
                .title("测试主题")
                .build();
        build.setAttachment(list);
        SearchSyncEntity build1 = SearchSyncEntity.builder()
                .id(123232233l)
                .categoryName("测试数据1")
                .categoryId(949443l)
                .channelName("BBS3")
                .content("测试内容22")
                .createTime(System.currentTimeMillis())
                .module("platform")
                .parentCategoryId(56887l)
                .parentCategoryName("测试父数据2")
                .title("测试主题2")
                .build();
        build1.setAttachment(list);
        SearchEntity build2 = SearchEntity.builder()
                .hasNext(true)
                .pageNo(1)
                .pageSize(10)
                .total(20)
                .result(Lists.list(build, build1))
                .build();
        build2.setId(100l);
        fastMQDelayTemplate.sendMsg(build2,1);
        return "成功！！";
    }
}
