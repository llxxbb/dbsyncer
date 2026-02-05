package org.dbsyncer.parser.util;

import org.dbsyncer.parser.model.ParseResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模板解析结果缓存
 */
public class TemplateCache {

    private static final TemplateCache INSTANCE = new TemplateCache();
    private static final int MAX_CACHE_SIZE = 1000;

    private final Map<String, ParseResult> cache = new ConcurrentHashMap<>();

    private TemplateCache() {
    }

    public static TemplateCache getInstance() {
        return INSTANCE;
    }

    public ParseResult getParseResult(String key) {
        return cache.get(key);
    }

    public void putParseResult(String key, ParseResult result) {
        if (cache.size() >= MAX_CACHE_SIZE) {
            int removeCount = cache.size() / 2;
            int count = 0;
            for (String k : cache.keySet()) {
                if (count >= removeCount) {
                    break;
                }
                cache.remove(k);
                count++;
            }
        }
        cache.put(key, result);
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }
}
