/**
 * 异常任务监控页面 JavaScript
 */

// 当前任务数据缓存
var _abnormalTasks = [];

// 加载任务数据
function loadTasks() {
    $.loadingT(true);
    $.ajax({
        url: $basePath + '/index/abnormalTasks/api',
        type: 'GET',
        success: function(data) {
            if (data && data.success && data.resultValue) {
                _abnormalTasks = data.resultValue;
                renderTaskList(data.resultValue);
            } else {
                _abnormalTasks = [];
                $('#listViewBody').html('<tr><td colspan="8" style="text-align:center; padding: 30px; color: #999;">没有找到异常任务</td></tr>');
            }
            $.loadingT(false);
            updateToolbar();
        },
        error: function() {
            _abnormalTasks = [];
            $('#listViewBody').html('<tr><td colspan="8" style="text-align:center; padding: 30px; color: #d9534f;">获取数据失败</td></tr>');
            $.loadingT(false);
            updateToolbar();
        }
    });
}

// 渲染任务列表
function renderTaskList(tasks) {
    if (!tasks || tasks.length === 0) {
        $('#listViewBody').html('<tr><td colspan="8" style="text-align:center; padding: 30px; color: #999;">没有找到异常任务</td></tr>');
        return;
    }
    renderListView(tasks);
}

// 渲染列表视图
function renderListView(tasks) {
    var html = '';
    tasks.forEach(function(task) {
        var stateHtml = getStatusHtml(task);
        var failHtml = getFailHtml(task);

        html += '<tr data-task-id="' + task.id + '" onclick="goToDetail(\'' + task.id + '\')" style="cursor: pointer;">';
        html += '<td><input type="checkbox" class="task-checkbox" value="' + task.id + '" /></td>';
        html += '<td><span class="fa fa-link"></span> ' + (task.name || '-') + '</td>';
        html += '<td>' + stateHtml + '</td>';
        html += '<td>' + failHtml + '</td>';
        html += '<td>' + (task.sourceConnector ? (task.sourceConnector.name || '-') : '-') + '</td>';
        html += '<td>' + (task.targetConnector ? (task.targetConnector.name || '-') : '-') + '</td>';
        html += '<td>' + (task.meta ? (task.meta.total || 0) + ' / ' + (task.meta.success || 0) : '0 / 0') + '</td>';
        html += '<td>';
        html += '<a href="javascript:;" class="btn btn-xs btn-primary start-single-btn" data-id="' + task.id + '" title="启动"><i class="fa fa-play"></i> 启动</a>';
        html += '</td>';
        html += '</tr>';
    });
    $('#listViewBody').html(html);

    // 绑定行内复选框点击事件（阻止冒泡，避免触发行点击）
    $('.task-checkbox').off('click').on('click', function(e) {
        e.stopPropagation();
        updateSelectAllState();
        updateBatchBtnState();
    });

    // 绑定单独启动按钮（阻止冒泡，避免触发行点击）
    $('.start-single-btn').off('click').on('click', function(e) {
        e.stopPropagation();
        var id = $(this).data('id');
        startSingleTask(id, $(this));
    });
}

// 获取状态 HTML
function getStatusHtml(task) {
    if (task.meta?.state === 3) {
        return '<span class="label label-danger">异常</span>';
    }
    return '<span class="label label-default">运行中</span>';
}

// 获取失败数 HTML
function getFailHtml(task) {
    if (task.meta?.fail > 0) {
        return '<span style="color: #d9534f; font-weight: bold;">' + task.meta.fail + '</span>';
    }
    return '0';
}

// 跳转到任务详情页面
function goToDetail(taskId) {
    if (!taskId) return;
    window.open('/#/mapping/page/edit?id=' + taskId, '_blank');
}

// 单独启动任务
function startSingleTask(id, $btn) {
    BootstrapDialog.show({
        title: '确认',
        type: BootstrapDialog.TYPE_PRIMARY,
        message: '确认启动该任务？',
        size: BootstrapDialog.SIZE_NORMAL,
        buttons: [{
            label: '确定',
            action: function(dialog) {
                $btn.prop('disabled', true).find('i').removeClass('fa-play').addClass('fa-spinner fa-spin');
                doPoster('/mapping/start?id=' + id, null, function(data) {
                    if (data.success) {
                        bootGrowl(data.resultValue || '启动成功', 'success');
                        // 刷新列表
                        loadTasks();
                    } else {
                        bootGrowl(data.resultValue || '启动失败', 'danger');
                        $btn.prop('disabled', false).find('i').removeClass('fa-spinner fa-spin').addClass('fa-play');
                    }
                });
                dialog.close();
            }
        }, {
            label: '取消',
            action: function(dialog) {
                dialog.close();
            }
        }]
    });
}

// 批量启动选中的任务
function batchStartTasks() {
    var selectedIds = [];
    $('.task-checkbox:checked').each(function() {
        selectedIds.push($(this).val());
    });
    if (selectedIds.length === 0) {
        bootGrowl('请先选择要启动的任务', 'warning');
        return;
    }

    BootstrapDialog.show({
        title: '确认',
        type: BootstrapDialog.TYPE_PRIMARY,
        message: '确认批量启动选中的 ' + selectedIds.length + ' 个任务？',
        size: BootstrapDialog.SIZE_NORMAL,
        buttons: [{
            label: '确定',
            action: function(dialog) {
                $('#batchStartBtn').prop('disabled', true);
                doPoster('/mapping/batchStart', { ids: selectedIds.join(',') }, function(data) {
                    if (data.success) {
                        bootGrowl(data.resultValue || '批量启动完成', 'success');
                        // 刷新列表
                        loadTasks();
                    } else {
                        bootGrowl(data.resultValue || '批量启动失败', 'danger');
                        $('#batchStartBtn').prop('disabled', false);
                    }
                });
                dialog.close();
            }
        }, {
            label: '取消',
            action: function(dialog) {
                dialog.close();
            }
        }]
    });
}

// 全选/取消全选
function toggleSelectAll() {
    var allChecked = $('#selectAllCheckbox').prop('checked');
    $('.task-checkbox').prop('checked', allChecked);
    updateBatchBtnState();
}

// 更新全选复选框状态
function updateSelectAllState() {
    var total = $('.task-checkbox').length;
    var checked = $('.task-checkbox:checked').length;
    $('#selectAllCheckbox').prop('checked', total > 0 && total === checked);
}

// 更新批量启动按钮状态
function updateBatchBtnState() {
    var checkedCount = $('.task-checkbox:checked').length;
    $('#batchStartBtn').prop('disabled', checkedCount === 0);
    if (checkedCount > 0) {
        $('#selectedCountLabel').text('已选 ' + checkedCount + ' 个任务');
    } else {
        $('#selectedCountLabel').text('');
    }
}

// 更新工具栏显示
function updateToolbar() {
    if (_abnormalTasks.length > 0) {
        $('#abnormalToolbar').css('display', 'flex');
    } else {
        $('#abnormalToolbar').hide();
    }
}

$(function () {
    loadTasks();
    $('#listView').show();

    // 全选复选框
    $('#selectAllCheckbox').on('click', function(e) {
        e.stopPropagation();
        toggleSelectAll();
    });

    // 全选按钮
    $('#selectAllBtn').on('click', function() {
        var allChecked = $('.task-checkbox').length > 0 && $('.task-checkbox:checked').length === $('.task-checkbox').length;
        $('.task-checkbox').prop('checked', !allChecked);
        $('#selectAllCheckbox').prop('checked', !allChecked);
        updateBatchBtnState();
    });

    // 批量启动按钮
    $('#batchStartBtn').on('click', function() {
        batchStartTasks();
    });
});
