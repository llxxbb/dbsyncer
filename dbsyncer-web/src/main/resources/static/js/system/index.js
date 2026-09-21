// ADR-0029: 6-Tab 系统参数页面

var tabsData = {};

function loadTabs() {
    doGetWithoutLoading('/system/tabs', {}, function (data) {
        if (data.success) {
            tabsData = data.resultValue || data.result;
            fillBasicTab(tabsData.basic);
            fillExecutorsTab(tabsData.executors);
            fillBuffersTab(tabsData.buffers);
            fillRetryTab(tabsData.retry);
            fillNotifyTab(tabsData.notify);
            fillErrorTab(tabsData.error);
            fillMonitorTab(tabsData.monitor);
            fillTemplateTab(tabsData.template);
            fillSqlServerCTTab(tabsData["sqlserver_ct"]);
        }
    });
}

function fillBasicTab(basic) {
    if (!basic) return;
    $('#basic_expireDataDays').val(basic.expireDataDays || 7);
    $('#basic_expireLogDays').val(basic.expireLogDays || 30);
    $('#basic_refreshIntervalSeconds').val(basic.refreshIntervalSeconds || 5);
    $('#basic_enableStorageWriteFail').prop('checked', basic.enableStorageWriteFail !== false);
    $('#basic_maxStorageErrorLength').val(basic.maxStorageErrorLength || 2048);
    $('#basic_enableCDN').prop('checked', basic.enableCDN === true);
    $('#basic_enableSchemaResolver').prop('checked', basic.enableSchemaResolver !== false);
    $('#basic_enableWatermark').prop('checked', basic.enableWatermark === true);
    $('#basic_watermark').val(basic.watermark || '');
    $('#basic_enablePrintTraceInfo').prop('checked', basic.enablePrintTraceInfo === true);
    $('#basic_resetPwd').prop('checked', basic.resetPwd === true);
    toggleWatermark();
    toggleStorageErrorLength();
}

function fillTemplateTab(template) {
    if (!template) return;
    $('#template_maxDepth').val(template.maxDepth);
}

function fillSqlServerCTTab(ct) {
    if (!ct) return;
    $('#sqlserverCT_pollIntervalMs').val(ct.pollIntervalMs);
    $('#sqlserverCT_snapshotTimeIntervalMs').val(ct.snapshotTimeIntervalMs);
}

function fillExecutorsTab(exec) {
    if (!exec) return;
    if (exec.general) {
        $('#exec_general_threadCoreSize').val(exec.general.threadCoreSize);
        $('#exec_general_maxThreadSize').val(exec.general.maxThreadSize);
        $('#exec_general_threadQueueCapacity').val(exec.general.threadQueueCapacity);
    }
    if (exec.storage) {
        $('#exec_storage_threadCoreSize').val(exec.storage.threadCoreSize);
        $('#exec_storage_maxThreadSize').val(exec.storage.maxThreadSize);
        $('#exec_storage_threadQueueCapacity').val(exec.storage.threadQueueCapacity);
    }
    if (exec.dispatch) {
        $('#exec_dispatch_threadCoreSize').val(exec.dispatch.threadCoreSize);
        $('#exec_dispatch_maxThreadSize').val(exec.dispatch.maxThreadSize);
        $('#exec_dispatch_threadQueueCapacity').val(exec.dispatch.threadQueueCapacity);
    }
}

function fillBuffersTab(buf) {
    if (!buf) return;
    if (buf) {
        $('#buf_general_bufferPullCount').val(buf.bufferPullCount);
        $('#buf_general_bufferQueueCapacity').val(buf.bufferQueueCapacity);
        $('#buf_general_bufferPeriodMillisecond').val(buf.bufferPeriodMillisecond);
    }
}

function fillRetryTab(retry) {
    if (!retry) return;
    $('#retry_disable').prop('checked', retry.disable === true);
    $('#retry_maxAttempts').val(retry.maxAttempts);
    $('#retry_initialIntervalMs').val(retry.initialIntervalMs);
    $('#retry_maxIntervalMs').val(retry.maxIntervalMs);
    $('#retry_multiplier').val(retry.multiplier);
    $('#retry_maxDurationMs').val(retry.maxDurationMs);
    $('#retry_terminationMode').val(retry.terminationMode || 'WHICHEVER_FIRST');
    $('#retry_useKeyword').prop('checked', retry.useKeyword === true);
    $('#retry_matchMode').val(retry.matchMode || 'CONTAINS_IGNORE_CASE');
    $('#retry_keywords').val((retry.keywords || []).join('\n'));
    $('#retry_excludeKeywords').val((retry.excludeKeywords || []).join('\n'));
}

function fillNotifyTab(notify) {
    if (!notify) return;
    // channels
    $('input[name="channels"]').prop('checked', false);
    if (notify.channels) {
        notify.channels.forEach(function (ch) {
            $('input[name="channels"][value="' + ch + '"]').prop('checked', true);
        });
    }
    // mail
    if (notify.mail) {
        $('#notify_mail_username').val(notify.mail.username || '');
        // 密码：如果是掩码值，显示在 placeholder 中，value 留空
        var pwd = notify.mail.password;
        if (pwd && pwd.indexOf('****') >= 0) {
            $('#notify_mail_password').attr('placeholder', '已配置 (' + pwd + ')，留空则保留原值');
            $('#notify_mail_password').val('');
        } else {
            $('#notify_mail_password').attr('placeholder', '未配置，请输入');
            $('#notify_mail_password').val('');
        }
    }
    // dingtalk
    if (notify.dingtalk) {
        $('#notify_dingtalk_webhookUrl').val(notify.dingtalk.webhookUrl || '');
        // 密钥：如果是掩码值，显示在 placeholder 中，value 留空
        var secret = notify.dingtalk.secret;
        if (secret && secret.indexOf('****') >= 0) {
            $('#notify_dingtalk_secret').attr('placeholder', '已配置 (' + secret + ')，留空则保留原值');
            $('#notify_dingtalk_secret').val('');
        } else {
            $('#notify_dingtalk_secret').attr('placeholder', '未配置，请输入');
            $('#notify_dingtalk_secret').val('');
        }
    }
}

function fillErrorTab(error) {
    if (!error) return;
    $('#error_threshold').val(error.threshold);
    $('#error_fatalMatchMode').val(error.fatalMatchMode || 'CONTAINS_IGNORE_CASE');
    $('#error_fatalKeywords').val((error.fatalKeywords || []).join('\n'));
}

function fillMonitorTab(monitor) {
    if (!monitor) return;
    $('#monitor_schedulerPoolSize').val(monitor.schedulerPoolSize || 8);
    $('#monitor_cpuMemoryIntervalMs').val(monitor.cpuMemoryIntervalMs || 5000);
    $('#monitor_healthCheckIntervalMs').val(monitor.healthCheckIntervalMs || 10000);
    $('#monitor_cleanupIntervalMs').val(monitor.cleanupIntervalMs || 30000);
    $('#monitor_metricReportIntervalMs').val(monitor.metricReportIntervalMs || 5000);
    $('#monitor_alertCron').val(monitor.alertCron || '0 */10 * * * ?');
}

// ===== Tab 保存 =====

function saveTab(tab) {
    var payload = buildPayload(tab);
    var body = { tab: tab, payload: JSON.stringify(payload) };

    doPoster('/system/editTab', body, function (data) {
        if (data.success) {
            bootGrowl(data.resultValue || "配置已保存", "success");
        } else {
            bootGrowl(data.resultValue || "保存失败", "danger");
        }
    });
}

function buildPayload(tab) {
    switch (tab) {
        case 'basic':
            return {
                expireDataDays: parseInt($('#basic_expireDataDays').val()),
                expireLogDays: parseInt($('#basic_expireLogDays').val()),
                refreshIntervalSeconds: parseInt($('#basic_refreshIntervalSeconds').val()),
                enableStorageWriteFail: $('#basic_enableStorageWriteFail').is(':checked'),
                maxStorageErrorLength: parseInt($('#basic_maxStorageErrorLength').val()),
                enableCDN: $('#basic_enableCDN').is(':checked'),
                enableSchemaResolver: $('#basic_enableSchemaResolver').is(':checked'),
                enableWatermark: $('#basic_enableWatermark').is(':checked'),
                watermark: $('#basic_watermark').val(),
                enablePrintTraceInfo: $('#basic_enablePrintTraceInfo').is(':checked'),
                resetPwd: $('#basic_resetPwd').is(':checked')
            };
        case 'executors':
            return {
                general: {
                    threadCoreSize: parseInt($('#exec_general_threadCoreSize').val()),
                    maxThreadSize: parseInt($('#exec_general_maxThreadSize').val()),
                    threadQueueCapacity: parseInt($('#exec_general_threadQueueCapacity').val())
                },
                storage: {
                    threadCoreSize: parseInt($('#exec_storage_threadCoreSize').val()),
                    maxThreadSize: parseInt($('#exec_storage_maxThreadSize').val()),
                    threadQueueCapacity: parseInt($('#exec_storage_threadQueueCapacity').val())
                },
                dispatch: {
                    threadCoreSize: parseInt($('#exec_dispatch_threadCoreSize').val()),
                    maxThreadSize: parseInt($('#exec_dispatch_maxThreadSize').val()),
                    threadQueueCapacity: parseInt($('#exec_dispatch_threadQueueCapacity').val())
                }
            };
        case 'buffers':
            return {
                bufferPullCount: parseInt($('#buf_general_bufferPullCount').val()),
                bufferQueueCapacity: parseInt($('#buf_general_bufferQueueCapacity').val()),
                bufferPeriodMillisecond: parseInt($('#buf_general_bufferPeriodMillisecond').val())
            };
        case 'retry':
            return {
                disable: $('#retry_disable').is(':checked'),
                maxAttempts: parseInt($('#retry_maxAttempts').val()),
                initialIntervalMs: parseInt($('#retry_initialIntervalMs').val()),
                maxIntervalMs: parseInt($('#retry_maxIntervalMs').val()),
                multiplier: parseFloat($('#retry_multiplier').val()),
                maxDurationMs: parseInt($('#retry_maxDurationMs').val()),
                terminationMode: $('#retry_terminationMode').val(),
                useKeyword: $('#retry_useKeyword').is(':checked'),
                matchMode: $('#retry_matchMode').val(),
                keywords: textToLines($('#retry_keywords').val()),
                excludeKeywords: textToLines($('#retry_excludeKeywords').val())
            };
        case 'notify':
            var channels = [];
            $('input[name="channels"]:checked').each(function () { channels.push($(this).val()); });
            return {
                channels: channels,
                mail: {
                    username: $('#notify_mail_username').val(),
                    password: $('#notify_mail_password').val()
                },
                dingtalk: {
                    webhookUrl: $('#notify_dingtalk_webhookUrl').val(),
                    secret: $('#notify_dingtalk_secret').val()
                }
            };
        case 'error':
            return {
                threshold: parseInt($('#error_threshold').val()),
                fatalMatchMode: $('#error_fatalMatchMode').val(),
                fatalKeywords: textToLines($('#error_fatalKeywords').val())
            };
        case 'monitor':
            return {
                schedulerPoolSize: parseInt($('#monitor_schedulerPoolSize').val()),
                cpuMemoryIntervalMs: parseInt($('#monitor_cpuMemoryIntervalMs').val()),
                healthCheckIntervalMs: parseInt($('#monitor_healthCheckIntervalMs').val()),
                cleanupIntervalMs: parseInt($('#monitor_cleanupIntervalMs').val()),
                metricReportIntervalMs: parseInt($('#monitor_metricReportIntervalMs').val()),
                alertCron: $('#monitor_alertCron').val()
            };
        case 'template':
            return {
                maxDepth: parseInt($('#template_maxDepth').val())
            };
        case 'sqlserver_ct':
            return {
                pollIntervalMs: parseInt($('#sqlserverCT_pollIntervalMs').val()),
                snapshotTimeIntervalMs: parseInt($('#sqlserverCT_snapshotTimeIntervalMs').val())
            };
        default:
            return {};
    }
}

function textToLines(text) {
    if (!text || !text.trim()) return [];
    return text.split('\n').map(function (s) { return s.trim(); }).filter(function (s) { return s.length > 0; });
}

function resetTab(tab) {
    if (!confirm('确定要恢复系统默认吗？该操作将清空此 Tab 的自定义配置。')) return;
    doPoster('/system/resetTab?tab=' + tab, {}, function (data) {
        if (data.success) {
            bootGrowl("已恢复系统默认。", "success");
            loadTabs();
        } else {
            bootGrowl(data.resultValue || "操作失败", "danger");
        }
    });
}

// ===== 辅助 =====

function toggleWatermark() {
    if ($('#basic_enableWatermark').is(':checked')) {
        $('#watermark_group').removeClass('hidden');
    } else {
        $('#watermark_group').addClass('hidden');
    }
}

function toggleStorageErrorLength() {
    if ($('#basic_enableStorageWriteFail').is(':checked')) {
        $('#maxStorageErrorLength_group').removeClass('hidden');
    } else {
        $('#maxStorageErrorLength_group').addClass('hidden');
    }
}

// ===== 初始化 =====

$(function () {
    loadTabs();

    // 保存按钮
    $(document).on('click', '.btn-save-tab', function () {
        var tab = $(this).data('tab');
        saveTab(tab);
    });

    // 恢复默认按钮
    $(document).on('click', '.btn-reset-tab', function () {
        var tab = $(this).data('tab');
        resetTab(tab);
    });

    // 开关切换
    $('#basic_enableWatermark').change(toggleWatermark);
    $('#basic_enableStorageWriteFail').change(toggleStorageErrorLength);

    // 分组通知配置
    initGroupNotify();
});

// ===== 分组通知配置 =====

var groupNotifyConfigs = {};
var projectGroups = [];
var currentGroupFilter = 'all';

function initGroupNotify() {
    loadGroupNotifyConfigs();
    loadProjectGroups();

    // 通道复选框切换
    $(document).on('change', 'input[name="gn_channels"]', function() {
        var mailChecked = $('input[name="gn_channels"][value="mail"]').is(':checked');
        var dingtalkChecked = $('input[name="gn_channels"][value="dingtalk"]').is(':checked');
        $('#gn_mail_section').toggle(mailChecked);
        $('#gn_dingtalk_section').toggle(dingtalkChecked);
    });

    // 过滤器
    $(document).on('click', '.group-filter-btn', function() {
        currentGroupFilter = $(this).data('filter');
        renderGroupNotifyList();
    });

    // 搜索
    $(document).on('input', '#groupNotifyFilter', function() {
        renderGroupNotifyList();
    });

    // 刷新
    $(document).on('click', '#btnRefreshGroups', function() {
        loadGroupNotifyConfigs();
        loadProjectGroups();
    });

    // 保存
    $(document).on('click', '#btnSaveGroupNotify', function() {
        saveGroupNotifyConfig();
    });

    // 删除
    $(document).on('click', '#btnDeleteGroupNotify', function() {
        deleteGroupNotifyConfig();
    });

    // 测试
    $(document).on('click', '#btnTestGroupNotify', function() {
        testGroupNotify();
    });
}

function loadGroupNotifyConfigs() {
    $.ajax({
        url: '/system/groupNotifyConfigs',
        type: 'GET',
        success: function(res) {
            if (res.code === '200') {
                groupNotifyConfigs = res.data || {};
                renderGroupNotifyList();
            }
        }
    });
}

function loadProjectGroups() {
    $.ajax({
        url: '/projectGroup/list',
        type: 'GET',
        success: function(res) {
            if (res.code === '200') {
                projectGroups = res.data || [];
                renderGroupNotifyList();
            }
        }
    });
}

function renderGroupNotifyList() {
    var filter = $('#groupNotifyFilter').val().toLowerCase();
    var $list = $('#groupNotifyList');
    $list.empty();

    // 添加“未分配”项
    var unassignedConfigured = groupNotifyConfigs['__unassigned__'] != null;
    if (currentGroupFilter === 'all' || (currentGroupFilter === 'configured' && unassignedConfigured) || (currentGroupFilter === 'unconfigured' && !unassignedConfigured)) {
        if (!filter || '未分配分组'.toLowerCase().indexOf(filter) >= 0) {
            var label = unassignedConfigured ? 'label-success' : 'label-default';
            $list.append('<li class="list-group-item group-notify-item" data-id="__unassigned__" style="cursor:pointer;">' +
                '<span class="label ' + label + '" style="margin-right:5px;">●</span> 未分配分组</li>');
        }
    }

    // 添加分组
    for (var i = 0; i < projectGroups.length; i++) {
        var g = projectGroups[i];
        var name = g.name || g.id;
        var configured = groupNotifyConfigs[g.id] != null;

        if (currentGroupFilter === 'configured' && !configured) continue;
        if (currentGroupFilter === 'unconfigured' && configured) continue;
        if (filter && name.toLowerCase().indexOf(filter) < 0) continue;

        var label = configured ? 'label-success' : 'label-default';
        $list.append('<li class="list-group-item group-notify-item" data-id="' + g.id + '" style="cursor:pointer;">' +
            '<span class="label ' + label + '" style="margin-right:5px;">●</span> ' + name + '</li>');
    }
}

$(document).on('click', '.group-notify-item', function() {
    var id = $(this).data('id');
    selectGroup(id);
});

function selectGroup(id) {
    $('#groupNotifyId').val(id);
    var name = id === '__unassigned__' ? '未分配分组' : '';
    for (var i = 0; i < projectGroups.length; i++) {
        if (projectGroups[i].id === id) {
            name = projectGroups[i].name || projectGroups[i].id;
            break;
        }
    }
    $('#groupNotifyTitle').text('配置分组：' + name);

    // 填充表单
    var config = groupNotifyConfigs[id];
    $('input[name="gn_channels"]').prop('checked', false);
    $('input[name="gn_mail_types"]').prop('checked', false);
    $('input[name="gn_dingtalk_types"]').prop('checked', false);
    $('input[name="gn_mail_username"]').val('');
    $('input[name="gn_mail_password"]').val('');
    $('input[name="gn_dingtalk_webhookUrl"]').val('');
    $('input[name="gn_dingtalk_secret"]').val('');

    if (config) {
        var channels = config.channels || [];
        for (var i = 0; i < channels.length; i++) {
            $('input[name="gn_channels"][value="' + channels[i] + '"]').prop('checked', true);
        }
        if (config.mail) {
            $('input[name="gn_mail_username"]').val(config.mail.username || '');
            var types = config.mail.notifyTypes || [];
            for (var i = 0; i < types.length; i++) {
                $('input[name="gn_mail_types"][value="' + types[i] + '"]').prop('checked', true);
            }
        }
        if (config.dingtalk) {
            $('input[name="gn_dingtalk_webhookUrl"]').val(config.dingtalk.webhookUrl || '');
            var types = config.dingtalk.notifyTypes || [];
            for (var i = 0; i < types.length; i++) {
                $('input[name="gn_dingtalk_types"][value="' + types[i] + '"]').prop('checked', true);
            }
        }
    }

    // 触发通道切换
    $('input[name="gn_channels"]').trigger('change');
}

function buildGroupNotifyPayload() {
    var channels = [];
    $('input[name="gn_channels"]:checked').each(function() { channels.push($(this).val()); });

    var payload = { channels: channels };

    if (channels.indexOf('mail') >= 0) {
        var mail = {};
        var username = $('input[name="gn_mail_username"]').val();
        var password = $('input[name="gn_mail_password"]').val();
        if (username) mail.username = username;
        if (password) mail.password = password;
        var types = [];
        $('input[name="gn_mail_types"]:checked').each(function() { types.push($(this).val()); });
        if (types.length > 0) mail.notifyTypes = types;
        payload.mail = mail;
    }

    if (channels.indexOf('dingtalk') >= 0) {
        var dingtalk = {};
        var webhookUrl = $('input[name="gn_dingtalk_webhookUrl"]').val();
        var secret = $('input[name="gn_dingtalk_secret"]').val();
        if (webhookUrl) dingtalk.webhookUrl = webhookUrl;
        if (secret) dingtalk.secret = secret;
        var types = [];
        $('input[name="gn_dingtalk_types"]:checked').each(function() { types.push($(this).val()); });
        if (types.length > 0) dingtalk.notifyTypes = types;
        payload.dingtalk = dingtalk;
    }

    return JSON.stringify(payload);
}

function saveGroupNotifyConfig() {
    var id = $('#groupNotifyId').val();
    if (!id) { alert('请选择分组'); return; }
    var payload = buildGroupNotifyPayload();
    $.ajax({
        url: '/system/saveGroupNotifyConfig',
        type: 'POST',
        data: { projectGroupId: id, payload: payload },
        success: function(res) {
            if (res.code === '200') {
                alert('保存成功');
                loadGroupNotifyConfigs();
            } else {
                alert('保存失败：' + res.msg);
            }
        }
    });
}

function deleteGroupNotifyConfig() {
    var id = $('#groupNotifyId').val();
    if (!id) { alert('请选择分组'); return; }
    if (!confirm('确定删除该分组的通知配置？')) return;
    $.ajax({
        url: '/system/deleteGroupNotifyConfig',
        type: 'POST',
        data: { projectGroupId: id },
        success: function(res) {
            if (res.code === '200') {
                alert('删除成功');
                loadGroupNotifyConfigs();
            } else {
                alert('删除失败：' + res.msg);
            }
        }
    });
}

function testGroupNotify() {
    var payload = buildGroupNotifyPayload();
    $.ajax({
        url: '/system/testGroupNotify',
        type: 'POST',
        data: { payload: payload },
        success: function(res) {
            if (res.code === '200') {
                alert('测试消息已发送');
            } else {
                alert('测试失败：' + res.msg);
            }
        }
    });
}
