package org.dbsyncer.common.config;

import org.dbsyncer.common.config.props.BasicProps;
import org.dbsyncer.common.config.props.ServerProps;
import org.dbsyncer.common.model.SystemConfig;
import org.dbsyncer.common.util.JsonUtil;
import org.junit.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * {@link ConfigRegistry} 合并策略单元测试。
 * <p>
 * 覆盖报障场景：不装箱之后 merged 里的 boolean 恒有值，不再出现 null。
 */
public class ConfigRegistryMergeTest {

    private static StandardEnvironment env(String... kvs) {
        StandardEnvironment env = new StandardEnvironment();
        Map<String, Object> map = new HashMap<String, Object>();
        for (int i = 0; i < kvs.length; i += 2) {
            map.put(kvs[i], kvs[i + 1]);
        }
        env.getPropertySources().addFirst(new MapPropertySource("test", map));
        return env;
    }

    /**
     * 模拟存量 DB 行：basic 子对象存在，但缺 enablePrintTraceInfo（NON_DEFAULT 把 false 省掉了）。
     */
    private static SystemConfig dbWithPartialBasic() {
        return JsonUtil.jsonToObj("{\"basic\":{\"expireDataDays\":60}}", SystemConfig.class);
    }

    @Test
    public void booleanInMergedIsNeverNull() {
        ConfigRegistry registry = new ConfigRegistry(env());
        registry.init(dbWithPartialBasic());

        BasicProps basic = registry.getConfig().getBasic();
        assertNotNull(basic);
        // 修复前：装箱 Boolean 拿到 null，对照表打印 final=-
        assertFalse(basic.getEnablePrintTraceInfo());
        assertFalse(basic.getEnableCDN());
        assertTrue(basic.getEnableSchemaResolver());
        // DB 里写了 60 → DB 优先
        assertEquals(60, basic.getExpireDataDays());
        // DB 里没写 → 反序列化落到字段初值
        assertEquals(30, basic.getExpireLogDays());
    }

    @Test
    public void dbWinsOverEnvironment() {
        ConfigRegistry registry = new ConfigRegistry(env(ConfigKeys.BASIC_ENABLE_CDN, "true"));
        registry.init(dbWithPartialBasic());

        // DB 子对象存在 → 整块采纳 DB，props 不再参与（首次迁移之后 DB 就是唯一权威）；
        // DB 里没写 enableCDN，反序列化落到类型默认 false
        assertFalse(registry.getConfig().getBasic().getEnableCDN());

        ConfigRegistry registry2 = new ConfigRegistry(env(ConfigKeys.BASIC_EXPIRE_DATA_DAYS, "99"));
        registry2.init(dbWithPartialBasic());
        assertEquals(60, registry2.getConfig().getBasic().getExpireDataDays());
    }

    @Test
    public void resetTabFallsBackToEnvironment() {
        ConfigRegistry registry = new ConfigRegistry(env(ConfigKeys.BASIC_ENABLE_CDN, "true",
                ConfigKeys.BASIC_ENABLE_PRINT_TRACE_INFO, "true"));
        SystemConfig db = dbWithPartialBasic();
        db.setBasic(null); // resetTab：把 Tab 子对象置 null
        registry.init(db);

        assertTrue(registry.getConfig().getBasic().getEnableCDN());
        assertTrue(registry.getConfig().getBasic().getEnablePrintTraceInfo());
    }

    @Test
    public void environmentOnlyTabWinsEvenWhenDbHasValue() {
        ConfigRegistry registry = new ConfigRegistry(env());
        SystemConfig db = dbWithPartialBasic();
        db.setServer(JsonUtil.jsonToObj("{\"port\":9999}", ServerProps.class));
        registry.init(db);

        // A 类只认 Environment，DB 里就算塞了值也不采纳
        assertEquals(18686, registry.getConfig().getServer().getPort());
    }

    @Test
    public void firstStartupWithoutDbKeepsEnvironmentOnlyTabs() {
        ConfigRegistry registry = new ConfigRegistry(env(ConfigKeys.BASIC_ENABLE_PRINT_TRACE_INFO, "true"));
        registry.init(null);

        // A 类是 @JsonIgnore，走 JSON 往返深拷贝会整个丢掉（修复前 merged.getServer() == null）
        assertNotNull(registry.getConfig().getServer());
        assertEquals(8080, registry.getConfig().getServer().getHttpPort());
        assertTrue(registry.getConfig().getBasic().getEnablePrintTraceInfo());
    }

    @Test
    public void mergedIsDeepCopyAndSharesNoReference() {
        ConfigRegistry registry = new ConfigRegistry(env());
        SystemConfig db = dbWithPartialBasic();
        registry.init(db);

        BasicProps merged = registry.getConfig().getBasic();
        assertNotSame(merged, registry.getDbConfig().getBasic());
        assertNotSame(merged, registry.getPropsConfig().getBasic());

        merged.setExpireDataDays(1);
        assertEquals(60, registry.getDbConfig().getBasic().getExpireDataDays());
        assertEquals(7, registry.getPropsConfig().getBasic().getExpireDataDays());
    }

    /**
     * 完整性守卫：merged 里每个 Tab 都必须非 null，且 SystemConfig 的每个子对象都在 {@link SystemTabEnum} 里登记。
     * 新增 Tab 忘了登记时，合并出来就是 null，运行期表现为 NPE——这条测试替我们挡住。
     */
    @Test
    public void everyTabIsPresentAndNonNull() {
        ConfigRegistry registry = new ConfigRegistry(env());
        registry.init(dbWithPartialBasic());
        SystemConfig merged = registry.getConfig();

        for (SystemTabEnum tab : SystemTabEnum.values()) {
            assertNotNull("Tab " + tab.name() + " 合并后为 null", tab.getProps(merged));
        }
        assertEquals("SystemTabEnum 未覆盖全部子对象", countConfigGetters(), SystemTabEnum.values().length);
    }

    /** SystemConfig 里返回 props 子对象的 getter 数量 */
    private int countConfigGetters() {
        int count = 0;
        for (Method m : SystemConfig.class.getMethods()) {
            if (m.getName().startsWith("get") && m.getParameterTypes().length == 0
                    && m.getReturnType().getName().startsWith("org.dbsyncer.common.config.props")) {
                count++;
            }
        }
        return count;
    }

    @Test
    public void schemaVersionComesFromDb() {
        ConfigRegistry registry = new ConfigRegistry(env());
        SystemConfig db = dbWithPartialBasic();
        db.setSchemaVersion(7);
        registry.init(db);
        assertEquals(7, registry.getConfig().getSchemaVersion());
    }
}
