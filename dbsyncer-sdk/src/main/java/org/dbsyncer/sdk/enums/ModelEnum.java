/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.sdk.enums;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.sdk.SdkException;

/**
 * 驱动同步方式枚举
 *
 * @Author AE86
 * @Version 1.0.0
 * @Date 2023-11-16 00:51
 */
public enum ModelEnum {

    FULL("full", "全量"),
    INCREMENT("increment", "增量"),
    FULL_INCREMENT("fullIncrement", "混合");

    private final String code;
    private final String name;

    private static final java.util.Map<String, ModelEnum> CODE_NAME_MAP = new java.util.HashMap<>();

    static {
        for (ModelEnum e : ModelEnum.values()) {
            CODE_NAME_MAP.put(e.code, e);
        }
    }

    ModelEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static ModelEnum getModelEnum(String code) throws SdkException {
        ModelEnum e = CODE_NAME_MAP.get(code);
        if (e != null) return e;
        throw new SdkException(String.format("Model code \"%s\" does not exist.", code));
    }

    public static boolean isFull(String model) {
        return StringUtil.equals(FULL.getCode(), model);
    }
    
    public static String getModelName(String code) {
        ModelEnum e = CODE_NAME_MAP.get(code);
        return e != null ? e.name : code;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}