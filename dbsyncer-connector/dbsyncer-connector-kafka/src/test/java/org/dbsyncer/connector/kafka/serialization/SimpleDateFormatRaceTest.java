package org.dbsyncer.connector.kafka.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.Test;

import java.io.IOException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 复现 MapToJsonSerializer 中 SimpleDateFormat 线程安全问题。
 *
 * 现象：多线程并发序列化 java.sql.Date 时，SimpleDateFormat 的共享 Calendar
 * 被其他线程的 setTime() 覆盖，导致日期值泄漏。
 *
 * 期望输出：
 *   {"date":"2026-03-12"}  ← 正确
 *   {"date":"2026-04-29"}  ← 正确
 *   {"date":"2026-03-29"}  ← 错误！线程A的月份被线程B的月份覆盖
 *   {"date":"2026-04-12"}  ← 错误！线程B的月份被线程A的月份覆盖
 *   {"date":"2025-04-29"}  ← 错误！线程A的年份被线程B的年份覆盖
 */
public class SimpleDateFormatRaceTest {

    // ============================================================
    // 有 bug 的版本：static SimpleDateFormat（非线程安全）
    // ============================================================
    private static final SimpleDateFormat BUGGY_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private static class BuggySerializer extends JsonSerializer<java.sql.Date> {
        @Override
        public void serialize(java.sql.Date value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(BUGGY_FORMAT.format(value));
        }
    }

    // ============================================================
    // 修复版本：DateTimeFormatter（线程安全）
    // ============================================================
    private static final java.time.format.DateTimeFormatter FIXED_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static class FixedSerializer extends JsonSerializer<java.sql.Date> {
        @Override
        public void serialize(java.sql.Date value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(value.toLocalDate().format(FIXED_FORMAT));
        }
    }

    // ============================================================
    // 测试：复现 SimpleDateFormat 线程安全问题
    // ============================================================
    @Test
    public void testSimpleDateFormatRaceCondition() throws Exception {
        // 准备两个固定的测试日期
        Date dateA = Date.valueOf(LocalDate.of(2026, 3, 12));  // 2026-03-12
        Date dateB = Date.valueOf(LocalDate.of(2026, 4, 29));  // 2026-04-29

        ObjectMapper buggyMapper = new ObjectMapper();
        buggyMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        SimpleModule buggyModule = new SimpleModule();
        buggyModule.addSerializer(Date.class, new BuggySerializer());
        buggyMapper.registerModule(buggyModule);

        int iterations = 100_000;
        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        List<String> expectedA = Collections.singletonList("{\"date\":\"2026-03-12\"}");
        List<String> expectedB = Collections.singletonList("{\"date\":\"2026-04-29\"}");

        AtomicInteger errorCountA = new AtomicInteger(0);
        AtomicInteger errorCountB = new AtomicInteger(0);
        AtomicInteger totalErrorCount = new AtomicInteger(0);

        // 用 Set 收集所有出现的异常值，用于分析模式
        Set<String> corruptedValuesA = ConcurrentHashMap.newKeySet();
        Set<String> corruptedValuesB = ConcurrentHashMap.newKeySet();

        CountDownLatch latch = new CountDownLatch(iterations);

        for (int i = 0; i < iterations; i++) {
            executor.submit(() -> {
                try {
                    // 线程交替序列化 dateA 和 dateB
                    Map<String, Object> mapA = new HashMap<>();
                    mapA.put("date", dateA);
                    String jsonA = buggyMapper.writeValueAsString(mapA);

                    Map<String, Object> mapB = new HashMap<>();
                    mapB.put("date", dateB);
                    String jsonB = buggyMapper.writeValueAsString(mapB);

                    if (!expectedA.contains(jsonA)) {
                        errorCountA.incrementAndGet();
                        corruptedValuesA.add(jsonA);
                    }
                    if (!expectedB.contains(jsonB)) {
                        errorCountB.incrementAndGet();
                        corruptedValuesB.add(jsonB);
                    }
                    if (!expectedA.contains(jsonA) || !expectedB.contains(jsonB)) {
                        totalErrorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    totalErrorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // ========== 输出结果 ==========
        System.out.println("\n======== SimpleDateFormat 线程安全测试 ========");
        System.out.println("迭代次数: " + iterations);
        System.out.println("线程数: " + threads);
        System.out.println("dateA 错误次数: " + errorCountA.get()
                + " (期望 2026-03-12)");
        System.out.println("dateB 错误次数: " + errorCountB.get()
                + " (期望 2026-04-29)");
        System.out.println("总异常轮次: " + totalErrorCount.get());
        System.out.println("\n--- dateA 被污染后的值（Top 10） ---");
        corruptedValuesA.stream().limit(10).forEach(v ->
                System.out.println("  " + v));
        System.out.println("\n--- dateB 被污染后的值（Top 10） ---");
        corruptedValuesB.stream().limit(10).forEach(v ->
                System.out.println("  " + v));

        if (totalErrorCount.get() > 0) {
            System.out.println("\n✅ 成功复现 SimpleDateFormat 线程安全问题！");
        } else {
            System.out.println("\n⚠️ 本次未触发竞态（可增大 threads/iterations 重试）");
        }
        System.out.println("================================================\n");
    }

    // ============================================================
    // 对比测试：修复版本应完全正确
    // ============================================================
    @Test
    public void testFixedSerializer_NoRaceCondition() throws Exception {
        Date dateA = Date.valueOf(LocalDate.of(2026, 3, 12));
        Date dateB = Date.valueOf(LocalDate.of(2026, 4, 29));

        ObjectMapper fixedMapper = new ObjectMapper();
        fixedMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        SimpleModule fixedModule = new SimpleModule();
        fixedModule.addSerializer(Date.class, new FixedSerializer());
        fixedMapper.registerModule(fixedModule);

        int iterations = 100_000;
        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(iterations);

        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < iterations; i++) {
            executor.submit(() -> {
                try {
                    Map<String, Object> mapA = new HashMap<>();
                    mapA.put("date", dateA);
                    String jsonA = fixedMapper.writeValueAsString(mapA);

                    Map<String, Object> mapB = new HashMap<>();
                    mapB.put("date", dateB);
                    String jsonB = fixedMapper.writeValueAsString(mapB);

                    if (!"{\"date\":\"2026-03-12\"}".equals(jsonA) ||
                        !"{\"date\":\"2026-04-29\"}".equals(jsonB)) {
                        errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("\n======== 修复版本测试 ========");
        System.out.println("迭代次数: " + iterations);
        System.out.println("线程数: " + threads);
        System.out.println("错误次数: " + errorCount.get());

        if (errorCount.get() == 0) {
            System.out.println("✅ 修复版本无竞态，测试通过！");
        } else {
            System.out.println("❌ 修复版本仍有错误！");
        }
        System.out.println("==============================\n");
    }
}
