package org.dbsyncer.parser.util;

import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.ParserException;
import org.dbsyncer.parser.convert.Handler;
import org.dbsyncer.parser.enums.ConvertEnum;
import org.dbsyncer.parser.model.Convert;
import org.dbsyncer.parser.model.ParseResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class ConvertUtil {

    private static final String ROOT_CONVERT_KEY = "__ROOT_CONVERT__";

    private ConvertUtil() {
    }

    /**
     * 转换参数（批量处理）
     */
    public static void convert(List<Convert> convert, List<Map> data) {
        if (!CollectionUtils.isEmpty(convert) && !CollectionUtils.isEmpty(data)) {
            data.forEach(row -> convert(convert, row));
        }
    }

    /**
     * 转换参数
     * <p>
     * 前置条件：配置已通过 validateFieldConverterRule 验证
     */
    public static void convert(List<Convert> converts, Map row) {
        if (CollectionUtils.isEmpty(converts) || row == null) {
            return;
        }

        // 按字段分组处理
        Map<String, List<Convert>> fieldConverts = converts.stream()
                .collect(Collectors.groupingBy(Convert::getName));

        // 3. 对每个字段，调用其根转换器
        for (Map.Entry<String, List<Convert>> entry : fieldConverts.entrySet()) {
            String fieldName = entry.getKey();
            List<Convert> fieldConvs = entry.getValue();

            // 获取该字段的根转换器
            Convert root = fieldConvs.stream()
                    .filter(Convert::isRoot)
                    .findFirst()
                    .orElse(null);

            assert root != null;

            // 创建 context，传入该字段的所有转换器
            Map<String, Object> context = new HashMap<>();
            context.put(ROOT_CONVERT_KEY, root);

            String code = root.getConvertCode();
            if (StringUtil.isBlank(code)) {
                continue;
            }

            ConvertEnum convertEnum;
            try {
                convertEnum = ConvertEnum.valueOf(code);
            } catch (Exception e) {
                // 无效的转换代码，跳过
                continue;
            }

            String args = root.getArgs();
            Handler handler = convertEnum.getHandler();

            Object value = row.get(fieldName);
            value = handler.handle(args, value, row, context, converts);
            row.put(fieldName, value);
        }
    }

    /**
     * 验证字段转换器规则并预解析模板
     * <p>
     * 规则：一个字段可以有0个或多个转换器，如果有多个转换器，则必须有且只有一个根转换器。
     * <p>
     * 注意：此方法应在 TableGroup 保存或编辑时调用，而不是在运行时调用。
     *
     * @param converts 转换器列表
     * @throws ParserException 如果验证失败
     */
    public static void validateFieldConverterRule(List<Convert> converts) {
        if (CollectionUtils.isEmpty(converts)) {
            return;
        }

        // 1. 清除缓存并按字段分组
        Map<String, List<Convert>> fieldConverters = new HashMap<>();
        for (Convert convert : converts) {
            convert.setParseResultCache(null);
            fieldConverters.computeIfAbsent(convert.getName(), k -> new ArrayList<>()).add(convert);
        }

        // 2. 验证每个字段有且仅有一个根转换器，并预解析模板
        TemplateParser parser = new TemplateParser();
        for (List<Convert> fieldConvs : fieldConverters.values()) {
            List<Convert> roots = fieldConvs.stream().filter(Convert::isRoot).collect(Collectors.toList());
            Convert root = fieldConvs.get(0);
            if (CollectionUtils.isEmpty(roots) && fieldConvs.size() == 1) {
                root.setRoot(true);
            } else {
                assert roots.size() == 1;
                root = roots.get(0);
            }

            if (ConvertEnum.TEMPLATE.getCode().equals(root.getConvertCode())) {
                ParseResult parseResult = parser.parseTemplate(root.getArgs(), root, converts);
                root.setParseResultCache(parseResult);
            }
        }
    }

    /**
     * 从 context 中获取根转换器
     */
    public static Convert getRootConvert(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object root = context.get(ROOT_CONVERT_KEY);
        if (root instanceof Convert) {
            return (Convert) root;
        }
        return null;
    }
}
