package org.dbsyncer.parser.convert.handler;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.convert.Handler;
import org.dbsyncer.parser.model.Convert;
import org.dbsyncer.parser.util.TemplateExecutor;

import java.util.List;
import java.util.Map;

/**
 * 模版处理器
 */
public class TemplateHandler implements Handler {

    @Override
    public Object handle(String args, Object value, Map<String, Object> sourceRow, Map<String, Object> context, List<Convert> converts) {
        if (StringUtil.isBlank(args) || sourceRow == null) {
            return null;
        }
        
        // 使用 TemplateExecutor 执行表达式
        TemplateExecutor executor = new TemplateExecutor();
        return executor.execute(args, sourceRow, context, converts);
    }
}

