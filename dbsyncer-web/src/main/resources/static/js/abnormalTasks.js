/**
 * 异常任务页面
 * 使用统一 TaskList 模块渲染任务列表
 */
(function ($) {
    'use strict';

    var taskList = null;
    var refreshTimer = null;

    // 页面代际守卫（ADR-0032）：本页面被新页面替换后，其异步回调与轮询自动失效
    var guard = pageGuard();

    /**
     * 页面已被替换则顺手停掉轮询并返回 true，调用方应立即放弃本次回调。
     * 有了它，定时器无需任何外部清理机制：导航离开后下一次触发就会自我销毁。
     */
    function stale() {
        if (isPageAlive(guard)) {
            return false;
        }
        if (refreshTimer) {
            clearInterval(refreshTimer);
            refreshTimer = null;
        }
        return true;
    }

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

    function loadData(silent) {
        if (stale()) return;
        if (!silent) $.loadingT(true);
        $.ajax({
            url: '/index/abnormalTasks/api',
            type: 'GET',
            success: function(resp) {
                if (stale()) return;
                if (!silent) $.loadingT(false);
                if (resp && resp.success) {
                    taskList.setData(resp.resultValue || []);
                } else {
                    taskList.setData([]);
                }
            },
            error: function() {
                if (stale()) return;
                if (!silent) $.loadingT(false);
                taskList.setData([]);
            }
        });
    }

    function startAutoRefresh() {
        if (refreshTimer) clearInterval(refreshTimer);
        $.get('/monitor/getRefreshIntervalSeconds', function(resp) {
            if (stale()) return;
            if (resp && resp.success) {
                var interval = resp.resultValue;
                refreshTimer = setInterval(function() { loadData(true); }, interval * 1000);
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

    init();

})(jQuery);
