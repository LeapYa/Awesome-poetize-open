package com.ld.poetry.service;
import com.ld.poetry.utils.JsonUtils;

import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.dao.ArticleMapper;
import com.ld.poetry.dao.HistoryInfoMapper;
import com.ld.poetry.dao.SysConfigMapper;
import com.ld.poetry.entity.HistoryInfo;
import com.ld.poetry.utils.RedisUtil;
import com.ld.poetry.utils.SpringContextUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WebmasterVisitRollbackTest {

    @InjectMocks
    private CacheService cacheService;

    @Mock
    private HistoryInfoMapper historyInfoMapper;

    @Mock
    private SysConfigMapper sysConfigMapper;

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private ArticleMapper articleMapper;

    @BeforeEach
    public void setUp() {
        // Mock SpringContextUtil to return mocked ApplicationContext and ArticleMapper
        new SpringContextUtil().setApplicationContext(applicationContext);
        lenient().when(applicationContext.getBean(ArticleMapper.class)).thenReturn(articleMapper);
    }

    @Test
    public void testWebmasterVisitRollback() {
        String webmasterIp = "192.168.99.99";
        int minutes = 5;

        // 1. Mock selectList from database to return one visit to article 123
        HistoryInfo historyInfo = new HistoryInfo();
        historyInfo.setIp(webmasterIp);
        historyInfo.setPageUri("/article/123");
        historyInfo.setCreateTime(LocalDateTime.now().minusMinutes(2));
        
        List<HistoryInfo> dbHistoryList = List.of(historyInfo);
        when(historyInfoMapper.selectList(any())).thenReturn(dbHistoryList);

        // 2. Mock Redis lGet to return one visit to article 123 (or another/same article)
        Map<String, Object> redisRecord = new HashMap<>();
        redisRecord.put("ip", webmasterIp);
        redisRecord.put("pageUri", "/article/123");
        redisRecord.put("createTime", LocalDateTime.now().minusMinutes(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        String recordJson = JsonUtils.toJsonString(redisRecord);
        
        when(redisUtil.lGet(anyString(), anyLong(), anyLong())).thenReturn(List.of(recordJson));

        // 3. Call the method under test
        cacheService.ignoreVisitIpAndCleanRecent(webmasterIp, minutes);

        // 5. Verify that decrementViewCount was called for article 123 with a count of 2
        // (1 from DB + 1 from Redis)
        verify(articleMapper, times(1)).decrementViewCount(eq(123), eq(2));
        
        // Also verify related caches are evicted
        verify(redisUtil, atLeastOnce()).del(eq(CacheConstants.buildArticleKey(123)));
    }
}
