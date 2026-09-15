/**
 * 任务列表页面（主页）
 * 使用 TaskPanel 组件管理搜索/过滤/列表/卡片视图
 */
(function ($) {
    'use strict';

    var panel = null;
    var refreshTimer = null;
    var currentProjectGroupId = '';

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
        bindAddProjectGroup();
        bindGroupList();

        // projectGroupId 由服务端渲染在隐藏字段中
        currentProjectGroupId = $('#projectGroup').val() || '';

        // 初始化 TaskPanel
        var isAdmin = $('#isAdmin').val() === 'true';
        panel = new TaskPanel();
        panel.init('#taskPanel', {
            enableSearch: true,
            enableFilter: true,
            enableCreate: isAdmin,
            enableCardView: true,
            enableCheckbox: false,
            emptyMessage: '暂无任务',

            onCreate: function() {
                updateHash('/mapping/pageAdd?projectGroupId=' + currentProjectGroupId);
            },

            onSearch: function(params) {
                performSearch(params);
            },

            onStart: function(id) {
                doSingleAction(id, '/mapping/start?id=', '启动');
            },

            onStop: function(id) {
                doSingleAction(id, '/mapping/stop?id=', '停止');
            }
        });

        // 初始化过滤选项（数据源列表）
        loadFilterOptions();

        // 加载任务数据
        loadTaskList();
        startAutoRefresh();
    }

    // ==================== 数据加载 ====================

    function loadTaskList(silent) {
        if (stale()) return;
        if (!silent) $.loadingT(true);
        $.ajax({
            url: '/index/mappingdata',
            type: 'GET',
            data: { projectGroupId: currentProjectGroupId },
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
                refreshTimer = setInterval(function() { loadTaskList(true); }, interval * 1000);
            }
        });
    }

    function loadFilterOptions() {
        $.ajax({
            url: '/datasource/list',
            type: 'POST',
            success: function(data) {
                if (data.success && data.status === 200) {
                    panel.setFilterOptions({
                        source: data.resultValue || [],
                        target: data.resultValue || []
                    });
                }
            },
            error: function() { /* 静默 */ }
        });
    }

    // ==================== 搜索 ====================

    function performSearch(params) {
        var hasFilter = params.keyword || params.state || params.sourceConnectorType || params.targetConnectorType;
        if (!hasFilter) {
            loadTaskList();
            return;
        }

        $.loadingT(true);
        $.ajax({
            url: '/index/searchMapping',
            type: 'GET',
            data: {
                keyword: params.keyword,
                projectGroupId: currentProjectGroupId,
                state: params.state,
                sourceConnectorType: params.sourceConnectorType,
                targetConnectorType: params.targetConnectorType
            },
            success: function(data) {
                if (stale()) return;
                $.loadingT(false);
                if (data.success && data.status === 200) {
                    panel.setData(data.resultValue || []);
                } else {
                    bootGrowl(data.resultValue || '搜索失败', 'danger');
                }
            },
            error: function() {
                if (stale()) return;
                $.loadingT(false);
                bootGrowl('搜索请求失败', 'danger');
            }
        });
    }

    // ==================== 操作 ====================

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
                            loadTaskList();
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

    // ==================== 分组管理 ====================

    function bindAddProjectGroup() {
        $('#addProjectGroupBtn').click(function() {
            updateHash('/projectGroup/page/add');
        });
    }

    function doEditProjectGroup(projectGroupId) {
        if (projectGroupId) {
            updateHash('/projectGroup/page/edit?id=' + projectGroupId);
        } else {
            bootGrowl('请选择分组', 'danger');
        }
    }

    function doRemoveProjectGroup(projectGroupId) {
        if (!projectGroupId) {
            bootGrowl('请选择分组', 'danger');
            return;
        }
        BootstrapDialog.show({
            title: '提示',
            type: BootstrapDialog.TYPE_INFO,
            message: '确认删除分组？',
            size: BootstrapDialog.SIZE_NORMAL,
            buttons: [{
                label: '确定',
                action: function(dialog) {
                    doPoster('/projectGroup/remove', { id: projectGroupId }, function(data) {
                        if (data.success) {
                            backIndexPage();
                            bootGrowl(data.resultValue, 'success');
                        } else {
                            bootGrowl(data.resultValue, 'danger');
                        }
                    });
                    dialog.close();
                }
            }, {
                label: '取消',
                action: function(dialog) { dialog.close(); }
            }]
        });
    }

    function bindGroupList() {
        $('.group-list-item').on('click.groupList', function(e) {
            if ($(e.target).closest('.dropdown').length > 0) return;
            var groupId = $(this).data('group-id') || '';
            groupShow(groupId);
        });
    }

    function groupShow(id) {
        currentProjectGroupId = id || '';
        $('#projectGroup').val(currentProjectGroupId);
        updateHash('/index?projectGroupId=' + currentProjectGroupId);
    }

    // ==================== 导出给 HTML 模板的 inline onclick ====================
    window.doEditProjectGroup = doEditProjectGroup;
    window.doRemoveProjectGroup = doRemoveProjectGroup;

    $(function() { init(); });

})(jQuery);
