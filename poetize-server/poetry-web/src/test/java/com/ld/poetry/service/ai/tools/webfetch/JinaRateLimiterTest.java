package com.ld.poetry.service.ai.tools.webfetch;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JinaRateLimiter} 滑动窗口 + 排队机制单元测试。
 * <p>
 * 通过子类化覆盖 {@link JinaRateLimiter#currentTimeMillis()} 注入受控时钟，
 * 避免真实等待 60 秒窗口滑出。
 */
class JinaRateLimiterTest {

    /**
     * 可控时钟的限流器：在真实墙钟基础上叠加一个 offset，并把窗口缩短便于测试。
     */
    static class TestableJinaRateLimiter extends JinaRateLimiter {
        private final AtomicLong offset = new AtomicLong(0);
        private final long windowMs;

        TestableJinaRateLimiter() {
            this(100L); // 默认 100ms 短窗口
        }

        TestableJinaRateLimiter(long windowMs) {
            this.windowMs = windowMs;
        }

        @Override
        protected long currentTimeMillis() {
            return System.currentTimeMillis() + offset.get();
        }

        @Override
        protected long getWindowMs() {
            return windowMs;
        }

        void advanceBy(long deltaMs) {
            offset.addAndGet(deltaMs);
        }
    }

    // ========== 场景 1：窗口未满时立即放行 ==========

    @Test
    void testImmediateAcquireBeforeWindowFull() {
        TestableJinaRateLimiter limiter = new TestableJinaRateLimiter();
        for (int i = 0; i < 20; i++) {
            JinaRateLimiter.AcquireResult r = limiter.acquire(false);
            assertTrue(r.isAcquired(), "第 " + (i + 1) + " 个请求应立即放行");
            assertNull(r.getRequestId(), "立即放行不应返回 requestId");
            assertEquals("FREE_IMMEDIATE", r.getStatus());
            assertEquals(0, r.getQueuePosition());
        }
    }

    // ========== 场景 2：窗口满后入队排队 ==========

    @Test
    void testQueueWhenWindowFull() {
        TestableJinaRateLimiter limiter = new TestableJinaRateLimiter();
        for (int i = 0; i < 20; i++) {
            limiter.acquire(false);
        }
        // 第 21 个 → 入队
        JinaRateLimiter.AcquireResult queued = limiter.acquire(false);
        assertTrue(queued.isAcquired(), "入队也算 acquired=true（需后续 waitForTurn）");
        assertNotNull(queued.getRequestId(), "入队请求必须返回 requestId 供轮询");
        assertEquals(1, queued.getQueuePosition());
        assertEquals("FREE_QUEUED", queued.getStatus());
        assertTrue(queued.getEstimatedWaitMs() > 0, "排队应有非零预计等待");
    }

    // ========== 场景 3：窗口满后入队，队首等待至窗口滑出后被放行 ==========

    @Test
    void testSlidingWindowExpiresAndReleases() throws InterruptedException {
        TestableJinaRateLimiter limiter = new TestableJinaRateLimiter();
        for (int i = 0; i < 20; i++) {
            limiter.acquire(false);
        }
        JinaRateLimiter.AcquireResult queued = limiter.acquire(false);
        assertEquals("FREE_QUEUED", queued.getStatus());

        AtomicBoolean result = new AtomicBoolean(false);
        Thread t = new Thread(() ->
                result.set(limiter.waitForTurn(queued.getRequestId(), 5)));
        t.start();

        // 窗口仅 100ms：真实墙钟流逝 300ms 后最早时间戳必然滑出窗口
        Thread.sleep(300);

        t.join(3000);
        assertFalse(t.isAlive(), "工作线程应已退出");
        assertTrue(result.get(), "窗口滑出后排队请求应被放行");

        JinaRateLimiter.QueueStatus status = limiter.getQueueStatus();
        assertEquals(0, status.getCurrentQueueSize(), "队列应已清空");
        assertEquals(21, status.getTotalProcessed(), "20 立即放行 + 1 排队消费");
    }

    // ========== 场景 4：排队超时返回 false ==========

    @Test
    void testWaitForTurnTimesOut() throws InterruptedException {
        // 用 60 秒长窗口：1 秒内窗口不会滑出，确保走超时分支
        TestableJinaRateLimiter limiter = new TestableJinaRateLimiter(60_000L);
        for (int i = 0; i < 20; i++) {
            limiter.acquire(false);
        }
        JinaRateLimiter.AcquireResult queued = limiter.acquire(false);

        long start = System.currentTimeMillis();
        boolean acquired = limiter.waitForTurn(queued.getRequestId(), 1);
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(acquired, "窗口未滑出时应超时");
        assertTrue(elapsed >= 900, "至少等了 1 秒");
        assertTrue(elapsed < 3000, "不应超过 3 秒");

        JinaRateLimiter.QueueStatus status = limiter.getQueueStatus();
        assertEquals(1, status.getTotalTimedOut(), "超时计数 +1");
        // 超时后 requestId 应已从队列中移除
        assertEquals(0, limiter.getQueuePosition(queued.getRequestId()));
    }

    // ========== 场景 5：队列满后第 71 个被立即拒绝 ==========

    @Test
    void testQueueFullRejection() {
        TestableJinaRateLimiter limiter = new TestableJinaRateLimiter();
        // 20 个立即放行
        for (int i = 0; i < 20; i++) {
            limiter.acquire(false);
        }
        // 50 个入队（队列最大 50）
        for (int i = 0; i < 50; i++) {
            JinaRateLimiter.AcquireResult r = limiter.acquire(false);
            assertTrue(r.isAcquired(), "队列未满时第 " + (i + 1) + " 个入队应成功");
            assertNotNull(r.getRequestId());
        }
        // 第 71 个 → 被拒绝
        JinaRateLimiter.AcquireResult rejected = limiter.acquire(false);
        assertFalse(rejected.isAcquired(), "队列满后应立即拒绝");
        assertEquals("FREE_QUEUE_FULL", rejected.getStatus());
        assertEquals(50, rejected.getQueuePosition(), "队列位置应为最大值");

        JinaRateLimiter.QueueStatus status = limiter.getQueueStatus();
        assertEquals(50, status.getCurrentQueueSize());
        assertEquals(50, status.getTotalQueued());
    }

    // ========== 场景 6：PAID 模式（有 Key）配额更高 ==========

    @Test
    void testPaidModeHigherQuota() {
        TestableJinaRateLimiter limiter = new TestableJinaRateLimiter();
        // PAID 模式 500 RPM，验证前若干个立即放行
        for (int i = 0; i < 10; i++) {
            JinaRateLimiter.AcquireResult r = limiter.acquire(true);
            assertTrue(r.isAcquired());
            assertNull(r.getRequestId());
            assertEquals("PAID_IMMEDIATE", r.getStatus());
        }
    }

    // ========== 场景 7：getQueueStatus 正确反映状态 ==========

    @Test
    void testGetQueueStatusReflectsState() {
        TestableJinaRateLimiter limiter = new TestableJinaRateLimiter();
        for (int i = 0; i < 20; i++) {
            limiter.acquire(false);
        }
        limiter.acquire(false);
        limiter.acquire(false);

        JinaRateLimiter.QueueStatus status = limiter.getQueueStatus();
        assertEquals(2, status.getCurrentQueueSize());
        assertEquals(2, status.getTotalQueued());
        assertEquals(20, status.getTotalProcessed(), "立即放行 20 个");
        assertEquals(0, status.getTotalTimedOut());
    }

    // ========== 场景 8：getQueueEntryInfo 返回排队详情 ==========

    @Test
    void testGetQueueEntryInfo() {
        TestableJinaRateLimiter limiter = new TestableJinaRateLimiter();
        for (int i = 0; i < 20; i++) {
            limiter.acquire(false);
        }
        JinaRateLimiter.AcquireResult first = limiter.acquire(false);
        limiter.acquire(false);

        JinaRateLimiter.QueueEntryInfo info = limiter.getQueueEntryInfo(first.getRequestId());
        assertNotNull(info);
        assertEquals(1, info.getPosition(), "第一个入队请求应在队首");
        assertTrue(info.getEstimatedWaitMs() > 0);
        assertTrue(info.getWaitedMs() >= 0);

        // 不存在的 requestId
        assertNull(limiter.getQueueEntryInfo("non-existent-id"));
        // null 入参
        assertNull(limiter.getQueueEntryInfo(null));
    }

    // ========== 场景 9：waitForTurn 入参为 null 直接返回 true（立即执行模式） ==========

    @Test
    void testWaitForTurnWithNullReturnsImmediately() {
        TestableJinaRateLimiter limiter = new TestableJinaRateLimiter();
        long start = System.currentTimeMillis();
        boolean acquired = limiter.waitForTurn(null, 5);
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(acquired);
        assertTrue(elapsed < 100, "null 入参应立即返回，不阻塞");
    }

    // ========== 场景 10：penalize 立即塞满窗口，后续请求强制排队 ==========

    /**
     * 模拟 Jina 服务端返回 429：本地算法认为窗口不满放行了请求，
     * 但 Jina 实际拒绝。此时调用 {@link JinaRateLimiter#penalize(boolean)} 把本地窗口塞满，
     * 后续 acquire 必须入队，等惩罚时间戳滑出窗口后才能恢复。
     */
    @Test
    void testPenalizeFillsWindowAndForcesQueue() {
        TestableJinaRateLimiter limiter = new TestableJinaRateLimiter();
        // 灌入 5 个立即放行（窗口内只有 5 个，远未满 20）
        for (int i = 0; i < 5; i++) {
            limiter.acquire(false);
        }
        // 模拟 Jina 返回 429，惩罚本地窗口
        int before = limiter.penalize(false);
        assertEquals(5, before, "惩罚前窗口内应有 5 个时间戳");

        // 紧接着 acquire 应该立即入队（窗口已被塞满 20 个）
        JinaRateLimiter.AcquireResult queued = limiter.acquire(false);
        assertEquals("FREE_QUEUED", queued.getStatus());
        assertNotNull(queued.getRequestId());
        assertEquals(1, queued.getQueuePosition());
    }

    // ========== 场景 11：penalize 后等窗口滑出可恢复放行 ==========

    @Test
    void testPenalizeRecoversAfterWindowExpires() throws InterruptedException {
        TestableJinaRateLimiter limiter = new TestableJinaRateLimiter(); // 100ms 窗口
        limiter.acquire(false); // 1 个正常请求
        limiter.penalize(false); // 模拟 Jina 429，塞满 20 个时间戳

        // 等真实墙钟 300ms（窗口 100ms 已过期，所有惩罚时间戳已滑出）
        Thread.sleep(300);

        // 现在应该能立即放行
        JinaRateLimiter.AcquireResult r = limiter.acquire(false);
        assertTrue(r.isAcquired());
        assertNull(r.getRequestId(), "惩罚时间戳过期后应能立即放行");
        assertEquals("FREE_IMMEDIATE", r.getStatus());
    }
}
