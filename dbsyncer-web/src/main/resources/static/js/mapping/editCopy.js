/**
 * 复制驱动模块（从 edit.js 拆出，Stage 1）
 *
 * 职责：任务编辑页上「复制」按钮的弹窗交互——拉取当前任务配置、选择新名称/同步类型/目标源，
 *       确认后调用 /mapping/copy 接口完成复制。
 *
 * 依赖（全部是全局，不是模块内定义）：
 *   - jQuery $
 *   - BootstrapDialog    插件（bootstrap-dialog）
 *   - bootGrowl          common.js
 *   - backIndexPage      common.js
 *   - doPoster           common.js
 *
 * 入向调用点（唯一）：edit.js 的 bindEditPageOperationButtons() 中 click 回调内调用
 *   showCopyMappingDialog()。因为是运行时回调（非求值期），加载顺序无严格要求。
 *
 * 本模块不声明任何全局变量，不创建任何定时器。
 */

// 显示复制任务配置弹窗
function showCopyMappingDialog(mappingId) {
    var currentMappingName = $("#mappingModifyName").text().trim();
    var currentModel = $('input[name="model"]').val();
    var currentTargetConnectorId = '';
    var currentTargetConnectorName = '';
    
    // 获取当前目标源信息
    var $targetDetailBtn = $('.target-detail-btn');
    if ($targetDetailBtn.length > 0) {
        currentTargetConnectorId = $targetDetailBtn.data('id') || '';
    }
    var $targetName = $('.col-md-4').eq(2).find('.driver_break_word').first().text().trim();
    if ($targetName) {
        currentTargetConnectorName = $targetName;
    }
    
    // 获取所有可用的连接器
    $.ajax({
        url: '/datasource/list',
        type: 'POST',
        success: function(response) {
            if (response.success && response.resultValue) {
                var connectors = response.resultValue;
                var connectorOptions = '';
                
                connectors.forEach(function(connector) {
                    var selected = connector.id === currentTargetConnectorId ? 'selected' : '';
                    connectorOptions += '<option value="' + connector.id + '" ' + selected + '>' + 
                        connector.name + ' (' + connector.config.connectorType + ')</option>';
                });
                
                var content = '<div style="padding: 15px;">' +
                    '<div class="form-group">' +
                    '<label for="copyMappingName">任务名称 <span class="text-danger">*</span></label>' +
                    '<input type="text" class="form-control" id="copyMappingName" value="' + currentMappingName + '(复制)" maxlength="64" placeholder="请输入新任务名称">' +
                    '</div>' +
                    '<div class="form-group">' +
                    '<label for="copyModel">同步类型</label>' +
                    '<select class="form-control" id="copyModel">' +
                    '<option value="full"' + (currentModel === 'full' ? ' selected' : '') + '>全量同步</option>' +
                    '<option value="increment"' + (currentModel === 'increment' ? ' selected' : '') + '>增量同步</option>' +
                    '<option value="fullIncrement"' + (currentModel === 'fullIncrement' ? ' selected' : '') + '>全量+增量</option>' +
                    '</select>' +
                    '</div>' +
                    '<div class="form-group">' +
                    '<label for="copyTargetConnector">目标源</label>' +
                    '<select class="form-control" id="copyTargetConnector">' +
                    '<option value="">保持原目标源 (' + currentTargetConnectorName + ')</option>' +
                    connectorOptions +
                    '</select>' +
                    '<small class="help-block text-muted">选择新的目标源后，复制时会保留原映射关系配置，如果新目标源中不存在对应的表，系统会自动创建</small>' +
                    '</div>' +
                    '</div>';
                
                BootstrapDialog.show({
                    title: '复制任务',
                    type: BootstrapDialog.TYPE_PRIMARY,
                    message: content,
                    size: BootstrapDialog.SIZE_NORMAL,
                    buttons: [{
                        label: '取消',
                        cssClass: 'btn-default',
                        action: function(dialog) {
                            dialog.close();
                        }
                    }, {
                        label: '确认复制',
                        cssClass: 'btn-primary',
                        action: function(dialog) {
                            var newName = $('#copyMappingName').val().trim();
                            var model = $('#copyModel').val();
                            var targetConnectorId = $('#copyTargetConnector').val();
                            
                            if (!newName) {
                                bootGrowl('请输入任务名称', 'danger');
                                return;
                            }
                            
                            dialog.close();
                            executeCopyMapping(mappingId, newName, model, targetConnectorId);
                        }
                    }]
                });
                
                // 绑定回车键确认
                setTimeout(function() {
                    $('#copyMappingName').off('keypress').on('keypress', function(e) {
                        if (e.which === 13) {
                            $('.btn-primary:last').click();
                        }
                    });
                }, 100);
            } else {
                bootGrowl('获取连接器列表失败', 'danger');
            }
        },
        error: function() {
            bootGrowl('获取连接器列表失败', 'danger');
        }
    });
}

// 执行复制任务
function executeCopyMapping(mappingId, newName, model, targetConnectorId) {
    var params = {
        id: mappingId
    };
    
    if (newName) {
        params.name = newName;
    }
    if (model) {
        params.model = model;
    }
    if (targetConnectorId) {
        params.targetConnectorId = targetConnectorId;
    }
    
    bootGrowl('正在复制任务...', 'info');
    
    doPoster('/mapping/copy', params, function(response) {
        if (response.success) {
            bootGrowl(response.resultValue, 'success');
            setTimeout(function() {
                backIndexPage();
            }, 1000);
        } else {
            bootGrowl(response.resultValue || '复制失败', 'danger');
        }
    });
}
