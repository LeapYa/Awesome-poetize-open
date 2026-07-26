package com.ld.poetry.vo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class BaseRequestVO extends Page {

    private String order;

    private boolean desc = true;

    private Integer source;

    private String commentType;

    private Integer floorCommentId;

    private String searchKey;

    private String articleSearch;

    // 是否推荐[0:否，1:是]
    private Boolean recommendStatus;

    private Integer sortId;

    private Integer labelId;

    private Boolean userStatus;

    private Integer userType;

    // 是否为第三方登录用户筛选[null:全部，true:第三方登录，false:普通注册]
    private Boolean isThirdPartyUser;

    private Integer userId;

    private String resourceType;

    private List<String> resourceTypes;

    private Boolean status;

    private String classify;

    // 时间段筛选：每个元素格式为 start~end（两端均可省略其一），
    // 同一字段的多个区间之间为 OR，不同字段之间为 AND，重复传参组合多个区间
    private List<String> createTimeRange;

    private List<String> updateTimeRange;

    // 首次公开发布时间（publish_time，RSS pubDate 口径）
    private List<String> publishTimeRange;
}
