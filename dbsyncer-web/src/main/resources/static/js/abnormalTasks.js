/**
 * 异常任务监控页面 JavaScript
 */

// 加载任务数据
function loadTasks() {
    $.loadingT(true);
    $.ajax({
        url: $basePath + '/index/abnormalTasks/api',
        type: 'GET',
        success: function(data) {
            if (data && data.success && data.resultValue) {
                renderTaskList(data.resultValue);
            } else {
                $('#listViewBody').html('<tr><td colspan="6" style="text-align:center; padding: 30px; color: #999;">没有找到异常任务</td></tr>');
                $('#cardViewContainer').html('<div style="text-align:center; padding: 50px; color: #999;">没有找到异常任务</div>');
            }
            $.loadingT(false);
        },
        fail: function() {
            $('#listViewBody').html('<tr><td colspan="6" style="text-align:center; padding: 30px; color: #d9534f;">获取数据失败</td></tr>');
            $('#cardViewContainer').html('<div style="text-align:center; padding: 50px; color: #d9534f;">获取数据失败</div>');
            $.loadingT(false);
        }
    });
}

// 渲染任务列表
function renderTaskList(tasks) {
    if (!tasks || tasks.length === 0) {
        $('#listViewBody').html('<tr><td colspan="6" style="text-align:center; padding: 30px; color: #999;">没有找到异常任务</td></tr>');
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

        html += '<tr onclick="goToDetail(\'' + task.id + '\')" style="cursor: pointer;">';
        html += '<td><span class="fa fa-link"></span> ' + (task.name || '-') + '</td>';
        html += '<td>' + stateHtml + '</td>';
        html += '<td>' + failHtml + '</td>';
        html += '<td>' + (task.sourceConnector ? (task.sourceConnector.name || '-') : '-') + '</td>';
        html += '<td>' + (task.targetConnector ? (task.targetConnector.name || '-') : '-') + '</td>';
        html += '<td>' + (task.meta ? (task.meta.total || 0) + ' / ' + (task.meta.success || 0) : '0 / 0') + '</td>';
        html += '</tr>';
    });
    $('#listViewBody').html(html);
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


// 跳转到详情页面（编辑页面）
function goToDetail(taskId) {
    if (!taskId) return;
    window.open('/#/mapping/page/edit?id=' + taskId, '_blank');
}

$(function () {
    loadTasks();
    $('#listView').show();

});
