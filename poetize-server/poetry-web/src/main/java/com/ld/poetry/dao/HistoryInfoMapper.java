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
     * 指定天数内的省份/国家访客统计（排除今天，今日数据由 Redis 实时补充）
     * <p>
     * region 归一逻辑与 getHistoryByProvince 一致：海外用国家名，中国用省份名。
     * 另外拆分 js_verified_visits（前端JS上报的真实访客口径）。
     */
    @Select("<script>" +
            "select region as province, count(*) as num, count(distinct ip) as unique_visitors, " +
            " sum(case when ua_type in ('pc','mobile') and visit_source = 'track' then 1 else 0 end) as js_verified_visits" +
            " from (" +
            " select ip, visit_source, ua_type, case" +
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
            " where create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)" +
            " and DATE(create_time) &lt; CURDATE()" +
            " <if test='ignoredIps != null and ignoredIps.size > 0'>" +
            " and ip not in" +
            " <foreach collection='ignoredIps' item='ip' open='(' separator=',' close=')'>#{ip}</foreach>" +
            " </if>" +
            " ) t" +
            " group by region" +
            " order by num desc" +
            " limit 100" +
            "</script>")
    List<Map<String, Object>> getProvinceStatsByDays(@Param("days") Integer days,
                                                     @Param("ignoredIps") List<String> ignoredIps);

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
            "select user_agent, ua_type, ua_name, bot_verify_status, bot_verify_reason, count(*) as num" +
            " from history_info" +
            " where ((user_agent is not null and user_agent != '')" +
            " or (ua_type is not null and ua_type != '' and ua_name is not null and ua_name != ''))" +
            " <if test='ignoredIps != null and ignoredIps.size > 0'>" +
            " and ip not in" +
            " <foreach collection='ignoredIps' item='ignoredIp' open='(' separator=',' close=')'>#{ignoredIp}</foreach>" +
            " </if>" +
            " group by user_agent, ua_type, ua_name, bot_verify_status, bot_verify_reason" +
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
            "select user_agent, ua_type, ua_name, bot_verify_status, bot_verify_reason, count(*) as num" +
            " from history_info" +
            " where ((user_agent is not null and user_agent != '')" +
            " or (ua_type is not null and ua_type != '' and ua_name is not null and ua_name != ''))" +
            " and date(create_time) = date_sub(curdate(), interval 1 day)" +
            " <if test='ignoredIps != null and ignoredIps.size > 0'>" +
            " and ip not in" +
            " <foreach collection='ignoredIps' item='ignoredIp' open='(' separator=',' close=')'>#{ignoredIp}</foreach>" +
            " </if>" +
            " group by user_agent, ua_type, ua_name, bot_verify_status, bot_verify_reason" +
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

    /**
     * 指定天数内的每日人机分离统计（排除今天）
     * <p>
     * human = ua_type 为 pc/mobile；bot = 已识别的爬虫类型（crawler/ai_crawler/scanner/
     * automation/search_engine/spoofed_search_engine 等）；unclassified = ua_type 为空或 unknown。
     * <p>
     * 在 human 基础上另按 JS 执行情况细分：
     * js_verified = 记录来自前端 JS 上报（visit_source='track'，真实浏览器口径）；
     * browser_no_js = UA 像浏览器但仅被 Nginx 日志补录（visit_source='nginx'，从未执行 JS，
     * 大概率是伪装正常 UA 的爬虫）；存量无渠道标记（NULL）的记录不计入这两列。
     */
    @Select("<script>" +
            "SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS visit_date, " +
            "SUM(CASE WHEN ua_type IN ('pc','mobile') THEN 1 ELSE 0 END) AS human_visits, " +
            "COUNT(DISTINCT CASE WHEN ua_type IN ('pc','mobile') THEN ip END) AS human_unique_visits, " +
            "SUM(CASE WHEN ua_type IN ('pc','mobile') AND visit_source = 'track' THEN 1 ELSE 0 END) AS js_verified_visits, " +
            "COUNT(DISTINCT CASE WHEN ua_type IN ('pc','mobile') AND visit_source = 'track' THEN ip END) AS js_verified_unique_visits, " +
            "SUM(CASE WHEN ua_type IN ('pc','mobile') AND visit_source = 'nginx' THEN 1 ELSE 0 END) AS browser_no_js_visits, " +
            "SUM(CASE WHEN ua_type IS NOT NULL AND ua_type != '' AND ua_type NOT IN ('pc','mobile','unknown') THEN 1 ELSE 0 END) AS bot_visits, " +
            "SUM(CASE WHEN ua_type IS NULL OR ua_type = '' OR ua_type = 'unknown' THEN 1 ELSE 0 END) AS unclassified_visits " +
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
    List<Map<String, Object>> getDailyVisitUaStatsExcludeToday(@Param("days") Integer days,
                                                               @Param("ignoredIps") List<String> ignoredIps);

    /**
     * 指定天数内按 UA 类型汇总（排除今天）
     */
    @Select("<script>" +
            "SELECT CASE WHEN ua_type IS NULL OR ua_type = '' THEN 'unknown' ELSE ua_type END AS ua_type, " +
            "COUNT(*) AS visits, " +
            "COUNT(DISTINCT ip) AS unique_ips " +
            "FROM history_info " +
            "WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "AND DATE(create_time) &lt; CURDATE() " +
            " <if test='ignoredIps != null and ignoredIps.size > 0'>" +
            " AND ip not in" +
            " <foreach collection='ignoredIps' item='ignoredIp' open='(' separator=',' close=')'>#{ignoredIp}</foreach>" +
            " </if>" +
            "GROUP BY CASE WHEN ua_type IS NULL OR ua_type = '' THEN 'unknown' ELSE ua_type END " +
            "ORDER BY visits DESC" +
            "</script>")
    List<Map<String, Object>> getUaTypeSummaryExcludeToday(@Param("days") Integer days,
                                                           @Param("ignoredIps") List<String> ignoredIps);

    /**
     * 指定天数内的 UA 访问原始统计（排除今天，与 getHistoryByUserAgent 同构，
     * 可直接交给 UserAgentClassifier.aggregateRawAndVisitRecords 聚合）
     */
    @Select("<script>" +
            "select user_agent, ua_type, ua_name, bot_verify_status, bot_verify_reason, count(*) as num" +
            " from history_info" +
            " where ((user_agent is not null and user_agent != '')" +
            " or (ua_type is not null and ua_type != '' and ua_name is not null and ua_name != ''))" +
            " and create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)" +
            " and DATE(create_time) &lt; CURDATE()" +
            " <if test='ignoredIps != null and ignoredIps.size > 0'>" +
            " and ip not in" +
            " <foreach collection='ignoredIps' item='ignoredIp' open='(' separator=',' close=')'>#{ignoredIp}</foreach>" +
            " </if>" +
            " group by user_agent, ua_type, ua_name, bot_verify_status, bot_verify_reason" +
            " order by num desc" +
            " limit 200" +
            "</script>")
    List<Map<String, Object>> getHistoryByUserAgentForDays(@Param("days") Integer days,
                                                           @Param("ignoredIps") List<String> ignoredIps);

    /**
     * 指定天数内来源站点 × 人机分离交叉统计（排除今天）
     * <p>
     * 用于识别 Direct 流量中的爬虫占比：爬虫通常不携带 Referer，会被归入 Direct。
     */
    @Select("<script>" +
            "SELECT" +
            " CASE" +
            "  WHEN referer IS NULL OR referer = '' THEN 'Direct'" +
            "  <if test=\"siteHost != null and siteHost != ''\">" +
            "  WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(referer, '/', 3), '/', -1), ':', 1) = #{siteHost} THEN 'Direct'" +
            "  </if>" +
            "  ELSE SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(referer, '/', 3), '/', -1), ':', 1)" +
            " END AS referrer_host," +
            " COUNT(*) AS visits," +
            " COUNT(DISTINCT ip) AS unique_visitors," +
            " SUM(CASE WHEN ua_type IN ('pc','mobile') THEN 1 ELSE 0 END) AS human_visits," +
            " SUM(CASE WHEN ua_type IN ('pc','mobile') AND visit_source = 'track' THEN 1 ELSE 0 END) AS js_verified_visits," +
            " SUM(CASE WHEN ua_type IN ('pc','mobile') AND visit_source = 'nginx' THEN 1 ELSE 0 END) AS browser_no_js_visits," +
            " SUM(CASE WHEN ua_type IS NOT NULL AND ua_type != '' AND ua_type NOT IN ('pc','mobile','unknown') THEN 1 ELSE 0 END) AS bot_visits," +
            " SUM(CASE WHEN ua_type IS NULL OR ua_type = '' OR ua_type = 'unknown' THEN 1 ELSE 0 END) AS unclassified_visits" +
            " FROM history_info" +
            " WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)" +
            " AND DATE(create_time) &lt; CURDATE()" +
            " <if test='ignoredIps != null and ignoredIps.size > 0'>" +
            " AND ip not in" +
            " <foreach collection='ignoredIps' item='ignoredIp' open='(' separator=',' close=')'>#{ignoredIp}</foreach>" +
            " </if>" +
            " GROUP BY referrer_host" +
            " ORDER BY visits DESC" +
            " LIMIT 50" +
            "</script>")
    List<Map<String, Object>> getReferrerUaSplitExcludeToday(@Param("days") Integer days,
                                                             @Param("siteHost") String siteHost,
                                                             @Param("ignoredIps") List<String> ignoredIps);

    /**
     * 访客来源站点统计（排除今天，今日数据由 Redis 实时补充）
     * <p>
     * 按 Referer 的 host 聚合，referer 为空或站内跳转归为 Direct。
     * host 提取：SUBSTRING_INDEX 三层嵌套，先取前3段(协议+host[:port])，
     * 再取最后一段(host[:port])，最后去掉端口。
     *
     * @param days       统计天数
     * @param siteHost   站点自身 host（用于识别站内跳转并归为 Direct），可为 null
     * @param ignoredIps 需排除的 IP（管理员等）
     */
    @Select("<script>" +
            "SELECT" +
            " CASE" +
            "  WHEN referer IS NULL OR referer = '' THEN 'Direct'" +
            "  <if test=\"siteHost != null and siteHost != ''\">" +
            "  WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(referer, '/', 3), '/', -1), ':', 1) = #{siteHost} THEN 'Direct'" +
            "  </if>" +
            "  ELSE SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(referer, '/', 3), '/', -1), ':', 1)" +
            " END AS referrer_host," +
            " COUNT(*) AS visits," +
            " COUNT(DISTINCT ip) AS unique_visitors" +
            " FROM history_info" +
            " WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)" +
            " AND DATE(create_time) &lt; CURDATE()" +
            " <if test='ignoredIps != null and ignoredIps.size > 0'>" +
            " AND ip not in" +
            " <foreach collection='ignoredIps' item='ignoredIp' open='(' separator=',' close=')'>#{ignoredIp}</foreach>" +
            " </if>" +
            " GROUP BY referrer_host" +
            " ORDER BY visits DESC" +
            "</script>")
    List<Map<String, Object>> getReferrerStatsExcludeToday(@Param("days") Integer days,
                                                            @Param("siteHost") String siteHost,
                                                            @Param("ignoredIps") List<String> ignoredIps);

    /**
     * 访客来源站点历史统计（所有历史数据）
     */
    @Select("<script>" +
            "SELECT" +
            " CASE" +
            "  WHEN referer IS NULL OR referer = '' THEN 'Direct'" +
            "  <if test=\"siteHost != null and siteHost != ''\">" +
            "  WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(referer, '/', 3), '/', -1), ':', 1) = #{siteHost} THEN 'Direct'" +
            "  </if>" +
            "  ELSE SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(referer, '/', 3), '/', -1), ':', 1)" +
            " END AS referrer_host," +
            " COUNT(*) AS num" +
            " FROM history_info" +
            " <where>" +
            " <if test='ignoredIps != null and ignoredIps.size > 0'>" +
            " ip not in" +
            " <foreach collection='ignoredIps' item='ignoredIp' open='(' separator=',' close=')'>#{ignoredIp}</foreach>" +
            " </if>" +
            " </where>" +
            " GROUP BY referrer_host" +
            " ORDER BY num DESC" +
            " LIMIT 200" +
            "</script>")
    List<Map<String, Object>> getReferrerHistory(@Param("siteHost") String siteHost,
                                                 @Param("ignoredIps") List<String> ignoredIps);

    /**
     * 昨日访客来源站点统计
     */
    @Select("<script>" +
            "SELECT" +
            " CASE" +
            "  WHEN referer IS NULL OR referer = '' THEN 'Direct'" +
            "  <if test=\"siteHost != null and siteHost != ''\">" +
            "  WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(referer, '/', 3), '/', -1), ':', 1) = #{siteHost} THEN 'Direct'" +
            "  </if>" +
            "  ELSE SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(referer, '/', 3), '/', -1), ':', 1)" +
            " END AS referrer_host," +
            " COUNT(*) AS num" +
            " FROM history_info" +
            " WHERE date(create_time) = date_sub(curdate(), interval 1 day)" +
            " <if test='ignoredIps != null and ignoredIps.size > 0'>" +
            " AND ip not in" +
            " <foreach collection='ignoredIps' item='ignoredIp' open='(' separator=',' close=')'>#{ignoredIp}</foreach>" +
            " </if>" +
            " GROUP BY referrer_host" +
            " ORDER BY num DESC" +
            " LIMIT 200" +
            "</script>")
    List<Map<String, Object>> getReferrerHistoryYesterday(@Param("siteHost") String siteHost,
                                                          @Param("ignoredIps") List<String> ignoredIps);
}
