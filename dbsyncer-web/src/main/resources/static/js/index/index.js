/**
 * 任务列表页面（主页）
 * 使用统一 TaskList 模块渲染任务列表
 */
(function ($) {
    'use strict';

    var taskList = null;
    var refreshTimer = null;
    var currentProjectGroupId = '';

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

        // 从 URL 获取 projectGroupId
        currentProjectGroupId = getUrlParam('projectGroupId');
        if (currentProjectGroupId) {
            $('#projectGroup').val(currentProjectGroupId);
        }

        loadTaskList();
        startAutoRefresh();
    }

    function loadTaskList() {
        $.loadingT(true);
        $.ajax({
            url: '/index/mappingdata',
            type: 'GET',
            data: { projectGroupId: currentProjectGroupId },
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
                refreshTimer = setInterval(loadTaskList, interval * 1000);
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
        updateHash('/index?projectGroupId=' + currentProjectGroupId, 1);
        loadTaskList();
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
            updateHash('/monitor?dataStatus=0&id=' + id, 2);
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

    // ==================== 工具函数 ====================

    function getUrlParam(name) {
        name = name.replace(/[\[]/, '\\[').replace(/[\]]/, '\\]');
        var regex = new RegExp('[\\#&]' + name + '=([^&#]*)');
        var results = regex.exec(window.location.href);
        return results === null ? '' : decodeURIComponent(results[1].replace(/\+/g, ' '));
    }

    // 暴露给 HTML 模板的 inline onclick 调用
    window.groupShow = groupShow;
    window.doEditProjectGroup = doEditProjectGroup;
    window.doRemoveProjectGroup = doRemoveProjectGroup;

    // 清理定时器
    $(window).on('beforeunload', function() {
        if (refreshTimer) {
            clearInterval(refreshTimer);
            refreshTimer = null;
        }
    });

    $(function() { init(); });

})(jQuery);
