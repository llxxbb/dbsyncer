/**
 * 异常任务监控页面 JavaScript
 */

// 防止重复加载的标志
if (typeof window.__abnormalTasksInitialized === 'undefined') {
    window.__abnormalTasksInitialized = false;
}

// 加载任务数据
function loadTasks() {
    $.loadingT(true);
    $.ajax({
        url: 'index/abnormalTasks/api',
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
        $('#cardViewContainer').html('<div style="text-align:center; padding: 50px; color: #999;">没有找到异常任务</div>');
        return;
    }
    renderListView(tasks);
    renderCardView(tasks);
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

// 渲染卡片视图
function renderCardView(tasks) {
    var html = '';
    tasks.forEach(function(task) {
        var stateIndicator = '';
        if (task.meta && task.meta.state === 3) {
            stateIndicator += '<span class="fa fa-exclamation-triangle text-danger" title="任务状态异常"></span> ';
        }
        if (task.meta && task.meta.fail > 0) {
            stateIndicator += '<span class="fa fa-exclamation-triangle text-warning" title="失败记录：' + task.meta.fail + '"></span> ' + task.meta.fail;
        }

        html += '<div class="mapping-card" style="margin-bottom: 15px; cursor: pointer;" onclick="goToDetail(\'' + task.id + '\')">';
        html += '  <div class="panel panel-default">';
        html += '    <div class="panel-heading">';
        html += '      <h4 class="panel-title"><span class="fa fa-link"></span> ' + (task.name || '-') + '</h4>';
        html += '    </div>';
        html += '    <div class="panel-body">';
        html += '      <div style="margin-bottom: 8px; display: flex; align-items: center;">';
        html += '        <span style="margin-right: 10px;">' + (task.sourceConnector && task.sourceConnector.name ? task.sourceConnector.name : '-') + ' → ' + (task.targetConnector && task.targetConnector.name ? task.targetConnector.name : '-') + '</span>';
        html += '        <span style="font-size: 11px;">' + (task.meta && task.meta.total ? task.meta.total : 0) + ' / ' + (task.meta && task.meta.success ? task.meta.success : 0) + '</span>';
        html += '      </div>';
        html += '      <div style="margin-top: 8px;">';
        html += '        ' + (stateIndicator || '<span class="text-success">正常</span>') + '';
        html += '      </div>';
        html += '    </div>';
        html += '  </div>';
        html += '</div>';
    });
    $('#cardViewContainer').html(html);
}

// 列表视图切换
function showListView() {
    $('#listView').show();
    $('#cardView').hide();
    $('#listViewBtn').addClass('active');
    $('#cardViewBtn').removeClass('active');
    savePreference('list');
}

// 卡片视图切换
function showCardView() {
    $('#listView').hide();
    $('#cardView').show();
    $('#listViewBtn').removeClass('active');
    $('#cardViewBtn').addClass('active');
    savePreference('card');
}

// 保存视图偏好
function savePreference(view) {
    localStorage.setItem('abnormal-tasks-view', view);
}

// 获取视图偏好
function getPreference() {
    return localStorage.getItem('abnormal-tasks-view') || 'card';
}

// 跳转到详情页面（编辑页面）
function goToDetail(taskId) {
    if (!taskId) return;
    if (typeof updateHash !== 'undefined') {
        updateHash('/mapping/page/edit?id=' + taskId);
    } else {
        window.location.hash = '(mapping/page/edit?id=' + taskId + ')';
    }
}

// 页面自动初始化（函数声明已提升，脚本执行时 DOM 已就绪）
if (!window.__abnormalTasksInitialized) {
    window.__abnormalTasksInitialized = true;
    loadTasks();
    var preference = getPreference();
    if (preference === 'list') {
        showListView();
    } else {
        showCardView();
    }
}
