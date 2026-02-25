package org.dbsyncer.parser.convert.handler;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.convert.Handler;
import org.dbsyncer.parser.model.Convert;
import org.dbsyncer.parser.model.ParseResult;
import org.dbsyncer.parser.util.ConvertUtil;
import org.dbsyncer.parser.util.TemplateExecutor;
import org.dbsyncer.parser.util.TemplateParser;

import java.util.List;
import java.util.Map;

/**
 * 模版处理器
 * <p>
 * 处理流程：
 * 1. 从 context 中获取根转换器（由 ConvertUtil 放入）
 * 2. 使用预解析的缓存结果执行模板（如有缓存）
 * 3. 无缓存时即时解析并执行
 * <p>
 * 注意：预解析在 validateFieldConverterRule 中完成，可提升性能
 */
public class TemplateHandler implements Handler {

    private final TemplateParser parser = new TemplateParser();

    @Override
    public Object handle(String args, Object value, Map<String, Object> sourceRow,
                         Map<String, Object> context, List<Convert> converts) {
        if (StringUtil.isBlank(args) || sourceRow == null) {
            return null;
        }

        // 1. 从 context 中获取根转换器
        Convert root = ConvertUtil.getRootConvert(context);
        assert root != null;

        // 2. 获取或创建解析结果
        ParseResult parseResult = root.getParseResultCache();
        assert parseResult != null;
        // 缓存未命中，即时解析
        parseResult = parser.parseTemplate(args, root, converts);

        // 3. 执行模板
        return TemplateExecutor.run(parseResult, sourceRow, context);
    }
}

