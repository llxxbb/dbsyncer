package org.dbsyncer.parser.notify;

import org.dbsyncer.common.config.ConfigRegistry;
import org.dbsyncer.common.config.props.NotifyProps;
import org.dbsyncer.common.constant.ConfigConstant;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.sdk.notify.NotifyConfigResolver;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 通知配置解析器实现
 * <p>
 * ADR-0035：根据 mappingId 解析分组配置，按通道合并，无分组配置时走全局兜底
 */
@Component
public class NotifyConfigResolverImpl implements NotifyConfigResolver {

    @Resource
    private ConfigRegistry configRegistry;

    @Resource
    private ProfileComponent profileComponent;

    @Override
    public NotifyProps resolve(String mappingId) {
        NotifyProps globalProps = configRegistry.getConfig().getNotify();
        if (globalProps == null) {
            return null;
        }

        Map<String, NotifyProps> groupConfigs = configRegistry.getConfig().getGroupNotifyConfigs();
        if (groupConfigs == null || groupConfigs.isEmpty()) {
            return globalProps;
        }

        // 确定 key
        String key = ConfigConstant.UNASSIGNED_GROUP_KEY;
        if (StringUtil.isNotBlank(mappingId)) {
            String groupId = profileComponent.findProjectGroupId(mappingId);
            if (StringUtil.isNotBlank(groupId)) {
                key = groupId;
            }
        }

        NotifyProps groupProps = groupConfigs.get(key);
        if (groupProps == null) {
            return globalProps;
        }

        // 按通道合并
        return mergeNotifyProps(groupProps, globalProps);
    }

    /**
     * 按通道合并分组配置和全局配置
     */
    private NotifyProps mergeNotifyProps(NotifyProps group, NotifyProps global) {
        NotifyProps merged = new NotifyProps();
        // channels：分组覆盖全局
        merged.setChannels(group.getChannels() != null ? group.getChannels() : global.getChannels());
        // mail：分组有则用分组
        merged.setMail(group.getMail() != null ? group.getMail() : global.getMail());
        // dingtalk：分组有则用分组
        merged.setDingtalk(group.getDingtalk() != null ? group.getDingtalk() : global.getDingtalk());
        return merged;
    }
}
