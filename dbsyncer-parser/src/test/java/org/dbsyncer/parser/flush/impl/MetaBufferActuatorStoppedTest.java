package org.dbsyncer.parser.flush.impl;

import org.dbsyncer.common.config.props.BufferProps;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.model.WriterRequest;
import org.dbsyncer.parser.model.WriterResponse;
import org.dbsyncer.sdk.enums.ChangedEventTypeEnum;
import org.dbsyncer.sdk.enums.MetaEnum;
import org.dbsyncer.sdk.listener.ChangedEvent;
import org.dbsyncer.sdk.listener.Listener;
import org.dbsyncer.sdk.listener.QuartzListenerContext;
import org.dbsyncer.sdk.listener.Watcher;
import org.dbsyncer.sdk.listener.event.RowChangedEvent;
import org.dbsyncer.sdk.model.ChangedOffset;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.model.Meta;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 任务停止后立即停止出队（ADR-0036 决策 2）
 * <p>
 * 核心不变量：非运行态立即停止出队，残留事件留在队列（等待重启后由源库重放）
 *
 * @version 1.0.0
 */
public class MetaBufferActuatorStoppedTest {

    private static final String META_ID = "meta-stopped-test";
    private static final String TABLE_NAME = "test_converter_from";

    /**
     * 任务停止后立即停止出队，残留事件保留在队列中
     */
    @Test
    public void testDequeueKeepsResidueWhenStopped() throws Exception {
        FakeListener listener = new FakeListener();
        MetaBufferActuator actuator = newActuator(stoppedMeta(listener));
        BlockingQueue<WriterRequest> queue = queueWithResidue(2);
        setField(actuator, "queue", queue);

        invoke(actuator, "dequeue", new Class[]{});

        Assert.assertEquals("残留事件应留在队列，等待重启后重放", 2, queue.size());
        Assert.assertEquals(0, listener.refreshCount);
    }

    // ===== 测试脚手架 =====

    private MetaBufferActuator newActuator(Meta meta) {
        // 覆写配置读取：单测不加载 ConfigRegistry，批次参数用默认值
        MetaBufferActuator actuator = new MetaBufferActuator() {
            @Override
            protected BufferProps getBufferProps() {
                return new BufferProps();
            }
        };
        actuator.setMetaId(META_ID);
        actuator.setProfileComponent(newProfileComponent(meta));
        return actuator;
    }

    /**
     * 熔断后的 Meta：状态为异常，仍绑定监听器（假终止现场）
     */
    private Meta stoppedMeta(Listener listener) {
        Meta meta = new Meta();
        meta.setId(META_ID);
        meta.saveState(MetaEnum.ERROR, "目标库连接失败，触发熔断");
        meta.setListener(listener);
        return meta;
    }

    private ProfileComponent newProfileComponent(Meta meta) {
        Mapping mapping = new Mapping();
        mapping.setId("mapping-stopped-test");
        mapping.setMetaId(META_ID);
        InvocationHandler handler = (proxy, method, args) -> {
            if ("getMeta".equals(method.getName())) {
                return meta;
            }
            if ("getMapping".equals(method.getName())) {
                return mapping;
            }
            return null;
        };
        return (ProfileComponent) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{ProfileComponent.class}, handler);
    }

    private WriterResponse newResponse(int position) {
        WriterResponse response = new WriterResponse();
        response.setTableName(TABLE_NAME);
        response.setTypeEnum(ChangedEventTypeEnum.ROW);
        response.setChangedOffset(newOffset(position));
        return response;
    }

    private BlockingQueue<WriterRequest> queueWithResidue(int size) {
        BlockingQueue<WriterRequest> queue = new LinkedBlockingQueue<>(100);
        for (int i = 1; i <= size; i++) {
            queue.offer(new WriterRequest(newEvent(i * 100)));
        }
        return queue;
    }

    private ChangedEvent newEvent(int position) {
        List<Object> row = new ArrayList<>(Arrays.asList(1, "name-" + position));
        List<String> columnNames = new ArrayList<>(Arrays.asList("id", "name"));
        return new RowChangedEvent(TABLE_NAME, "INSERT", row, "binlog.000001", position, columnNames);
    }

    private ChangedOffset newOffset(int position) {
        ChangedOffset offset = new ChangedOffset();
        offset.setMetaId(META_ID);
        offset.setNextFileName("binlog.000001");
        offset.setPosition(position);
        return offset;
    }

    private void invoke(MetaBufferActuator actuator, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = MetaBufferActuator.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        try {
            method.invoke(actuator, args);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    private void setField(MetaBufferActuator actuator, String name, Object value) throws Exception {
        Field field = MetaBufferActuator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(actuator, value);
    }

    /**
     * 记录快照点推进次数的监听器
     */
    private static class FakeListener implements Listener {

        private int refreshCount = 0;

        @Override
        public void start() {
        }

        @Override
        public void close() {
        }

        @Override
        public void register(Watcher watcher) {
        }

        @Override
        public void changeEventBefore(QuartzListenerContext context) {
        }

        @Override
        public void changeEvent(ChangedEvent event) {
        }

        @Override
        public void refreshEvent(ChangedOffset offset) {
            refreshCount++;
        }

        @Override
        public void errorEvent(Exception e) {
        }
    }
}
