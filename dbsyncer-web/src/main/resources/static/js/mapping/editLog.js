/**
 * 任务日志模块（从 edit.js 拆出，Stage 1/5）
 *
 * 职责：任务日志的类型筛选、分页查询、列表渲染、日志内容详情弹窗。
 *
 * 依赖（全部是全局，不是模块内定义）：
 *   - currentMappingId            edit.js 的顶层 var，本模块读它做「任务已切换」判断，
 *                                 并在 bindMappingLogEvent() 里回写
 *   - doGetter / doPoster /       common.js
 *     bootGrowl
 *   - BootstrapDialog             插件（bootstrap-dialog）
 *   - jQuery $
 *
 * 加载顺序：必须在 common.js 之后；与 edit.js 的先后不影响行为（本模块不定义
 * 任何与 edit.js / editFilterAndConvert.js 重名的函数）。
 *
 * 本模块不创建任何定时器，因此不涉及 ADR-0032 的「定时器句柄局部持有」约束。
 */

function loadLogTypeOptions() {
    doGetter('/monitor/getLogTypes', {}, function (data) {
        if (data.success === true) {
            var $select = $('#logTypeFilter');
            $select.find('option').not(':first').remove();
            data.resultValue.forEach(function (item) {
                $select.append('<option value="' + item.code + '">' + item.name + '</option>');
            });
        }
    });
}

// currentMappingId 已在文件顶部（轮询定时器附近）声明。
// 此处不可重复赋 null，否则会清空正在运行的轮询所依赖的任务标识。

function doQueryLog(pageNum, append, callback) {
    if (!currentMappingId) return;
    var logType = $('#logTypeFilter').val();
    var logLevel = $('#logLevelFilter').val();
    doGetter('/monitor/queryLog', {
        id: currentMappingId,
        type: logType,
        logLevel: logLevel,
        pageNum: pageNum || 1,
        pageSize: 10
    }, function (data) {
        if (data.success === true) {
            if (append) {
                refreshLogList(data.resultValue, true);
                if (data.resultValue.data.length > 0 && callback) callback();
            } else {
                refreshLogList(data.resultValue);
            }
        } else {
            bootGrowl(data.resultValue, "danger");
        }
    });
}

// Log 查询功能
function bindMappingLogEvent(mappingId) {
    currentMappingId = mappingId;

    // 查询日志
    $('#queryLogBtn').click(function () {
        doQueryLog(1);
    });
    
    // 显示更多
    $('#queryLogMore').click(function () {
        var $this = $(this);
        var num = parseInt($this.attr('num'));
        var nextPage = num + 1;
        doQueryLog(nextPage, true, function() {
            $this.attr('num', nextPage);
        });
    });
    
    // 清空日志
    $('#clearLogBtn').click(function () {
        BootstrapDialog.show({
            title: '警告',
            type: BootstrapDialog.TYPE_DANGER,
            message: '确认清空该任务的所有日志？',
            size: BootstrapDialog.SIZE_NORMAL,
            buttons: [{
                label: '确定',
                action: function (dialog) {
                    doPoster('/monitor/clearLog', {
                        id: mappingId
                    }, function (data) {
                        if (data.success === true) {
                            bootGrowl('清空日志成功!', "success");
                            refreshLogList({ data: [], total: 0 });
                        } else {
                            bootGrowl(data.resultValue, "danger");
                        }
                    });
                    dialog.close();
                }
            }, {
                label: '取消',
                action: function (dialog) {
                    dialog.close();
                }
            }]
        });
    });
}

function formatDate(time) {
    return new Date(time).toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false
    }).replace(/\//g, '-');
}

function bindLogContentDetailEvent() {
    var $queryLogContent = $("#mappingLog .queryLogContent");
    $queryLogContent.unbind("click");
    $queryLogContent.click(function () {
        var content = $(this).text();
        var html = '<div class="row driver_break_word">' + content + '</div>';
        BootstrapDialog.show({
            title: "日志内容",
            size: BootstrapDialog.SIZE_WIDE,
            message: html,
            type: BootstrapDialog.TYPE_INFO,
            buttons: [{
                label: "关闭",
                action: function (dialog) {
                    dialog.close();
                }
            }]
        });
    });
}

function refreshLogList(resultValue, append) {
    var $logList = $('#logList');
    var $logTotal = $('#logTotal');
    var html = showLog($logList, resultValue.data, append);
    if (append) {
        $logList.append(html);
    } else {
        $logList.html(html);
        $('#queryLogMore').attr('num', 1);
    }
    $logTotal.html(resultValue.total);
    bindLogContentDetailEvent();
}

function showLog($logList, arr, append) {
    var size = arr.length;
    var html = '';
    if (size > 0) {
        var start = append ? $logList.find('tr').length : 0;
        for (var i = 0; i < size; i++) {
            html += '<tr>';
            html += '<td>' + (start + i + 1) + '</td>';
            html += '<td>' + (arr[i].logType || '-') + '</td>';
            html += '<td>' + (arr[i].logLevel || '-') + '</td>';
            html += '<td style="max-width:100px;" class="dbsyncer_over_hidden"><a href="javascript:;" class="dbsyncer_pointer queryLogContent">' + (arr[i].json || '') + '</a></td>';
            html += '<td>' + formatDate(arr[i].createTime) + '</td>';
            html += '</tr>';
        }
    }
    return html;
}

// 初始化调用
$(function () {
    var mappingIdField = $('input[name="mappingId"], #mappingId, #id');
    var mappingId = mappingIdField.val();

    loadLogTypeOptions();
    bindMappingLogEvent(mappingId);
});

//# sourceURL=/js/mapping/editLog.js
