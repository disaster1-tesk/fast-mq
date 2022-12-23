package io.github.fastmq.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SearchSyncEntity implements Serializable {
    /**
     * 数据id
     */
    private Long id;

    /**
     * 分类id
     */
    private Long categoryId;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 父分类id
     */
    private Long parentCategoryId;

    /**
     * 父分类名称
     */
    private String parentCategoryName;

    /**
     * 内容
     */
    private String content;

    /**
     * 标题
     */
    private String title;

    /**
     * 创建时间
     */
    private Long createTime;

    /**
     * 模块标识
     */
    private String module;

    /**
     * 频道名称
     */
    private String channelName;

    /**
     * 附件名单
     */
    private List<AttachmentEntity> attachment;
}
