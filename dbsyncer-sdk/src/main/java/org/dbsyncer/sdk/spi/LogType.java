package org.dbsyncer.sdk.spi;

/**
 * 统一日志类型枚举
 * 
 * 设计思路：
 * - 分类由日志表中的 model_type 字段区分
 * 
 * @author AE86
 * @version 2.0.0
 * @date 2020/04/21 -> 2026/06/24
 */
public enum LogType {
    // 常规
    INSERT("新增"),
    UPDATE("修改"),
    DELETE("删除"),
    COPY("复制"),

    // 任务
    CONFIG("配置"),
    INIT("初始化"),
    START("启动"),
    STOP("停止"),
    CLEAR("清空"),
    RESET("重置"),
    CONNECT("建立连接"),
    SYNC("同步"),


    UPLOAD("上传"),
    ACTIVATE("激活"),
    
    IMPORT("导入"),
    EXPORT("导出"), 
;

    private final String type;

    LogType(String type) {
        this.type = type;
    }

    /**
     * 获取日志类型
     */
    public String getType() {
        return type;
    }
}
