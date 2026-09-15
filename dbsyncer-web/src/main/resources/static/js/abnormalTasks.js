/**
 * 异常任务页面
 * 使用 TaskPanel 组件渲染
 */
(function ($) {
    'use strict';

    var panel = null;
    var refreshTimer = null;

    // 页面代际守卫（ADR-0032）
    var guard = pageGuard();

    function stale() {
        if (isPageAlive(guard)) return false;
        if (refreshTimer) {
            clearInterval(refreshTimer);
            refreshTimer = null;
        }
        return true;
    }

    function init() {
        panel = new TaskPanel();
        panel.init('#abnormalTasksContainer', {
            enableSearch: false,
            enableFilter: false,
            enableCreate: false,
            enableCardView: false,
            enableCheckbox: true,
            enableBatchStart: true,
            enableBatchStop: false,
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
                    panel.setData(resp.resultValue || []);
                } else {
                    panel.setData([]);
                }
            },
            error: function() {
                if (stale()) return;
                if (!silent) $.loadingT(false);
                panel.setData([]);
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
