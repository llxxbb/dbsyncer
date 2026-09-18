/**
 * 数据源详情弹窗模块（从 edit.js 拆出，Stage 3/5）
 *
 * 职责：任务编辑页上「源/目标数据源」详情按钮的绑定，拉取连接器信息并渲染成
 *      BootstrapDialog 弹窗（配置项做中文标签映射）。
 *
 * 依赖（全部是全局，不是模块内定义）：
 *   - jQuery $
 *   - BootstrapDialog    插件（bootstrap-dialog）
 *   - bootGrowl          common.js
 *   - escapeHtml         ★ 定义在 editFilterAndConvert.js，本模块只调用不定义。
 *                        Stage 0 删掉了 edit.js 里那个较弱的同名实现（DOM 法，
 *                        不转义引号），现在页面内 escapeHtml 唯一来源就是
 *                        editFilterAndConvert.js 的正则版（转义 & < > " '）。
 *                        因为本模块不定义它，所以与它的加载先后无关；
 *                        但谁要是在这里再加一个 escapeHtml，就会重现覆盖问题。
 *                        escapeHtml 只在点击后的 renderConnectorDetailDialog 里调用，
 *                        属运行时解析，所以本文件早于 editFilterAndConvert.js 加载没问题。
 *
 * 入向调用点（唯一）：edit.js 的 ready 块里调用 bindConnectorDetailBtnClick()。
 * ★ 因此本文件必须在 edit.js **之前**加载：SPA 外壳中 document 早已 complete，
 *   jQuery 1.11.3 的 ready Deferred 是 once-memory，edit.js 求值时其 ready 回调
 *   同步执行，此时若本文件尚未加载就会 ReferenceError。
 *   （jsdom 验证台已实测复现过这个错误。）
 *
 * 本模块不创建任何定时器，不涉及 ADR-0032 的「定时器句柄局部持有」约束。
 *
 * 已知事实（拆分不改变，仅记录）：showConnectorDetail 用直连 $.ajax 且 URL 是
 * 裸相对路径 '/datasource/connector/get'，没有走 common.js 的 doGetter（后者会
 * 拼 $basePath）。应用部署在非根 context-path 下时这个请求会 404 —— 这是 edit.js
 * 里多处共有的历史问题，不是本模块引入的。
 */

//*********************************** 数据源详情 开始位置***********************************//
function bindConnectorDetailBtnClick() {
    $(document).off('click', '.source-detail-btn, .target-detail-btn').on('click', '.source-detail-btn, .target-detail-btn', function(e) {
        e.preventDefault();
        e.stopPropagation();
        var connectorId = $(this).data('id');
        if (connectorId) {
            showConnectorDetail(connectorId);
        } else {
            bootGrowl('无法获取数据源ID', 'danger');
        }
    });
}

function showConnectorDetail(connectorId) {
    $.ajax({
        url: '/datasource/connector/get?id=' + connectorId,
        type: 'GET',
        dataType: 'json',
        success: function(data) {
            if (data.success === true) {
                renderConnectorDetailDialog(data.resultValue);
            } else {
                var errorMsg = typeof data.resultValue === 'string' ? data.resultValue : JSON.stringify(data.resultValue);
                bootGrowl('加载数据源详情失败：' + errorMsg, 'danger');
            }
        },
        error: function() {
            bootGrowl('加载数据源详情失败', 'danger');
        }
    });
}

function renderConnectorDetailDialog(connector) {
    try {
        var content = '<style>.connector-detail-dialog { width: 100%; } .connector-detail-dialog table { width: 100%; table-layout: fixed; } .connector-detail-dialog td { word-break: break-all !important; word-wrap: break-word !important; overflow-wrap: break-word !important; white-space: normal !important; }</style>';
        content += '<div class="connector-detail-dialog">';
        content += '<table class="table table-bordered table-striped">';
        content += '<tbody>';
        
        content += '<tr><td style="width: 120px; font-weight: bold; white-space: nowrap;">数据源名称</td><td>' + escapeHtml(connector.name) + '</td></tr>';
        content += '<tr><td style="font-weight: bold; white-space: nowrap;">数据源类型</td><td>' + escapeHtml(connector.type) + '</td></tr>';
        
        if (connector.config && typeof connector.config === 'object') {
            for (var key in connector.config) {
                if (connector.config.hasOwnProperty(key)) {
                    if (key === 'password') {
                        continue;
                    }
                    var value = connector.config[key];
                    if (typeof value === 'object') {
                        value = JSON.stringify(value);
                    }
                    content += '<tr><td style="font-weight: bold; white-space: nowrap;">' + getConfigLabel(key) + '</td><td>' + escapeHtml(value) + '</td></tr>';
                }
            }
        }
        
        content += '</tbody>';
        content += '</table>';
        content += '</div>';
        
        BootstrapDialog.show({
            title: '数据源详情',
            size: BootstrapDialog.SIZE_WIDE,
            message: content,
            type: BootstrapDialog.TYPE_PRIMARY,
            buttons: [{
                label: '关闭',
                action: function(dialog) {
                    dialog.close();
                }
            }]
        });
    } catch (e) {
        bootGrowl('渲染数据源详情失败: ' + e.message, 'danger');
    }
}

function getConfigLabel(key) {
    var labels = {
        'username': '用户名',
        'password': '密码',
        'url': '数据源URL',
        'host': '主机',
        'port': '端口',
        'database': '数据库名',
        'driverClassName': '驱动类名',
        'connectorType': '连接器类型',
        'schema': 'Schema'
    };
    return labels[key] || key;
}

//*********************************** 数据源详情 结束位置***********************************//

//# sourceURL=/js/mapping/editConnectorDetail.js
