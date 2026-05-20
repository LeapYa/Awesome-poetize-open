package com.ld.poetry.dao;

import com.ld.poetry.entity.HistoryInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 历史信息 Mapper 接口
 * </p>
 *
 * @author sara
 * @since 2023-07-24
 */
@Mapper
public interface HistoryInfoMapper extends BaseMapper<HistoryInfo> {

    /**
     * 批量插入访问记录
     * @param historyInfoList 访问记录列表
     * @return 插入成功的记录数
     */
    int batchInsert(@Param("list") List<HistoryInfo> historyInfoList);

    /**
     * 访问IP最多的10个省
     */
    @Select("select province, count(distinct ip) as num" +
            " from history_info" +
            " where province is not null and province != ''" +
            " group by province" +
            " order by num desc" +
            " limit 10")
    List<Map<String, Object>> getHistoryByProvince();

    /**
     * 访问次数最多的10个IP
     */
    @Select("select ip, count(*) as num" +
            " from history_info" +
            " group by ip" +
            " order by num desc" +
            " limit 10")
    List<Map<String, Object>> getHistoryByIp();

    /**
     * 访问24小时内的数据
     */
    @Select("select ip, user_id, nation, province" +
            " from history_info" +
            " where create_time >= (now() - interval 24 hour)")
    List<Map<String, Object>> getHistoryBy24Hour();

    /**
     * 总访问量（按唯一IP统计）
     */
    @Select("select count(distinct ip) from history_info")
    Long getHistoryCount();

    /**
     * 今日访问量（按唯一IP统计）
     */
    @Select("select count(distinct ip) from history_info where date(create_time) = curdate()")
    Long getTodayHistoryCount();

    /**
     * 昨日访问量（按日历天计算）
     */
    @Select("select count(distinct ip) from history_info where date(create_time) = date_sub(curdate(), interval 1 day)")
    Long getYesterdayHistoryCount();

    /**
     * 昨日访问记录详情（按日历天计算）
     */
    @Select("select ip, user_id, nation, province" +
            " from history_info" +
            " where date(create_time) = date_sub(curdate(), interval 1 day)")
    List<Map<String, Object>> getHistoryByYesterday();

    /**
     * UA访问TOP10原始统计
     */
    @Select("select user_agent, count(*) as num" +
            " from history_info" +
            " where user_agent is not null and user_agent != ''" +
            " group by user_agent" +
            " order by num desc" +
            " limit 200")
    List<Map<String, Object>> getHistoryByUserAgent();

    /**
     * 昨日UA访问原始统计
     */
    @Select("select user_agent, count(*) as num" +
            " from history_info" +
            " where user_agent is not null and user_agent != ''" +
            " and date(create_time) = date_sub(curdate(), interval 1 day)" +
            " group by user_agent" +
            " order by num desc" +
            " limit 200")
    List<Map<String, Object>> getHistoryByUserAgentYesterday();

    /**
     * 文章页面访问原始统计
     */
    @Select("select page_uri, count(*) as num" +
            " from history_info" +
            " where page_uri is not null and page_uri != '' and page_uri like '/article/%'" +
            " group by page_uri" +
            " order by num desc" +
            " limit 200")
    List<Map<String, Object>> getHistoryByArticlePageUri();

    /**
     * 昨日文章页面访问原始统计
     */
    @Select("select page_uri, count(*) as num" +
            " from history_info" +
            " where page_uri is not null and page_uri != '' and page_uri like '/article/%'" +
            " and date(create_time) = date_sub(curdate(), interval 1 day)" +
            " group by page_uri" +
            " order by num desc" +
            " limit 200")
    List<Map<String, Object>> getHistoryByArticlePageUriYesterday();

    /**
     * 指定天数内的每日访问统计
     * @param days 需要统计的天数，最大 365
     * @return 每日唯一 IP 和总访问量
     */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS visit_date, " +
            "COUNT(DISTINCT ip) AS unique_visits, " +
            "COUNT(*) AS total_visits " +
            "FROM history_info " +
            "WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d') " +
            "ORDER BY visit_date")
    List<Map<String, Object>> getDailyVisitStats(@Param("days") Integer days);

    /**
     * 指定天数内的每日访问统计（排除今天）
     * @param days 需要统计的天数，最大 365
     * @return 每日唯一 IP 和总访问量（不包括今天）
     */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS visit_date, " +
            "COUNT(DISTINCT ip) AS unique_visits, " +
            "COUNT(*) AS total_visits " +
            "FROM history_info " +
            "WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "AND DATE(create_time) < CURDATE() " +
            "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d') " +
            "ORDER BY visit_date")
    List<Map<String, Object>> getDailyVisitStatsExcludeToday(@Param("days") Integer days);
}
