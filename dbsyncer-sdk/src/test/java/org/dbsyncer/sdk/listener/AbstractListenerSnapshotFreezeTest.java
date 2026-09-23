package org.dbsyncer.sdk.listener;

import org.dbsyncer.sdk.connector.ConnectorInstance;
import org.dbsyncer.sdk.model.ChangedOffset;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 监听器关闭后冻结快照点推进（ADR-0036 决策 5）
 * <p>
 * 覆盖两个不变量：
 * <ul>
 *     <li>关闭后 refreshEvent 空操作，断开窗口内到达的事件不会把续读点推过未落库的数据</li>
 *     <li>关闭后持久化线程终止；快照点未变化时不重复写入</li>
 * </ul>
 *
 * @version 1.0.0
 */
public class AbstractListenerSnapshotFreezeTest {

    private static final String POSITION = "position";

    /**
     * 关闭后 refreshEvent 被忽略：模拟 disconnect 窗口内仍在投递的事件
     */
    @Test
    public void testRefreshEventAfterCloseIsIgnored() throws Exception {
        FakeWatcher watcher = new FakeWatcher();
        TestListener listener = newListener(watcher, 100);

        // 先让快照点前进并写入一次（position 100 → 200）
        listener.setSnapshot(snapshot(200));
        listener.refreshEvent(offset(200));
        invokeFlush(listener);
        Assert.assertEquals(1, watcher.flushed.size());

        // 关闭
        listener.close();

        // 关闭后源库仍在推送，快照点被推到 999
        listener.setSnapshot(snapshot(999));
        listener.refreshEvent(offset(999));
        invokeFlush(listener);

        Assert.assertEquals("关闭后不得推进快照点", 1, watcher.flushed.size());
        Assert.assertEquals("200", watcher.lastFlushedPosition());
    }

    /**
     * 关闭后持久化线程已终止
     */
    @Test
    public void testFlushExecutorShutdownAfterClose() throws Exception {
        TestListener listener = newListener(new FakeWatcher(), 100);

        listener.close();

        ScheduledExecutorService executor = readFlushExecutor(listener);
        Assert.assertTrue("持久化线程池应已关闭", executor.isShutdown());
        Assert.assertTrue("持久化线程应已退出", executor.isTerminated());
    }

    /**
     * 快照点未变化时不重复写入
     */
    @Test
    public void testUnchangedSnapshotIsNotRewritten() throws Exception {
        FakeWatcher watcher = new FakeWatcher();
        TestListener listener = newListener(watcher, 100);

        listener.refreshEvent(offset(100));
        invokeFlush(listener);
        invokeFlush(listener);

        Assert.assertEquals("快照点未变化不应写入", 0, watcher.flushed.size());
    }

    private TestListener newListener(FakeWatcher watcher, int position) {
        TestListener listener = new TestListener();
        listener.setMetaId("meta-snapshot-freeze-test");
        listener.setSnapshot(snapshot(position));
        listener.register(watcher);
        listener.init();
        return listener;
    }

    private Map<String, String> snapshot(int position) {
        Map<String, String> snapshot = new HashMap<>(4);
        snapshot.put("fileName", "binlog.000001");
        snapshot.put(POSITION, String.valueOf(position));
        return snapshot;
    }

    private ChangedOffset offset(int position) {
        ChangedOffset offset = new ChangedOffset();
        offset.setMetaId("meta-snapshot-freeze-test");
        offset.setNextFileName("binlog.000001");
        offset.setPosition(position);
        return offset;
    }

    private void invokeFlush(AbstractListener<?> listener) throws Exception {
        Method method = AbstractListener.class.getDeclaredMethod("flushPendingSnapshot");
        method.setAccessible(true);
        method.invoke(listener);
    }

    private ScheduledExecutorService readFlushExecutor(AbstractListener<?> listener) throws Exception {
        Field field = AbstractListener.class.getDeclaredField("flushExecutor");
        field.setAccessible(true);
        return (ScheduledExecutorService) field.get(listener);
    }

    /**
     * 记录快照点写入次数的 Watcher
     */
    private static class FakeWatcher implements Watcher {

        private final List<Map<String, String>> flushed = new ArrayList<>();

        @Override
        public void changeEventBefore(QuartzListenerContext context) {
        }

        @Override
        public void changeEvent(ChangedEvent event) {
        }

        @Override
        public void flushEvent(Map<String, String> snapshot) {
            flushed.add(new HashMap<>(snapshot));
        }

        @Override
        public void errorEvent(Exception e) {
        }

        @Override
        public long getMetaUpdateTime() {
            return 0L;
        }

        @Override
        public boolean hasPendingTask() {
            return false;
        }

        private String lastFlushedPosition() {
            return flushed.get(flushed.size() - 1).get(POSITION);
        }
    }

    /**
     * 测试用监听器：重写 close() 避免依赖 staticLogService
     */
    private static class TestListener extends AbstractListener<ConnectorInstance> {

        @Override
        public void start() {
        }

        @Override
        public void close() {
            // 跳过 staticLogService.log，只测试父类的关闭逻辑
            try {
                java.lang.reflect.Field closedField = AbstractListener.class.getDeclaredField("closed");
                closedField.setAccessible(true);
                closedField.setBoolean(this, true);
                java.lang.reflect.Method method = AbstractListener.class.getDeclaredMethod("shutdownFlushExecutor");
                method.setAccessible(true);
                method.invoke(this);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
