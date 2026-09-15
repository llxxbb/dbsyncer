/**
 * 任务列表页面（主页）
 * 使用统一 TaskList 模块渲染任务列表
 */
(function ($) {
    'use strict';

    var taskList = null;
    var refreshTimer = null;
    var currentProjectGroupId = '';

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
        bindAddProjectGroup();
        bindAddConnector();
        bindEditConnector();
        bindAddMapping();
        bindEditMapping();
        bindQueryData();
        bindConnectorDropdownMenu();
        bindMappingSearch();
        bindViewToggle();
        bindGroupList();

        // 初始化 TaskList
        taskList = new TaskList();
        taskList.init('#taskListContainer', {
            enableCheckbox: false,
            enableBatchStart: false,
            enableBatchStop: false,
            rowClickAction: null,
            emptyMessage: '暂无任务',
            onStart: function(id) { doSingleAction(id, '/mapping/start?id=', '启动'); },
            onStop: function(id) { doSingleAction(id, '/mapping/stop?id=', '停止'); }
        });

        // projectGroupId 由服务端渲染在 #projectGroup 隐藏字段中，直接读它即可。
        // 不要去解析地址栏：hash 路由下地址形如 .../#/index?projectGroupId=xxx，
        // 常规的 query 解析很容易在 '#' 之后取不到值（历史上这里就因此永远拿到空字符串），
        // 表现为任务列表不跟随分组切换，而卡片视图（服务端渲染）正常。
        currentProjectGroupId = $('#projectGroup').val() || '';

        loadTaskList();
        startAutoRefresh();
    }

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
                refreshTimer = setInterval(function() { loadTaskList(true); }, interval * 1000);
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
        // 仅更新 hash，由 SPA 路由重新加载页面（新页面的 init() 会拉取任务列表）。
        // 不能再额外调用 loadTaskList()：否则一次点击会发两次请求，
        // 且旧页面实例的定时器仍在跑，叠加后表现为鼠标移动就持续发请求。
        updateHash('/index?projectGroupId=' + currentProjectGroupId);
    }

    // ==================== 连接管理 ====================

    function bindAddConnector() {
        $('#indexAddConnectorBtn').click(function() {
            updateHash('/connector/page/add');
        });
    }

    function bindEditConnector() {
        $('.connectorList .dbsyncer_block').click(function() {
            var id = $(this).attr('id');
            updateHash('/connector/page/edit?id=' + id);
        });
    }

    function bindConnectorDropdownMenu() {
        $('.connectorList .dropdown-menu li.remove').click(function() {
            var url = $(this).attr('url');
            BootstrapDialog.show({
                title: '警告',
                type: BootstrapDialog.TYPE_DANGER,
                message: '确认删除连接？',
                size: BootstrapDialog.SIZE_NORMAL,
                buttons: [{
                    label: '确定',
                    action: function(dialog) {
                        doPost(url);
                        dialog.close();
                    }
                }, {
                    label: '取消',
                    action: function(dialog) { dialog.close(); }
                }]
            });
        });

        $('.connectorList .dropdown-menu li.copy').click(function() {
            var url = $(this).attr('url');
            doPost(url);
        });
    }

    function doPost(url) {
        doPoster(url, null, function(data) {
            if (data.success) {
                backIndexPage(currentProjectGroupId, true);
                bootGrowl(data.resultValue, 'success');
            } else {
                bootGrowl(data.resultValue, 'danger');
            }
        });
    }

    // ==================== 任务管理 ====================

    function bindAddMapping() {
        $('#indexAddMappingBtn').click(function() {
            var projectGroupId = $('#projectGroup').val() || '';
            updateHash('/mapping/pageAdd?projectGroupId=' + projectGroupId);
        });
    }

    function bindEditMapping() {
        $('.mapping-card').click(function(e) {
            var id = $(this).attr('data-id') || $(this).attr('id');
            if (id) {
                window.open('/#/mapping/page/edit?classOn=0&id=' + id, '_blank');
                if (refreshTimer) {
                    clearInterval(refreshTimer);
                    refreshTimer = null;
                }
            }
        });
    }

    function bindQueryData() {
        $('.mappingList .queryData').click(function(event) {
            event.cancelBubble = true;
            var $menu = $('#menu > li');
            $menu.removeClass('active');
            $menu.find("a[url='/monitor']").parent().addClass('active');

            var id = $(this).attr('id');
            updateHash('/monitor?dataStatus=0&id=' + id);
        });
    }

    // ==================== 搜索 ====================

    function bindMappingSearch() {
        $('#mappingSearchBtn').click(function() { performMappingSearch(); });
        $('#mappingSearchInput').keypress(function(e) {
            if (e.which === 13) {
                e.preventDefault();
                performMappingSearch();
            }
        });
        initFilterDropdowns();
    }

    function initFilterDropdowns() {
        $.ajax({
            url: '/datasource/list',
            type: 'POST',
            success: function(data) {
                if (data.success && data.status === 200) {
                    var connectors = data.resultValue;
                    var $sourceSelect = $('#filterSource');
                    var $targetSelect = $('#filterTarget');

                    $sourceSelect.find('option:not(:first)').remove();
                    $targetSelect.find('option:not(:first)').remove();

                    var connectorTypes = {};
                    if (Array.isArray(connectors)) {
                        connectors.forEach(function(connector) {
                            var type = connector.config && connector.config.connectorType ? connector.config.connectorType : 'default';
                            if (!connectorTypes[type]) connectorTypes[type] = [];
                            connectorTypes[type].push(connector);
                        });
                    }

                    for (var type in connectorTypes) {
                        if (connectorTypes.hasOwnProperty(type)) {
                            var option = '<option value="' + type + '">' + type + '</option>';
                            $sourceSelect.append(option);
                            $targetSelect.append(option);
                        }
                    }
                }
            },
            error: function() { bootGrowl('加载数据源列表失败', 'danger'); }
        });

        $('#filterState, #filterSource, #filterTarget').on('change', function() {
            performMappingSearch();
        });
    }

    function performMappingSearch() {
        var keyword = $('#mappingSearchInput').val().trim();
        var state = $('#filterState').val();
        var sourceConnectorType = $('#filterSource').val();
        var targetConnectorType = $('#filterTarget').val();

        if (!keyword && !state && !sourceConnectorType && !targetConnectorType) {
            loadTaskList();
            $('#noSearchResults').hide();
            return;
        }

        $.loadingT(true);
        $.ajax({
            url: '/index/searchMapping',
            type: 'GET',
            data: {
                keyword: keyword,
                projectGroupId: currentProjectGroupId,
                state: state,
                sourceConnectorType: sourceConnectorType,
                targetConnectorType: targetConnectorType
            },
            success: function(data) {
                if (stale()) return;
                $.loadingT(false);
                if (data.success && data.status === 200) {
                    var results = data.resultValue || [];
                    if (results.length === 0) {
                        taskList.setData([]);
                        $('#noSearchResults').show();
                    } else {
                        taskList.setData(results);
                        $('#noSearchResults').hide();
                    }
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

    // ==================== 视图切换 ====================

    function bindViewToggle() {
        $('#listViewBtn').click(function() {
            if (!$(this).hasClass('active')) {
                $(this).addClass('active');
                $('#cardViewBtn').removeClass('active');
                $('#listView').show();
                $('#cardView').hide();
                localStorage.setItem('dbsyncerViewMode', 'list');
            }
        });

        $('#cardViewBtn').click(function() {
            if (!$(this).hasClass('active')) {
                $('#listViewBtn').removeClass('active');
                $(this).addClass('active');
                $('#listView').hide();
                $('#cardView').show();
                localStorage.setItem('dbsyncerViewMode', 'card');
            }
        });

        var savedViewMode = localStorage.getItem('dbsyncerViewMode');
        if (savedViewMode === 'card') {
            $('#cardViewBtn').click();
        }
    }

    // ==================== 导出给 HTML 模板的 inline onclick ====================
    window.doEditProjectGroup = doEditProjectGroup;
    window.doRemoveProjectGroup = doRemoveProjectGroup;

    $(function() { init(); });

})(jQuery);
