package org.dbsyncer.parser.notify;

import org.dbsyncer.common.config.ConfigRegistry;
import org.dbsyncer.common.config.props.DingTalkProps;
import org.dbsyncer.common.config.props.MailProps;
import org.dbsyncer.common.config.props.NotifyProps;
import org.dbsyncer.common.constant.ConfigConstant;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.sdk.notify.NotifyConfigResolver;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

        // 分组未启用独立配置，走全局
        if (!Boolean.TRUE.equals(groupProps.getEnabled())) {
            return globalProps;
        }

        // 按通道合并
        return mergeNotifyProps(groupProps, globalProps);
    }

    /**
     * 字段级合并分组配置和全局配置。
     * <p>
     * 策略：分组显式配置的字段覆盖全局，未配置的字段继承全局。
     * <ul>
     *   <li>channels：取并集，确保全局启用的通道不被分组吞掉</li>
     *   <li>mail/dingtalk：逐字段合并，分组未设的字段（如 excludedNotifyTypes）继承全局</li>
     * </ul>
     */
    private NotifyProps mergeNotifyProps(NotifyProps group, NotifyProps global) {
        NotifyProps merged = new NotifyProps();

        // channels：取并集
        merged.setChannels(mergeChannels(group.getChannels(), global.getChannels()));

        // mail：字段级合并
        merged.setMail(mergeMailProps(group.getMail(), global.getMail()));

        // dingtalk：字段级合并
        merged.setDingtalk(mergeDingTalkProps(group.getDingtalk(), global.getDingtalk()));

        return merged;
    }

    /**
     * 合并通道列表（取并集，保持顺序）
     */
    private List<String> mergeChannels(List<String> group, List<String> global) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (!CollectionUtils.isEmpty(global)) {
            merged.addAll(global);
        }
        if (!CollectionUtils.isEmpty(group)) {
            merged.addAll(group);
        }
        return merged.isEmpty() ? null : new ArrayList<>(merged);
    }

    /**
     * 字段级合并 MailProps：分组非空字段覆盖全局，空字段继承全局。
     */
    private MailProps mergeMailProps(MailProps group, MailProps global) {
        if (group == null) return global;
        if (global == null) return group;

        MailProps merged = new MailProps();
        merged.setUsername(StringUtil.isNotBlank(group.getUsername()) ? group.getUsername() : global.getUsername());
        merged.setPassword(StringUtil.isNotBlank(group.getPassword()) ? group.getPassword() : global.getPassword());
        merged.setExcludedNotifyTypes(
                !CollectionUtils.isEmpty(group.getExcludedNotifyTypes()) ? group.getExcludedNotifyTypes() : global.getExcludedNotifyTypes()
        );
        return merged;
    }

    /**
     * 字段级合并 DingTalkProps：分组非空字段覆盖全局，空字段继承全局。
     */
    private DingTalkProps mergeDingTalkProps(DingTalkProps group, DingTalkProps global) {
        if (group == null) return global;
        if (global == null) return group;

        DingTalkProps merged = new DingTalkProps();
        merged.setWebhookUrl(StringUtil.isNotBlank(group.getWebhookUrl()) ? group.getWebhookUrl() : global.getWebhookUrl());
        merged.setSecret(StringUtil.isNotBlank(group.getSecret()) ? group.getSecret() : global.getSecret());
        merged.setExcludedNotifyTypes(
                !CollectionUtils.isEmpty(group.getExcludedNotifyTypes()) ? group.getExcludedNotifyTypes() : global.getExcludedNotifyTypes()
        );
        return merged;
    }
}
