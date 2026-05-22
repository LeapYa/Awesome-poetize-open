package com.ld.poetry.dao;

import com.ld.poetry.entity.HistoryInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
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
     * 访问次数最多的10个省份/国家
     */
    default List<Map<String, Object>> getHistoryByProvince() {
        return getHistoryByProvince(java.util.Collections.emptyList());
    }

    @Select("<script>" +
            "select region as province, count(*) as num" +
            " from (" +
            " select case" +
            " when nation is not null and trim(nation) != ''" +
            "   and trim(nation) not in ('0', '-', '未知', '内网IP')" +
            "   and lower(trim(nation)) not in ('reserved', 'unknown', 'null', 'undefined', '内网ip', 'china', 'cn', 'chn', 'prc')" +
            "   and trim(nation) != '中国' then trim(nation)" +
            " when province is not null and trim(province) != ''" +
            "   and trim(province) not in ('0', '-', '未知', '内网IP')" +
            "   and lower(trim(province)) not in ('reserved', 'unknown', 'null', 'undefined', '内网ip') then trim(province)" +
            " when nation is not null and trim(nation) != ''" +
            "   and trim(nation) not in ('0', '-', '未知', '内网IP')" +
            "   and lower(trim(nation)) not in ('reserved', 'unknown', 'null', 'undefined', '内网ip') then trim(nation)" +
            " when city is not null and trim(city) != ''" +
            "   and trim(city) not in ('0', '-', '未知', '内网IP')" +
            "   and lower(trim(city)) not in ('reserved', 'unknown', 'null', 'undefined', '内网ip') then trim(city)" +
            " else '未知' end as region" +
            " from history_info" +
            " <where>" +
            " <if test='ignoredIps != null and ignoredIps.size > 0'>" +
            " ip not in" +
            " <foreach collection='ignoredIps' item='ip' open='(' separator=',' close=')'>#{ip}</foreach>" +
            " </if>" +
            " </where>" +
            " ) t" +
            " group by region" +
            " order by num desc" +
            " limit 10" +
            "</script>")
    List<Map<String, Object>> getHistoryByProvince(@Param("ignoredIps") List<String> ignoredIps);

    /**
     * 访问次数最多的10个IP
     */
    default List<Map<String, Object>> getHistoryByIp() {
        return getHistoryByIp(java.util.Collections.emptyList());
    }

    @Select("<script>" +
            "select ip, count(*) as num" +
            " from history_info" +
            " <where>" +
            " <if test='ignoredIps != null and ignoredIps.size > 0'>" +
            " ip not in" +
            " <foreach collection='ignoredIps' item='ignoredIp' open='(' separator=',' close=')'>#{ignoredIp}</foreach>" +
            " </if>" +
            " </where>" +
            " group by ip" +
            " order by num desc" +
            " limit 10" +
            "</script>")
    List<Map<String, Object>> getHistoryByIp(@Param("ignoredIps") List<String> ignoredIps);

    /**
     * 删除指定IP的访问历史
     */
    @Delete("delete from history_info where ip = #{ip}")
    int deleteByIp(@Param("ip") String ip);

    /**
     * 删除指定IP在指定时间之后的访问历史。
     */
    @Delete("delete from history_info where ip = #{ip} and create_time >= #{since}")
    int deleteByIpSince(@Param("ip") String ip, @Param("since") LocalDateTime since);

    /**
     * 删除指定User-Agent的访问历史。
     */
    @Delete("delete from history_info where user_agent = #{userAgent}")
    int deleteByUserAgent(@Param("userAgent") String userAgent);

    /**
     * 访问24小时内的数据
     */
    default List<Map<String, Object>> getHistoryBy24Hour() {
        return getHistoryBy24Hour(java.util.Collections.emptyList());
    }

    @Select("<script>" +
            "select ip, user_id, nation, province" +
            " from history_info" +
            " where create_time >= (now() - interval 24 hour)" +
            " <if test='ignoredIps != null and ignoredIps.size > 0'>" +
            " and ip not in" +
            " <foreach collection='ignoredIps' item='ignoredIp' open='(' separator=',' close=')'>#{ignoredIp}</foreach>" +
            " </if>" +
            "</script>")
    List<Map<String, Object>> getHistoryBy24Hour(@Param("ignoredIps") List<String> ignoredIps);

    /**
     * 总访问量（按唯一IP统计）
     */
    default Long getHistoryCount() {
        return getHistoryCount(java.util.Collections.emptyList());
    }

    @Select("<script>" +
            "select count(distinct ip) from history_info" +
            " <where>" +
            " <if test='ignoredIps != null and ignoredIps.size > 0'>" +
            " ip not in" +
            " <foreach collection='ignoredIps' item='ignoredIp' open='(' separator=',' close=')'>#{ignoredIp}</foreach>" +
            " </if>" +
            " </where>" +
            "</script>")
    Long getHistoryCount(@Param("ignoredIps") List<String> ignoredIps);

    /**
     * 今日访问量（按唯一IP统计）
     */
    default Long getTodayHistoryCount() {
        return getTodayHistoryCount(java.util.Collections.emptyList());
    }

    @Select("<script>" +
            "select count(distinct ip) from history_info where date(create_time) = curdate()" +
            " <if test='ignoredIps != null and ignoredIps.size > 0'>" +
            " and ip not in" +
            " <foreach collection='ignoredIps' item='ignoredIp' open='(' separator=',' close=')'>#{ignoredIp}</foreach>" +
            " </if>" +
            "</script>")
    Long getTodayHistoryCount(@Param("ignoredIps") List<String> ignoredIps);

    /**
     * 昨日访问量（按日历天计算）
     */
    default Long getYesterdayHistoryCount() {
        return getYesterdayHistoryCount(java.util.Collections.emptyList());
    }

    @Select("<script>" +
            "select count(distinct ip) from history_info where date(create_time) = date_sub(curdate(), interval 1 day)" +
            " <if test='ignoredIps != null and ignoredIps.size > 0'>" +
            " and ip not in" +
            " <foreach collection='ignoredIps' item='ignoredIp' open='(' separator=',' close=')'>#{ignoredIp}</foreach>" +
            " </if>" +
            "</script>")
    Long getYesterdayHistoryCount(@Param("ignoredIps") List<String> ignoredIps);

    /**
     * 昨日访问记录详情（按日历天计算）
     */
    default List<Map<String, Object>> getHistoryByYesterday() {
        return getHistoryByYesterday(java.util.Collections.emptyList());
    }

    @Select("<script>" +
            "select ip, user_id, nation, province" +
            " from history_info" +
            " where date(create_time) = date_sub(curdate(), interval 1 day)" +
            " <if test='ignoredIps != null and ignoredIps.size > 0'>" +
            " and ip not in" +
            " <foreach collection='ignoredIps' item='ignoredIp' open='(' separator=',' close=')'>#{ignoredIp}</foreach>" +
            " </if>" +
            "</script>")
    List<Map<String, Object>> getHistoryByYesterday(@Param("ignoredIps") List<String> ignoredIps);

    /**
     * UA访问TOP10原始统计
     */
    default List<Map<String, Object>> getHistoryByUserAgent() {
        return getHistoryByUserAgent(java.util.Collections.emptyList());
    }

    @Select("<script>" +
            "select user_agent, count(*) as num" +
            " from history_info" +
            " where user_agent is not null and user_agent != ''" +
            " <if test='ignoredIps != null and ignoredIps.size > 0'>" +
            " and ip not in" +
            " <foreach collection='ignoredIps' item='ignoredIp' open='(' separator=',' close=')'>#{ignoredIp}</foreach>" +
            " </if>" +
            " group by user_agent" +
            " order by num desc" +
            " limit 200" +
            "</script>")
    List<Map<String, Object>> getHistoryByUserAgent(@Param("ignoredIps") List<String> ignoredIps);

    /**
     * 昨日UA访问原始统计
     */
    default List<Map<String, Object>> getHistoryByUserAgentYesterday() {
        return getHistoryByUserAgentYesterday(java.util.Collections.emptyList());
    }

    @Select("<script>" +
            "select user_agent, count(*) as num" +
            " from history_info" +
            " where user_agent is not null and user_agent != ''" +
            " and date(create_time) = date_sub(curdate(), interval 1 day)" +
            " <if test='ignoredIps != null and ignoredIps.size > 0'>" +
            " and ip not in" +
            " <foreach collection='ignoredIps' item='ignoredIp' open='(' separator=',' close=')'>#{ignoredIp}</foreach>" +
            " </if>" +
            " group by user_agent" +
            " order by num desc" +
            " limit 200" +
            "</script>")
    List<Map<String, Object>> getHistoryByUserAgentYesterday(@Param("ignoredIps") List<String> ignoredIps);

    /**
     * 文章页面访问原始统计
     */
    default List<Map<String, Object>> getHistoryByArticlePageUri() {
        return getHistoryByArticlePageUri(java.util.Collections.emptyList());
    }

    @Select("<script>" +
            "select page_uri, count(*) as num" +
            " from history_info" +
            " where page_uri is not null and page_uri != '' and page_uri like '/article/%'" +
            " <if test='ignoredIps != null and ignoredIps.size > 0'>" +
            " and ip not in" +
            " <foreach collection='ignoredIps' item='ignoredIp' open='(' separator=',' close=')'>#{ignoredIp}</foreach>" +
            " </if>" +
            " group by page_uri" +
            " order by num desc" +
            " limit 200" +
            "</script>")
    List<Map<String, Object>> getHistoryByArticlePageUri(@Param("ignoredIps") List<String> ignoredIps);

    /**
     * 昨日文章页面访问原始统计
     */
    default List<Map<String, Object>> getHistoryByArticlePageUriYesterday() {
        return getHistoryByArticlePageUriYesterday(java.util.Collections.emptyList());
    }

    @Select("<script>" +
            "select page_uri, count(*) as num" +
            " from history_info" +
            " where page_uri is not null and page_uri != '' and page_uri like '/article/%'" +
            " and date(create_time) = date_sub(curdate(), interval 1 day)" +
            " <if test='ignoredIps != null and ignoredIps.size > 0'>" +
            " and ip not in" +
            " <foreach collection='ignoredIps' item='ignoredIp' open='(' separator=',' close=')'>#{ignoredIp}</foreach>" +
            " </if>" +
            " group by page_uri" +
            " order by num desc" +
            " limit 200" +
            "</script>")
    List<Map<String, Object>> getHistoryByArticlePageUriYesterday(@Param("ignoredIps") List<String> ignoredIps);

    /**
     * 指定天数内的每日访问统计
     * @param days 需要统计的天数，最大 365
     * @return 每日唯一 IP 和总访问量
     */
    default List<Map<String, Object>> getDailyVisitStats(Integer days) {
        return getDailyVisitStats(days, java.util.Collections.emptyList());
    }

    @Select("<script>" +
            "SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS visit_date, " +
            "COUNT(DISTINCT ip) AS unique_visits, " +
            "COUNT(*) AS total_visits " +
            "FROM history_info " +
            "WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            " <if test='ignoredIps != null and ignoredIps.size > 0'>" +
            " AND ip not in" +
            " <foreach collection='ignoredIps' item='ignoredIp' open='(' separator=',' close=')'>#{ignoredIp}</foreach>" +
            " </if>" +
            "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d') " +
            "ORDER BY visit_date" +
            "</script>")
    List<Map<String, Object>> getDailyVisitStats(@Param("days") Integer days,
                                                 @Param("ignoredIps") List<String> ignoredIps);

    /**
     * 指定天数内的每日访问统计（排除今天）
     * @param days 需要统计的天数，最大 365
     * @return 每日唯一 IP 和总访问量（不包括今天）
     */
    default List<Map<String, Object>> getDailyVisitStatsExcludeToday(Integer days) {
        return getDailyVisitStatsExcludeToday(days, java.util.Collections.emptyList());
    }

    @Select("<script>" +
            "SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS visit_date, " +
            "COUNT(DISTINCT ip) AS unique_visits, " +
            "COUNT(*) AS total_visits " +
            "FROM history_info " +
            "WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "AND DATE(create_time) &lt; CURDATE() " +
            " <if test='ignoredIps != null and ignoredIps.size > 0'>" +
            " AND ip not in" +
            " <foreach collection='ignoredIps' item='ignoredIp' open='(' separator=',' close=')'>#{ignoredIp}</foreach>" +
            " </if>" +
            "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d') " +
            "ORDER BY visit_date" +
            "</script>")
    List<Map<String, Object>> getDailyVisitStatsExcludeToday(@Param("days") Integer days,
                                                             @Param("ignoredIps") List<String> ignoredIps);
}
