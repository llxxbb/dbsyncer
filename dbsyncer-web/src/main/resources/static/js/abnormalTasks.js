/**
 * 异常任务页面
 * 使用统一 TaskList 模块渲染任务列表
 */
(function ($) {
    'use strict';

    var taskList = null;
    var refreshTimer = null;

    function init() {
        taskList = new TaskList();
        taskList.init('#abnormalTasksContainer', {
            enableCheckbox: true,
            enableBatchStart: true,
            enableBatchStop: false,
            rowClickAction: 'detail',
            emptyMessage: '暂无异常任务',
            onStart: function(id) {
                doSingleAction(id, '/mapping/start?id=', '启动');
            },
            onBatchStart: function(ids) {
                doBatchAction(ids, '/mapping/batchStart', '启动');
            }
        });

        loadData();
        startAutoRefresh();
    }

    function loadData() {
        $.loadingT(true);
        $.ajax({
            url: '/index/abnormalTasks/api',
            type: 'GET',
            success: function(resp) {
                $.loadingT(false);
                if (resp && resp.success) {
                    taskList.setData(resp.resultValue || []);
                } else {
                    taskList.setData([]);
                }
            },
            error: function() {
                $.loadingT(false);
                taskList.setData([]);
            }
        });
    }

    function startAutoRefresh() {
        if (refreshTimer) clearInterval(refreshTimer);
        $.get('/monitor/getRefreshIntervalSeconds', function(resp) {
            if (resp && resp.success) {
                var interval = resp.resultValue;
                refreshTimer = setInterval(loadData, interval * 1000);
            }
        });
    }

    function doSingleAction(id, urlPrefix, actionName) {
        BootstrapDialog.show({
            title: '确认',
            type: BootstrapDialog.TYPE_PRIMARY,
            message: '确认' + actionName + '该任务？',
            size: BootstrapDialog.SIZE_NORMAL,
            buttons: [{
                label: '确定',
                action: function(dialog) {
                    $.loadingT(true);
                    $.post(urlPrefix + id, function(data) {
                        $.loadingT(false);
                        if (data && data.success) {
                            bootGrowl(data.resultValue || actionName + '成功', 'success');
                            loadData();
                        } else {
                            bootGrowl((data && data.resultValue) || actionName + '失败', 'danger');
                        }
                    }).error(function() {
                        $.loadingT(false);
                        bootGrowl(actionName + '失败', 'danger');
                    });
                    dialog.close();
                }
            }, {
                label: '取消',
                action: function(dialog) { dialog.close(); }
            }]
        });
    }

    function doBatchAction(ids, url, actionName) {
        BootstrapDialog.show({
            title: '确认',
            type: BootstrapDialog.TYPE_PRIMARY,
            message: '确认批量' + actionName + '选中的 ' + ids.length + ' 个任务？',
            size: BootstrapDialog.SIZE_NORMAL,
            buttons: [{
                label: '确定',
                action: function(dialog) {
                    $.loadingT(true);
                    $.post(url, { ids: ids.join(',') }, function(data) {
                        $.loadingT(false);
                        if (data && data.success) {
                            bootGrowl(data.resultValue || '批量' + actionName + '完成', 'success');
                            loadData();
                        } else {
                            bootGrowl((data && data.resultValue) || '批量' + actionName + '失败', 'danger');
                        }
                    }).error(function() {
                        $.loadingT(false);
                        bootGrowl('批量' + actionName + '失败', 'danger');
                    });
                    dialog.close();
                }
            }, {
                label: '取消',
                action: function(dialog) { dialog.close(); }
            }]
        });
    }

    // 清理定时器
    $(window).on('beforeunload', function() {
        if (refreshTimer) clearInterval(refreshTimer);
    });

    init();

})(jQuery);
