package org.dbsyncer.parser.service;

import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.sdk.enums.MetaEnum;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.model.Meta;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * ADR-0036 决策 6 回归测试：关闭链路不得清除异常状态。
 *
 * <p>混合驱动关闭顺序是 fullPuller.close() → incrementPuller.close()，
 * 其中 FullPuller.close() 直接调用 resetMetaState。熔断已把 Meta 置为 ERROR，
 * 若无条件重置会把“异常”抹成“未运行”，用户看不到熔断原因。
 */
public class MappingModelServiceResetStateTest {

    private static final String META_ID = "meta-0036";

    /**
     * 持久化次数
     */
    private final AtomicInteger persistCount = new AtomicInteger();

    private MappingModelService service;

    private Mapping mapping;

    private Meta meta;

    @Before
    public void setUp() throws Exception {
        meta = new Meta();
        meta.setId(META_ID);

        mapping = new Mapping();
        mapping.setId("mapping-0036");
        mapping.setMetaId(META_ID);

        service = new MappingModelService();
        java.lang.reflect.Field field = MappingModelService.class.getDeclaredField("profileComponent");
        field.setAccessible(true);
        field.set(service, newProfileComponent());
    }

    /**
     * 熔断（ERROR）后关闭：状态与原因必须保留，且不写一次无意义的持久化。
     */
    @Test
    public void testErrorStateIsPreserved() throws Exception {
        meta.saveState(MetaEnum.ERROR, "熔断：错误计数达到阈值");

        service.resetMetaState(mapping);

        assertTrue("异常状态不得被重置", meta.isError());
        assertEquals("熔断原因必须保留", "熔断：错误计数达到阈值", meta.getErrorMessage());
        assertEquals("异常状态下不应持久化", 0, persistCount.get());
    }

    /**
     * 正常手动停止：状态重置为未运行并持久化。
     */
    @Test
    public void testReadyStateIsReset() throws Exception {
        meta.saveState(MetaEnum.RUNNING, "");

        service.resetMetaState(mapping);

        assertFalse("正常运行态应被重置", meta.isError());
        assertEquals(MetaEnum.READY.getCode(), meta.getState());
        assertEquals("重置后应持久化一次", 1, persistCount.get());
    }

    /**
     * Meta 缺失时不抛异常、不持久化。
     */
    @Test
    public void testMissingMetaIsNoop() throws Exception {
        Mapping unknown = new Mapping();
        unknown.setId("mapping-unknown");
        unknown.setMetaId("meta-unknown");

        service.resetMetaState(unknown);

        assertEquals(0, persistCount.get());
    }

    private ProfileComponent newProfileComponent() {
        return (ProfileComponent) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ProfileComponent.class},
                (proxy, method, args) -> invoke(method.getName(), args));
    }

    private Object invoke(String methodName, Object[] args) {
        if ("getMeta".equals(methodName) && args != null && META_ID.equals(args[0])) {
            return meta;
        }
        if ("editConfigModel".equals(methodName)) {
            persistCount.incrementAndGet();
            return null;
        }
        return null;
    }
}
