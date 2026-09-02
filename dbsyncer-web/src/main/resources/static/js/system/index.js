// ADR-0029: 6-Tab 系统参数页面

var tabsData = {};

function loadTabs() {
    doGet('/system/tabs', function (data) {
        if (data.success) {
            tabsData = data.resultValue || data.result;
            fillBasicTab(tabsData.basic);
            fillExecutorsTab(tabsData.executors);
            fillBuffersTab(tabsData.buffers);
            fillRetryTab(tabsData.retry);
            fillNotifyTab(tabsData.notify);
            fillErrorTab(tabsData.error);
        }
    });
}

function loadServerInfo() {
    doGet('/system/serverInfo', function (data) {
        if (data.success) {
            var info = data.resultValue || data.result;
            var html = '<table class="table table-condensed table-striped">';
            html += '<tr><td>服务端口</td><td>' + (info.serverPort || '-') + '</td></tr>';
            html += '<tr><td>SSL</td><td>' + (info.sslEnabled || 'false') + '</td></tr>';
            html += '<tr><td>HTTP 端口</td><td>' + (info.httpPort || '-') + '</td></tr>';
            html += '<tr><td>上下文路径</td><td>' + (info.contextPath || '/') + '</td></tr>';
            html += '<tr><td>存储类型</td><td>' + (info.storageType || '-') + '</td></tr>';
            html += '<tr><td>数据库地址</td><td>' + (info.databaseUrl || '-') + '</td></tr>';
            html += '<tr><td>数据库用户</td><td>' + (info.databaseUsername || '-') + '</td></tr>';
            html += '<tr><td>Worker ID</td><td>' + (info.workerId || '-') + '</td></tr>';
            html += '<tr><td>帮助文档路径</td><td>' + (info.helpdocPath || '-') + '</td></tr>';
            html += '<tr><td>日志级别</td><td>' + (info.logLevel || '-') + '</td></tr>';
            html += '</table>';
            $('#serverInfoContent').html(html);
        }
    });
}

// ===== Tab 填充 =====

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
    $('#exec_schedulerPoolSize').val(exec.schedulerPoolSize);
}

function fillBuffersTab(buf) {
    if (!buf) return;
    if (buf.general) {
        $('#buf_general_bufferWriterCount').val(buf.general.bufferWriterCount);
        $('#buf_general_bufferPullCount').val(buf.general.bufferPullCount);
        $('#buf_general_bufferQueueCapacity').val(buf.general.bufferQueueCapacity);
        $('#buf_general_bufferPeriodMillisecond').val(buf.general.bufferPeriodMillisecond);
    }
    if (buf.storage) {
        $('#buf_storage_bufferPullCount').val(buf.storage.bufferPullCount);
        $('#buf_storage_bufferQueueCapacity').val(buf.storage.bufferQueueCapacity);
        $('#buf_storage_bufferPeriodMillisecond').val(buf.storage.bufferPeriodMillisecond);
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
        // password 不预填（脱敏）
    }
    // dingtalk
    if (notify.dingtalk) {
        $('#notify_dingtalk_webhookUrl').val(notify.dingtalk.webhookUrl || '');
        $('#notify_dingtalk_secret').val(notify.dingtalk.secret || '');
    }
}

function fillErrorTab(error) {
    if (!error) return;
    $('#error_threshold').val(error.threshold);
    $('#error_fatalMatchMode').val(error.fatalMatchMode || 'CONTAINS_IGNORE_CASE');
    $('#error_fatalKeywords').val((error.fatalKeywords || []).join('\n'));
}

// ===== Tab 保存 =====

function saveTab(tab) {
    var payload = buildPayload(tab);
    var body = { tab: tab, payload: JSON.stringify(payload) };

    doPoster('/system/editTab', body, function (data) {
        if (data.success) {
            var result = data.resultValue || data.result;
            if (result && result.restartRequired) {
                bootGrowl("配置已保存，需重启应用生效。", "warning");
            } else if (result && result.errors) {
                bootGrowl("校验失败: " + result.errors.join(', '), "danger");
            } else {
                bootGrowl("配置已保存并立即生效。", "success");
            }
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
                },
                schedulerPoolSize: parseInt($('#exec_schedulerPoolSize').val())
            };
        case 'buffers':
            return {
                general: {
                    bufferWriterCount: parseInt($('#buf_general_bufferWriterCount').val()),
                    bufferPullCount: parseInt($('#buf_general_bufferPullCount').val()),
                    bufferQueueCapacity: parseInt($('#buf_general_bufferQueueCapacity').val()),
                    bufferPeriodMillisecond: parseInt($('#buf_general_bufferPeriodMillisecond').val())
                },
                storage: {
                    bufferPullCount: parseInt($('#buf_storage_bufferPullCount').val()),
                    bufferQueueCapacity: parseInt($('#buf_storage_bufferQueueCapacity').val()),
                    bufferPeriodMillisecond: parseInt($('#buf_storage_bufferPeriodMillisecond').val())
                }
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
                    password: $('#notify_mail_password').val() || '********'
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

// ===== HTTP helpers =====

function doGet(url, callback) {
    $.ajax({
        url: url,
        type: 'GET',
        dataType: 'json',
        success: callback,
        error: function (xhr) {
            bootGrowl("请求失败: " + xhr.statusText, "danger");
        }
    });
}

function doPoster(url, data, callback) {
    $.ajax({
        url: url,
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(data),
        dataType: 'json',
        success: callback,
        error: function (xhr) {
            bootGrowl("请求失败: " + xhr.statusText, "danger");
        }
    });
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

    // 加载 A 类信息
    $('#serverInfoPanel').on('shown.bs.collapse', function () {
        loadServerInfo();
    });
});
