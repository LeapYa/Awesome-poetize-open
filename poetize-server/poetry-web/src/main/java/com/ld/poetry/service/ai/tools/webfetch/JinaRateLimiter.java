package com.ld.poetry.service.ai.tools.webfetch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Jina Reader 滑动窗口速率限制器 + 排队队列。
 * <p>
 * 基于<strong>滑动窗口算法</strong>实现 RPM 限制，严格保证 60 秒窗口内请求数不超过配额。
 * <p>
 * 限流策略：
 * <ul>
 *   <li>无 API Key：60 秒窗口内最多 20 次请求（20 RPM）</li>
 *   <li>有 API Key：60 秒窗口内最多 500 次请求（500 RPM）</li>
 * </ul>
 * <p>
 * 工作原理：
 * <ol>
 *   <li>记录每次请求的时间戳到窗口队列</li>
 *   <li>窗口内请求数 &lt; 配额 → 立即放行，记录时间戳</li>
 *   <li>窗口内请求数 ≥ 配额 → 入队排队，等待最早的时间戳滑出窗口</li>
 *   <li>队首请求优先消费空出来的配额（FIFO 公平）</li>
 * </ol>
 * <p>
 * 例如无 Key 模式下：前 20 个请求直接放行 → 第 21 个等待窗口内最早请求过期
 * （最多等 60 秒）→ 过期后窗口腾出位置，排队请求按 FIFO 依次执行。
 * <p>
 * 排队超时：120 秒。前端可通过 /ai/jina-queue/status 实时查询排队位置。
 */
public class JinaRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(JinaRateLimiter.class);

    // ========== 限流参数 ==========

    /** 无 Key 模式：60 秒窗口内最大请求数 */
    private static final int FREE_RPM = 20;

    /** 有 Key 模式：60 秒窗口内最大请求数 */
    private static final int PAID_RPM = 500;

    /** 滑动窗口大小（毫秒）— 1 分钟。测试可子类化覆盖以使用更短窗口。 */
    protected long getWindowMs() {
        return 60_000L;
    }

    /** 排队超时时间（秒） */
    private static final long QUEUE_TIMEOUT_SECONDS = 120;

    /** 排队最大长度（超过则立即拒绝） */
    private static final int MAX_QUEUE_SIZE = 50;

    // ========== 滑动窗口状态 ==========

    /** 60 秒窗口内已放行请求的时间戳队列（按时间升序），受 lock 保护 */
    private final Deque<Long> requestTimestamps = new ArrayDeque<>();

    // ========== 排队队列状态 ==========

    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, QueueEntry> queueEntries = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    private final AtomicInteger totalQueued = new AtomicInteger(0);
    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    private final AtomicInteger totalTimedOut = new AtomicInteger(0);

    // ========== 可注入的时间源 ==========

    /**
     * 当前时间戳（毫秒）。测试可子类化覆盖以控制时间快进。
     */
    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    // ========== 核心方法 ==========

    /**
     * 尝试获取调用许可（滑动窗口 + 排队）。
     * <p>
     * 窗口内请求数不足配额 → 立即放行，记录时间戳；
     * 已满 → 入队排队，返回 requestId 供前端查询。
     *
     * @param hasApiKey 是否有 API Key
     * @return {@code AcquireResult}
     */
    public AcquireResult acquire(boolean hasApiKey) {
        int maxRpm = hasApiKey ? PAID_RPM : FREE_RPM;
        String tierName = hasApiKey ? "PAID" : "FREE";

        lock.lock();
        try {
            long now = currentTimeMillis();
            // 清理已滑出窗口的过期时间戳
            cleanExpiredTimestamps(now);

            // 窗口内请求数不足配额 → 立即放行
            if (requestTimestamps.size() < maxRpm) {
                requestTimestamps.addLast(now);
                totalProcessed.incrementAndGet();
                log.debug("Jina 立即放行: tier={}, windowCount={}/{}", tierName, requestTimestamps.size(), maxRpm);
                return new AcquireResult(null, true, 0, 0, tierName + "_IMMEDIATE");
            }

            // 队列已满 → 拒绝
            if (queue.size() >= MAX_QUEUE_SIZE) {
                String requestId = UUID.randomUUID().toString();
                log.warn("Jina 排队队列已满（{}），拒绝请求: tier={}", MAX_QUEUE_SIZE, tierName);
                return new AcquireResult(requestId, false, MAX_QUEUE_SIZE, 0, tierName + "_QUEUE_FULL");
            }

            // 窗口已满 → 入队排队
            String requestId = UUID.randomUUID().toString();
            int position = queue.size() + 1;
            long estimatedWaitMs = calculateEstimatedWait(position, maxRpm, now);

            QueueEntry entry = new QueueEntry(requestId, now, maxRpm);
            queue.add(requestId);
            queueEntries.put(requestId, entry);
            totalQueued.incrementAndGet();

            log.info("Jina 请求入队: requestId={}, tier={}, position={}, windowCount={}/{}, estimatedWait={}ms",
                    requestId, tierName, position, requestTimestamps.size(), maxRpm, estimatedWaitMs);
            return new AcquireResult(requestId, true, position, estimatedWaitMs, tierName + "_QUEUED");
        } finally {
            lock.unlock();
        }
    }

    /**
     * 阻塞等待排队完成（最多等待 timeoutSeconds 秒）。
     * <p>
     * 轮到队首时，清理窗口并检查是否有空缺席位。有则记录时间戳并放行，无则继续等待。
     *
     * @param requestId       排队 ID
     * @param timeoutSeconds  超时秒数
     * @return true 排队成功；false 超时或被中断
     */
    public boolean waitForTurn(String requestId, long timeoutSeconds) {
        if (requestId == null) {
            return true; // 无需排队（立即执行模式）
        }

        QueueEntry entry = queueEntries.get(requestId);
        if (entry == null) {
            return true; // 不在队列中，直接通过
        }

        long deadline = currentTimeMillis() + timeoutSeconds * 1000;

        while (currentTimeMillis() < deadline) {
            lock.lock();
            try {
                // 只有队首才能消费配额（FIFO 公平）
                String head = queue.peek();
                if (requestId.equals(head)) {
                    long now = currentTimeMillis();
                    cleanExpiredTimestamps(now);

                    // 窗口内有空缺席位 → 放行
                    if (requestTimestamps.size() < entry.maxRpm) {
                        requestTimestamps.addLast(now);
                        queue.poll();
                        queueEntries.remove(requestId);
                        totalProcessed.incrementAndGet();
                        log.info("Jina 排队完成: requestId={}, waited={}ms, windowCount={}/{}",
                                requestId, currentTimeMillis() - entry.enqueueTime,
                                requestTimestamps.size(), entry.maxRpm);
                        return true;
                    }
                }
            } finally {
                lock.unlock();
            }

            // 等待下一次检查
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                queue.remove(requestId);
                queueEntries.remove(requestId);
                return false;
            }
        }

        // 超时
        queue.remove(requestId);
        queueEntries.remove(requestId);
        totalTimedOut.incrementAndGet();
        log.warn("Jina 排队超时: requestId={}, timeout={}s", requestId, timeoutSeconds);
        return false;
    }

    /**
     * 清理已滑出窗口的过期时间戳（必须在持有 lock 时调用）。
     */
    private void cleanExpiredTimestamps(long now) {
        long cutoff = now - getWindowMs();
        while (!requestTimestamps.isEmpty() && requestTimestamps.peekFirst() < cutoff) {
            requestTimestamps.pollFirst();
        }
    }

    /**
     * 计算排队位置 position 的预计等待时间（毫秒）。
     * <p>
     * 队列第 1 位等最早的时间戳滑出窗口；第 2 位等第 2 早的；以此类推。
     * 如果 position 超过当前窗口内的时间戳数（多轮排队场景），用平均间隔估算。
     */
    private long calculateEstimatedWait(int position, int maxRpm, long now) {
        // 将时间戳转为数组以便按索引访问
        Long[] timestamps = requestTimestamps.toArray(new Long[0]);
        int idx = position - 1; // 0-indexed

        if (idx < timestamps.length) {
            // 第 position 位等待第 position 早的时间戳滑出窗口
            long expiry = timestamps[idx] + getWindowMs();
            return Math.max(0, expiry - now);
        }

        // position 超过窗口内时间戳数：用平均间隔估算后续等待
        // 平均间隔 = getWindowMs() / maxRpm（如 20 RPM → 3 秒/个）
        long windowMs = getWindowMs();
        long avgInterval = windowMs / maxRpm;
        long baseWait = timestamps.length > 0
                ? Math.max(0, timestamps[timestamps.length - 1] + windowMs - now)
                : 0;
        int remaining = position - timestamps.length;
        return baseWait + (long) remaining * avgInterval;
    }

    /**
     * <strong>429 自适应惩罚</strong>：Jina 服务端返回 429 时调用，立即"塞满"本地窗口。
     * <p>
     * 应用场景：本地滑动窗口与 Jina 服务端窗口存在时钟漂移或窗口定义差异，
     * 本地认为可以放行但 Jina 实际拒绝时，通过惩罚机制让本地立即同步到「已被限流」状态。
     * <p>
     * 实现：向 {@link #requestTimestamps} 塞入 {@code maxRpm} 个当前时间戳，让窗口立即变满。
     * 后续 {@link #acquire} 调用将自动入队排队，等到惩罚时间戳滑出窗口（约 1 个窗口期）后恢复放行。
     * <p>
     * 排队中的请求不受影响（它们的 {@code waitForTurn} 会等到惩罚时间戳过期后才能消费空缺席位）。
     *
     * @param hasApiKey 是否有 API Key（决定使用 FREE 还是 PAID 配额）
     * @return 惩罚前窗口内已有的请求数（用于诊断）
     */
    public int penalize(boolean hasApiKey) {
        int maxRpm = hasApiKey ? PAID_RPM : FREE_RPM;
        String tierName = hasApiKey ? "PAID" : "FREE";
        lock.lock();
        try {
            long now = currentTimeMillis();
            int before = requestTimestamps.size();
            // 清理过期时间戳后再塞入新时间戳，确保塞入数量等于 maxRpm
            cleanExpiredTimestamps(now);
            // 塞入 maxRpm 个当前时间戳，让窗口立即变满
            // 下次 cleanExpiredTimestamps 调用前，所有 acquire 都会走排队分支
            for (int i = 0; i < maxRpm; i++) {
                requestTimestamps.addLast(now);
            }
            log.warn("Jina 返回 429，本地窗口被惩罚塞满: tier={}, before={}, after={}, 接下来 {}ms 内不再放行",
                    tierName, before, requestTimestamps.size(), getWindowMs());
            return before;
        } finally {
            lock.unlock();
        }
    }

    // ========== 查询方法 ==========

    /**
     * 查询 requestId 的当前排队位置。
     *
     * @return 0 = 正在执行或已完成；>0 = 前面还有 N 个请求
     */
    public int getQueuePosition(String requestId) {
        if (requestId == null) {
            return 0;
        }
        QueueEntry entry = queueEntries.get(requestId);
        if (entry == null) {
            return 0;
        }
        return getPositionInQueue(requestId);
    }

    /**
     * 查询指定 requestId 的排队详情。
     */
    public QueueEntryInfo getQueueEntryInfo(String requestId) {
        if (requestId == null) {
            return null;
        }
        QueueEntry entry = queueEntries.get(requestId);
        if (entry == null) {
            return null;
        }
        int position = getPositionInQueue(requestId);
        long now = currentTimeMillis();
        long waitedMs = now - entry.enqueueTime;
        long estimatedWaitMs;
        lock.lock();
        try {
            estimatedWaitMs = calculateEstimatedWait(position, entry.maxRpm, now);
        } finally {
            lock.unlock();
        }
        return new QueueEntryInfo(requestId, position, waitedMs, estimatedWaitMs);
    }

    /**
     * 获取队列状态摘要。
     */
    public QueueStatus getQueueStatus() {
        return new QueueStatus(
                queue.size(),
                totalQueued.get(),
                totalProcessed.get(),
                totalTimedOut.get()
        );
    }

    /**
     * 获取队列快照（所有排队条目详情），供前端轮询展示。
     */
    public List<QueueEntryInfo> getQueueSnapshot() {
        List<QueueEntryInfo> snapshot = new ArrayList<>();
        long now = currentTimeMillis();

        lock.lock();
        try {
            int position = 0;
            for (String id : queue) {
                position++;
                QueueEntry entry = queueEntries.get(id);
                if (entry != null) {
                    long waitedMs = now - entry.enqueueTime;
                    long estimatedWaitMs = calculateEstimatedWait(position, entry.maxRpm, now);
                    snapshot.add(new QueueEntryInfo(id, position, waitedMs, estimatedWaitMs));
                }
            }
        } finally {
            lock.unlock();
        }
        return snapshot;
    }

    /**
     * 判断当前是否有请求正在排队。
     */
    public boolean isQueueActive() {
        return !queue.isEmpty();
    }

    /**
     * 计算 requestId 在队列中的位置（1 = 队首）。
     */
    private int getPositionInQueue(String requestId) {
        int position = 0;
        for (String id : queue) {
            position++;
            if (id.equals(requestId)) {
                return position;
            }
        }
        return 0;
    }

    // ========== 内部数据结构 ==========

    private static class QueueEntry {
        final String requestId;
        final long enqueueTime;
        final int maxRpm;

        QueueEntry(String requestId, long enqueueTime, int maxRpm) {
            this.requestId = requestId;
            this.enqueueTime = enqueueTime;
            this.maxRpm = maxRpm;
        }
    }

    public static class AcquireResult {
        private final String requestId;
        private final boolean acquired;
        private final int queuePosition;
        private final long estimatedWaitMs;
        private final String status;

        public AcquireResult(String requestId, boolean acquired, int queuePosition,
                             long estimatedWaitMs, String status) {
            this.requestId = requestId;
            this.acquired = acquired;
            this.queuePosition = queuePosition;
            this.estimatedWaitMs = estimatedWaitMs;
            this.status = status;
        }

        public String getRequestId() { return requestId; }
        public boolean isAcquired() { return acquired; }
        public int getQueuePosition() { return queuePosition; }
        public long getEstimatedWaitMs() { return estimatedWaitMs; }
        public String getStatus() { return status; }
    }

    public static class QueueStatus {
        private final int currentQueueSize;
        private final int totalQueued;
        private final int totalProcessed;
        private final int totalTimedOut;

        public QueueStatus(int currentQueueSize, int totalQueued, int totalProcessed, int totalTimedOut) {
            this.currentQueueSize = currentQueueSize;
            this.totalQueued = totalQueued;
            this.totalProcessed = totalProcessed;
            this.totalTimedOut = totalTimedOut;
        }

        public int getCurrentQueueSize() { return currentQueueSize; }
        public int getTotalQueued() { return totalQueued; }
        public int getTotalProcessed() { return totalProcessed; }
        public int getTotalTimedOut() { return totalTimedOut; }
    }

    public static class QueueEntryInfo {
        private final String requestId;
        private final int position;
        private final long waitedMs;
        private final long estimatedWaitMs;

        public QueueEntryInfo(String requestId, int position, long waitedMs, long estimatedWaitMs) {
            this.requestId = requestId;
            this.position = position;
            this.waitedMs = waitedMs;
            this.estimatedWaitMs = estimatedWaitMs;
        }

        public String getRequestId() { return requestId; }
        public int getPosition() { return position; }
        public long getWaitedMs() { return waitedMs; }
        public long getEstimatedWaitMs() { return estimatedWaitMs; }
    }
}
