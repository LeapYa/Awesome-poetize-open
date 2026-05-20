package com.ld.poetry.handle;

import com.ld.poetry.dao.HistoryInfoMapper;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("unchecked")
@Component
@EnableScheduling
@Slf4j
public class ScheduleTask {

    @Autowired
    private HistoryInfoMapper historyInfoMapper;

    @Autowired
    private CacheService cacheService;

    /**
     * 每天凌晨执行的完整清理和统计任务
     * 此时访问量统计会刷新，包括总访问量和今日访问量
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanIpHistory() {
        try {
            log.info("====================开始执行每日访问记录同步和统计任务====================");
            
            // 同步昨天的Redis访问记录到数据库
            syncVisitRecordsToDatabase();

            // 重新生成统计数据（仅基于数据库数据，无Redis实时计数）
            cacheService.refreshLocationStatisticsCache();
            log.info("IP历史记录清理和统计任务执行完成，访问量统计已更新");
            
        } catch (Exception e) {
            log.error("IP历史记录清理和统计任务执行失败", e);
            // 确保缓存不为空，避免前端显示异常
            ensureStatisticsCache();
        }
    }
    

    
    /**
     * 确保统计缓存存在，避免前端报错
     */
    private void ensureStatisticsCache() {
        try {
            Map<String, Object> cachedStats = (Map<String, Object>) cacheService.getCachedIpHistoryStatistics();
            if (cachedStats == null) {
                log.warn("统计缓存为空，重新生成统计数据");
                refreshStatisticsCache();
            } else {
            }
        } catch (Exception e) {
            log.error("检查统计缓存时出错，初始化默认数据", e);
            initializeDefaultStatistics();
        }
    }

    /**
     * 刷新统计缓存（仅基于数据库数据，无Redis实时计数）
     */
    private void refreshStatisticsCache() {
        try (var scope = StructuredTaskScope.open()) {
            // Fork 省份统计查询
            Subtask<List<Map<String, Object>>> provinceTask = scope.fork(() -> 
                historyInfoMapper.getHistoryByProvince()
            );
            
            // Fork IP统计查询
            Subtask<List<Map<String, Object>>> ipTask = scope.fork(() -> 
                historyInfoMapper.getHistoryByIp()
            );
            
            // Fork 小时统计查询
            Subtask<List<Map<String, Object>>> hourTask = scope.fork(() -> 
                historyInfoMapper.getHistoryBy24Hour()
            );
            
            // Fork 总数查询
            Subtask<Long> countTask = scope.fork(() -> 
                historyInfoMapper.getHistoryCount()
            );
            
            // 等待所有查询完成
            scope.join();
            
            // 获取查询结果
            List<Map<String, Object>> provinceStats = 
                (provinceTask.state() == Subtask.State.SUCCESS) ? provinceTask.get() : new ArrayList<>();
            List<Map<String, Object>> ipStats = 
                (ipTask.state() == Subtask.State.SUCCESS) ? ipTask.get() : new ArrayList<>();
            List<Map<String, Object>> hourStats = 
                (hourTask.state() == Subtask.State.SUCCESS) ? hourTask.get() : new ArrayList<>();
            Long totalCount = 
                (countTask.state() == Subtask.State.SUCCESS) ? countTask.get() : 0L;

            // 构建统计数据
            Map<String, Object> stats = new HashMap<>();
            stats.put(CommonConst.IP_HISTORY_PROVINCE, provinceStats != null ? provinceStats : new ArrayList<>());
            stats.put(CommonConst.IP_HISTORY_IP, ipStats != null ? ipStats : new ArrayList<>());
            stats.put(CommonConst.IP_HISTORY_HOUR, hourStats != null ? hourStats : new ArrayList<>());
            stats.put(CommonConst.IP_HISTORY_COUNT, totalCount != null ? totalCount : 0L);

            // 缓存统计数据
            cacheService.cacheIpHistoryStatistics(stats);
            log.info("统计缓存刷新成功，数据库总访问量: {}", totalCount);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("统计缓存刷新被中断", e);
            initializeDefaultStatistics();
        } catch (Exception e) {
            log.error("刷新统计缓存失败，使用默认数据", e);
            initializeDefaultStatistics();
        }
    }

    /**
     * 初始化默认统计数据
     */
    private void initializeDefaultStatistics() {
        try {
            Map<String, Object> defaultStats = new HashMap<>();
            defaultStats.put(CommonConst.IP_HISTORY_PROVINCE, new ArrayList<>());
            defaultStats.put(CommonConst.IP_HISTORY_IP, new ArrayList<>());
            defaultStats.put(CommonConst.IP_HISTORY_HOUR, new ArrayList<>());
            defaultStats.put(CommonConst.IP_HISTORY_COUNT, 0L);
            cacheService.cacheIpHistoryStatistics(defaultStats);
            log.info("已初始化默认统计数据");
        } catch (Exception e) {
            log.error("初始化默认统计数据失败", e);
        }
    }

    /**
     * 应用启动时初始化缓存
     */
    @Scheduled(fixedDelay = Long.MAX_VALUE) // 只执行一次
    public void initializeCacheOnStartup() {
        log.info("应用启动，初始化统计缓存");
        ensureStatisticsCache();
    }
    
    /**
     * 同步Redis中的访问记录到数据库
     */
    private void syncVisitRecordsToDatabase() {
        try {
            // 获取昨天的日期
            String yesterday = java.time.LocalDate.now().minusDays(1).toString();
            log.info("开始同步{}的访问记录到数据库", yesterday);
            
            // 获取昨天的未同步访问记录
            List<Map<String, Object>> visitRecords = cacheService.getUnsyncedDailyVisitRecords(yesterday);
            
            if (visitRecords.isEmpty()) {
                log.info("{}没有未同步访问记录需要同步", yesterday);
                return;
            }
            
            // 预处理访问记录，转换为实体对象列表
            List<com.ld.poetry.entity.HistoryInfo> historyInfoList = new java.util.ArrayList<>();
            List<Map<String, Object>> validRecords = new java.util.ArrayList<>();
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            
            for (Map<String, Object> record : visitRecords) {
                try {
                    com.ld.poetry.entity.HistoryInfo historyInfo = new com.ld.poetry.entity.HistoryInfo();
                    historyInfo.setIp((String) record.get("ip"));
                    
                    Object userIdObj = record.get("userId");
                    if (userIdObj != null) {
                        historyInfo.setUserId(Integer.valueOf(userIdObj.toString()));
                    }
                    
                    historyInfo.setNation((String) record.get("nation"));
                    historyInfo.setProvince((String) record.get("province"));
                    historyInfo.setCity((String) record.get("city"));
                    Object pageUriObj = record.get("pageUri");
                    if (pageUriObj != null) {
                        historyInfo.setPageUri(pageUriObj.toString());
                    }
                    Object userAgentObj = record.get("userAgent");
                    if (userAgentObj != null) {
                        historyInfo.setUserAgent(userAgentObj.toString());
                    }
                    
                    // 设置创建时间
                    String createTimeStr = (String) record.get("createTime");
                    if (createTimeStr != null) {
                        // 解析数据库兼容格式的时间字符串 yyyy-MM-dd HH:mm:ss
                        historyInfo.setCreateTime(java.time.LocalDateTime.parse(createTimeStr, formatter));
                    } else {
                        historyInfo.setCreateTime(java.time.LocalDateTime.now().minusDays(1));
                    }
                    
                    historyInfoList.add(historyInfo);
                    validRecords.add(record);
                    
                } catch (Exception e) {
                    log.error("处理访问记录失败: {}", record, e);
                }
            }
            
            // 真正的批量插入
            AtomicInteger successCount = new AtomicInteger(0);
            int failCount = 0;
            Map<Integer, List<Map<String, Object>>> successfulBatches = new ConcurrentHashMap<>();
            
            if (!historyInfoList.isEmpty()) {
                try (var scope = StructuredTaskScope.open()) {
                    // 分批插入，避免单次插入数据量过大
                    int batchSize = 500; // 每批插入500条
                    List<Subtask<Integer>> insertTasks = new ArrayList<>();
                    
                    for (int i = 0; i < historyInfoList.size(); i += batchSize) {
                        final int batchIndex = i / batchSize;
                        final int startIdx = i;
                        final int endIdx = Math.min(i + batchSize, historyInfoList.size());
                        
                        // Fork 并行插入任务
                        insertTasks.add(scope.fork(() -> {
                            List<com.ld.poetry.entity.HistoryInfo> batch = historyInfoList.subList(startIdx, endIdx);
                            List<Map<String, Object>> batchRecords = validRecords.subList(startIdx, endIdx);
                            
                            int insertedCount = historyInfoMapper.batchInsert(batch);
                            
                            // 记录成功插入的批次
                            if (insertedCount > 0) {
                                successfulBatches.put(batchIndex, batchRecords.subList(0, insertedCount));
                            }
                            
                            log.info("批量插入第{}批访问记录: {} 条", batchIndex + 1, insertedCount);
                            return insertedCount;
                        }));
                    }
                    
                    // 等待所有批次插入完成
                    scope.join();
                    
                    // 统计成功数量
                    for (Subtask<Integer> task : insertTasks) {
                        if (task.state() == Subtask.State.SUCCESS) {
                            successCount.addAndGet(task.get());
                        }
                    }
                    
                    failCount = historyInfoList.size() - successCount.get();
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("批量插入被中断", e);
                    failCount = historyInfoList.size() - successCount.get();
                } catch (Exception e) {
                    log.error("批量插入访问记录失败", e);
                    failCount = historyInfoList.size() - successCount.get();
                }
            }
            
            // 合并所有成功插入的记录
            List<Map<String, Object>> successfullyInsertedRecords = new java.util.ArrayList<>();
            successfulBatches.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> successfullyInsertedRecords.addAll(entry.getValue()));
            
            log.info("{}的访问记录同步完成: 成功{}, 失败{}", yesterday, successCount.get(), failCount);
            
            // 标记成功同步的记录，并清空昨天的缓存（因为昨天已经过去了）
            if (successCount.get() > 0) {
                // 先标记已同步
                cacheService.markVisitRecordsAsSynced(yesterday, successfullyInsertedRecords);
                // 清空昨天的缓存（定时任务可以清空昨天的缓存）
                cacheService.clearDailyVisitRecords(yesterday);
                log.info("已清空{}的Redis访问记录缓存", yesterday);
            }
            
        } catch (Exception e) {
            log.error("同步访问记录到数据库失败", e);
        }
    }
}
