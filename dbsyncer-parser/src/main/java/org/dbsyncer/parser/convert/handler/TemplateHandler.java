package org.dbsyncer.parser.convert.handler;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.convert.Handler;
import org.dbsyncer.sdk.model.Convert;
import org.dbsyncer.sdk.model.ParseResult;
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
 * 2. 解析模板表达式（入口调用可复用缓存，递归调用需重新解析）
 * 3. 执行模板
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

        // 2. 解析模板表达式
        //    缓存命中条件：originalExpression 等于当前 args
        //    缓存更新：只在入口调用时（args == root.getArgs()）
        ParseResult parseResult = root.getParseResultCache();
        if (parseResult == null || !args.equals(parseResult.getOriginalExpression())) {
            parseResult = parser.parseTemplate(args, root, converts);
            if (args.equals(root.getArgs())) {
                root.setParseResultCache(parseResult);
            }
        }

        // 3. 执行模板
        return TemplateExecutor.run(parseResult, sourceRow, context);
    }
}
