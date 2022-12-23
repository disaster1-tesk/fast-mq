package io.github.fastmq.domain;

import lombok.*;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@ToString
@NoArgsConstructor
public class SearchEntity extends BaseEntity implements Serializable {
    private boolean hasNext;
    private long pageNo;
    private long pageSize;
    private List<SearchSyncEntity> result;
    private long total;
    private long totalPage;
}
