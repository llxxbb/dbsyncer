package org.dbsyncer.common.config;

import org.dbsyncer.common.config.props.BasicProps;
import org.dbsyncer.common.config.props.BufferActuatorProps;
import org.dbsyncer.common.config.props.BufferProps;
import org.dbsyncer.common.config.props.ErrorProps;
import org.dbsyncer.common.config.props.ExecutorProps;
import org.dbsyncer.common.config.props.MonitorProps;
import org.dbsyncer.common.config.props.RetryProps;
import org.dbsyncer.common.config.props.RuntimeProps;
import org.dbsyncer.common.config.props.ServerProps;
import org.dbsyncer.common.config.props.TemplateProps;
import org.dbsyncer.common.config.props.WorkerProps;
import org.dbsyncer.common.util.JsonUtil;
import org.junit.Test;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.Assert.*;

/**
 * Props 默认值回归测试（ADR-0029 R3）。
 * <p>
 * 不变式：Environment 里<b>没有</b>某个键时，{@code loadFromEnvironment} 必须保持字段初值不变。
 * 早先用两参 {@code getProperty(key, Type)}，key 缺失返回 null 会把初值整个抹掉，
 * 首次迁移写库的种子因此丢掉代码里声明的产品默认值。
 * <p>
 * 用「序列化后逐字段比对」而不是硬编码每个数字：初值改了测试也不会假失败。
 */
public class PropsDefaultValueTest {

    /** 空的 Spring Environment：只有系统属性，不含任何 dbsyncer.* 键 */
    private static final StandardEnvironment EMPTY = new StandardEnvironment();

    private static void assertLoadKeepsDefaults(EnvironmentLoadable target) {
        String before = JsonUtil.objToJson(target);
        target.loadFromEnvironment(EMPTY);
        assertEquals("loadFromEnvironment(空 env) 不应改变任何字段值", before, JsonUtil.objToJson(target));
    }

    @Test
    public void basicKeepsDefaults() {
        assertLoadKeepsDefaults(new BasicProps());
    }

    @Test
    public void serverKeepsDefaults() {
        assertLoadKeepsDefaults(new ServerProps());
    }

    @Test
    public void monitorKeepsDefaults() {
        assertLoadKeepsDefaults(new MonitorProps());
    }

    @Test
    public void retryKeepsDefaults() {
        assertLoadKeepsDefaults(new RetryProps());
    }

    @Test
    public void errorKeepsDefaults() {
        assertLoadKeepsDefaults(new ErrorProps());
    }

    @Test
    public void runtimeKeepsDefaults() {
        assertLoadKeepsDefaults(new RuntimeProps());
    }

    @Test
    public void workerKeepsDefaults() {
        assertLoadKeepsDefaults(new WorkerProps());
    }

    @Test
    public void templateKeepsDefaults() {
        assertLoadKeepsDefaults(new TemplateProps());
    }

    /**
     * 嵌套容器（ExecutorProps / BufferProps）持有的是子对象，loadFromEnvironment 会新建子对象，
     * 所以逐字段比对的不变式不适用，改测"空 env 下子对象取到什么"。
     */
    @Test
    public void nestedPoolAndBufferDefaults() {
        ExecutorProps executor = new ExecutorProps();
        executor.loadFromEnvironment(EMPTY);
        // 例外：每个执行器的默认值不同（含 CPU 核数计算），PoolProps 保持装箱 null，
        // 由 ExecutorFactory.getCore/getMax/getQueue 兜底
        assertNull(executor.getGeneral().getThreadCoreSize());

        BufferProps buffer = new BufferProps();
        buffer.loadFromEnvironment(EMPTY);
        assertEquals(100, buffer.getGeneral().getBufferWriterCount());
        assertEquals(1000, buffer.getGeneral().getBufferPullCount());
        assertEquals(30000, buffer.getGeneral().getBufferQueueCapacity());
        assertEquals(300, buffer.getGeneral().getBufferPeriodMillisecond());
    }
}
