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
            enableCreate: false,
            enableCheckbox: true,
            enableBatchStart: true,
            emptyMessage: '暂无异常任务',
            onRefresh: function() {
                loadData();
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

    init();

})(jQuery);
