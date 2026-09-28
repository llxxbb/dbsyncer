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
            fillErrorTab(tabsData.error);
            fillMonitorTab(tabsData.monitor);
            fillTemplateTab(tabsData.template);
            fillSqlServerCTTab(tabsData["sqlserver_ct"]);
            // 通知类型需要先加载枚举定义再渲染
            loadNotifyTypes(function() {
                fillNotifyTab(tabsData.notify);
            });
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

// 通知类型枚举定义，从后端 API 动态加载
var NOTIFY_TYPES = [];

function loadNotifyTypes(callback) {
    doGetWithoutLoading('/system/notifyTypes', {}, function (data) {
        if (data.success) {
            var list = data.resultValue || data.result || [];
            NOTIFY_TYPES = list.map(function (item) {
                return { value: item.value, label: item.label, tip: item.description };
            });
        }
        if (typeof callback === 'function') callback();
    });
}

function renderNotifyForm($container, config, opts) {
    opts = opts || {};
    var pwdPlaceholder = opts.pwdPlaceholder || '留空保留原值';
    var overridePlaceholder = opts.overridePlaceholder || '';
    var isGroupMode = opts.mode === 'group';
    
    var html = '';
    
    // 分组模式：增加“启用独立配置”开关
    if (isGroupMode) {
        html += '<div class="form-group">';
        html += '<label class="col-sm-2 control-label">启用配置<i class="fa fa-question-circle fa_gray" title="开启后使用本分组的通道配置；关闭则走全局配置。"></i></label>';
        html += '<div class="col-sm-10">';
        html += '<label class="checkbox-inline"><input type="checkbox" class="nf-enabled"> 启用</label>';
        html += '</div></div>';
    }
    
    html += '<div class="nf-fields-section"' + (isGroupMode ? ' style="display:none;"' : '') + '>';
    html += '<div class="form-group">';
    html += '<label class="col-sm-2 control-label">启用通道<i class="fa fa-question-circle fa_gray" title="选择要启用的通知通道。未勾选的通道不会发送任何通知消息。"></i></label>';
    html += '<div class="col-sm-10">';
    html += '<label class="checkbox-inline"><input type="checkbox" class="nf-channels" value="mail"> 邮件</label>';
    html += '<label class="checkbox-inline"><input type="checkbox" class="nf-channels" value="dingtalk"> 钉钉</label>';
    html += '</div></div>';
    
    html += '<div class="nf-mail-section" style="display:none;">';
    html += '<h4>邮件配置</h4>';
    html += '<div class="form-group">';
    html += '<label class="col-sm-2 control-label">发件邮箱<i class="fa fa-question-circle fa_gray" title="用于发送告警通知邮件的发件人邮箱地址。需确保该邮箱已开启 SMTP 服务。"></i></label>';
    html += '<div class="col-sm-4"><input type="text" class="form-control nf-mail-username"' + (overridePlaceholder ? ' placeholder="' + overridePlaceholder + '"' : '') + '/></div>';
    html += '<label class="col-sm-2 control-label">授权码<i class="fa fa-question-circle fa_gray" title="发件邮箱的 SMTP 授权码（非邮箱登录密码）。通常需在邮箱设置中开启 SMTP 服务后获取。"></i></label>';
    html += '<div class="col-sm-4"><input type="password" class="form-control nf-mail-password" placeholder="' + pwdPlaceholder + '"/></div>';
    html += '</div>';
    html += '<div class="form-group">';
    html += '<label class="col-sm-2 control-label">排除类型<i class="fa fa-question-circle fa_gray" title="勾选后该类型的通知将不会发送。默认全部不勾选，即接收所有类型的通知。适用于排除噪音类型（如手动操作）的场景。"></i></label>';
    html += '<div class="col-sm-10 nf-mail-types" style="line-height:2.2;">';
    for (var i = 0; i < NOTIFY_TYPES.length; i++) {
        html += '<label class="checkbox-inline" style="margin-right:15px;" title="' + NOTIFY_TYPES[i].tip + '">';
        html += '<input type="checkbox" class="nf-mail-type" value="' + NOTIFY_TYPES[i].value + '">';
        html += ' ' + NOTIFY_TYPES[i].label + '</label>';
    }
    html += '</div></div></div>';
    
    html += '<div class="nf-dingtalk-section" style="display:none;">';
    html += '<h4>钉钉配置</h4>';
    html += '<div class="form-group">';
    html += '<label class="col-sm-2 control-label">Webhook URL<i class="fa fa-question-circle fa_gray" title="钉钉群机器人的 Webhook 推送地址。在钉钉群 → 设置 → 智能群助手 → 添加机器人 后获取。"></i></label>';
    html += '<div class="col-sm-4"><input type="text" class="form-control nf-dingtalk-webhookUrl"' + (overridePlaceholder ? ' placeholder="' + overridePlaceholder + '"' : '') + '/></div>';
    html += '<label class="col-sm-2 control-label">签名密钥<i class="fa fa-question-circle fa_gray" title="钉钉机器人的签名校验密钥（加签方式）。配置后需重启应用生效。如机器人安全设置中未启用加签可留空。"></i></label>';
    html += '<div class="col-sm-4"><input type="password" class="form-control nf-dingtalk-secret" placeholder="' + pwdPlaceholder + '"/></div>';
    html += '</div>';
    html += '<div class="form-group">';
    html += '<label class="col-sm-2 control-label">排除类型<i class="fa fa-question-circle fa_gray" title="勾选后该类型的通知将不会通过钉钉发送。默认全部不勾选，即接收所有类型。"></i></label>';
    html += '<div class="col-sm-10 nf-dingtalk-types" style="line-height:2.2;">';
    for (var i = 0; i < NOTIFY_TYPES.length; i++) {
        html += '<label class="checkbox-inline" style="margin-right:15px;" title="' + NOTIFY_TYPES[i].tip + '">';
        html += '<input type="checkbox" class="nf-dingtalk-type" value="' + NOTIFY_TYPES[i].value + '">';
        html += ' ' + NOTIFY_TYPES[i].label + '</label>';
    }
    html += '</div></div></div>';
    html += '</div>'; // close nf-fields-section
    
    $container.html(html);
    
    if (config) {
        fillNotifyFormValues($container, config);
    }
    
    // 分组模式：绑定启用开关事件
    if (isGroupMode) {
        $container.find('.nf-enabled').on('change', function() {
            $container.find('.nf-fields-section').toggle($(this).is(':checked'));
        });
    }
    
    $container.find('.nf-channels').on('change', function() {
        toggleNotifySections($container);
    });
}

function toggleNotifySections($container) {
    var mailChecked = $container.find('.nf-channels[value="mail"]').is(':checked');
    var dingtalkChecked = $container.find('.nf-channels[value="dingtalk"]').is(':checked');
    $container.find('.nf-mail-section').toggle(mailChecked);
    $container.find('.nf-dingtalk-section').toggle(dingtalkChecked);
}

function fillNotifyFormValues($container, config) {
    $container.find('.nf-channels').prop('checked', false);
    $container.find('.nf-mail-type, .nf-dingtalk-type').prop('checked', false);
    $container.find('input[type="text"], input[type="password"]').val('');
    
    // 分组模式：处理 enabled 字段
    var $enabled = $container.find('.nf-enabled');
    if ($enabled.length > 0) {
        var isEnabled = config && config.enabled === true;
        $enabled.prop('checked', isEnabled);
        $container.find('.nf-fields-section').toggle(isEnabled);
    }
    
    if (!config) return;
    
    var channels = config.channels || [];
    for (var i = 0; i < channels.length; i++) {
        $container.find('.nf-channels[value="' + channels[i] + '"]').prop('checked', true);
    }
    
    if (config.mail) {
        $container.find('.nf-mail-username').val(config.mail.username || '');
        var pwd = config.mail.password;
        if (pwd && pwd.indexOf('****') >= 0) {
            $container.find('.nf-mail-password').attr('placeholder', '已配置，留空则保留原值');
        }
        var types = config.mail.excludedNotifyTypes || [];
        for (var i = 0; i < types.length; i++) {
            $container.find('.nf-mail-type[value="' + types[i] + '"]').prop('checked', true);
        }
    }
    
    if (config.dingtalk) {
        $container.find('.nf-dingtalk-webhookUrl').val(config.dingtalk.webhookUrl || '');
        var secret = config.dingtalk.secret;
        if (secret && secret.indexOf('****') >= 0) {
            $container.find('.nf-dingtalk-secret').attr('placeholder', '已配置，留空则保留原值');
        }
        var types = config.dingtalk.excludedNotifyTypes || [];
        for (var i = 0; i < types.length; i++) {
            $container.find('.nf-dingtalk-type[value="' + types[i] + '"]').prop('checked', true);
        }
    }
    
    toggleNotifySections($container);
}

function readNotifyForm($container) {
    var payload = {};
    
    // 分组模式：读取 enabled 字段
    var $enabled = $container.find('.nf-enabled');
    if ($enabled.length > 0) {
        payload.enabled = $enabled.is(':checked');
    }
    
    var channels = [];
    $container.find('.nf-channels:checked').each(function() { channels.push($(this).val()); });
    payload.channels = channels;
    
    if (channels.indexOf('mail') >= 0) {
        var mail = {};
        var username = $container.find('.nf-mail-username').val();
        var password = $container.find('.nf-mail-password').val();
        if (username) mail.username = username;
        if (password) mail.password = password;
        var types = [];
        $container.find('.nf-mail-type:checked').each(function() { types.push($(this).val()); });
        if (types.length > 0) mail.excludedNotifyTypes = types;
        payload.mail = mail;
    }
    
    if (channels.indexOf('dingtalk') >= 0) {
        var dingtalk = {};
        var webhookUrl = $container.find('.nf-dingtalk-webhookUrl').val();
        var secret = $container.find('.nf-dingtalk-secret').val();
        if (webhookUrl) dingtalk.webhookUrl = webhookUrl;
        if (secret) dingtalk.secret = secret;
        var types = [];
        $container.find('.nf-dingtalk-type:checked').each(function() { types.push($(this).val()); });
        if (types.length > 0) dingtalk.excludedNotifyTypes = types;
        payload.dingtalk = dingtalk;
    }
    
    return payload;
}

function fillNotifyTab(notify) {
    if (!notify) return;
    renderNotifyForm($('#globalNotifyFields'), notify, { mode: 'global' });
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
                enablePrintTraceInfo: $('#basic_enablePrintTraceInfo').is(':checked')
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
            return readNotifyForm($('#globalNotifyFields'));
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

function initGroupNotify() {
    // 监听 tab 切换，切换到"通知通道"时才加载分组数据
    $('a[data-toggle="tab"]').on('shown.bs.tab', function(e) {
        if ($(e.target).attr('href') === '#tab-notify') {
            loadGroupNotifyConfigs();
            loadProjectGroups();
        }
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
            if (res.success) {
                groupNotifyConfigs = res.resultValue || {};
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
            if (res.success) {
                projectGroups = res.resultValue || [];
                renderGroupNotifyList();
            }
        }
    });
}

function renderGroupNotifyList() {
    var filter = $('#groupNotifyFilter').val().toLowerCase();
    var $list = $('#groupNotifyList');
    $list.empty();

    // 添加"未分配"项
    var unassignedConfigured = groupNotifyConfigs['__unassigned__'] != null;
    if (!filter || '未分配分组'.toLowerCase().indexOf(filter) >= 0) {
        var label = unassignedConfigured ? 'label-success' : 'label-default';
        $list.append('<li class="list-group-item group-notify-item" data-id="__unassigned__" style="cursor:pointer;">' +
            '<span class="label ' + label + '" style="margin-right:5px;">●</span> 未分配分组</li>');
    }

    // 添加全部分组
    for (var i = 0; i < projectGroups.length; i++) {
        var g = projectGroups[i];
        var name = g.name || g.id;
        var configured = groupNotifyConfigs[g.id] != null;

        if (filter && name.toLowerCase().indexOf(filter) < 0) continue;

        var label = configured ? 'label-success' : 'label-default';
        $list.append('<li class="list-group-item group-notify-item" data-id="' + g.id + '" style="cursor:pointer;">' +
            '<span class="label ' + label + '" style="margin-right:5px;">●</span> ' + name + '</li>');
    }

    // 恢复选中状态
    var selectedId = $('#groupNotifyId').val();
    if (selectedId) {
        $('.group-notify-item[data-id="' + selectedId + '"]').addClass('active');
    }
}

$(document).on('click', '.group-notify-item', function() {
    $('.group-notify-item').removeClass('active');
    $(this).addClass('active');
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

    // 渲染表单
    var config = groupNotifyConfigs[id];
    renderNotifyForm($('#groupNotifyFields'), config, { 
        mode: 'group', 
        overridePlaceholder: '留空使用全局配置' 
    });
}

function buildGroupNotifyPayload() {
    return JSON.stringify(readNotifyForm($('#groupNotifyFields')));
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
            if (res.success) {
                bootGrowl('保存成功', 'success');
                loadGroupNotifyConfigs();
            } else {
                bootGrowl(res.resultValue || '保存失败', 'danger');
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
            if (res.success) {
                bootGrowl('删除成功', 'success');
                loadGroupNotifyConfigs();
            } else {
                bootGrowl(res.resultValue || '删除失败', 'danger');
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
            if (res.success) {
                bootGrowl('测试消息已发送', 'success');
            } else {
                bootGrowl(res.resultValue || '测试失败', 'danger');
            }
        }
    });
}
