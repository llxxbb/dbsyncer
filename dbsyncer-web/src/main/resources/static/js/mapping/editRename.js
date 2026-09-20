/**
 * 目标表重命名模块（从 edit.js 拆出，Stage 5）
 *
 * 职责：任务编辑页上「映射关系」Tab 中目标表重命名图标的点击交互——
 *       弹出模态框让用户输入新表名，调用 /tableGroup/edit 接口更新，
 *       如果目标表不存在则触发创建表流程。
 *
 * 依赖（全部是全局，不是模块内定义）：
 *   - jQuery $
 *   - bootGrowl                        common.js
 *   - doPoster                         common.js
 *   - showCreateTableConfirmDialogForMapping  edit.js（运行时调用，在 AJAX 回调内）
 *   - window._pendingRenameParams      全局变量（跨模块共享，供 retrySubmitMapping 使用）
 *
 * 入向调用点（唯一）：edit.js 的 bindMappingTableGroupListClick() 中调用
 *   bindTargetTableRenameClick()。因为是运行时回调（非求值期），加载顺序无严格要求。
 *
 * 本模块不创建任何定时器。
 */

// 绑定目标表重命名点击事件
function bindTargetTableRenameClick() {
    // 使用事件委托，支持动态添加的元素
    // 注意：需要绑定到tableGroupList容器上，而不是document，以确保事件能正确触发
    var $tableGroupList = $("#tableGroupList");
    if ($tableGroupList.length === 0) {
        // 如果tableGroupList不存在，使用document委托（作为备选方案）
        $(document).off('click', '.target-table-rename').on('click', '.target-table-rename', function(e) {
            e.stopPropagation();
            e.preventDefault();
            e.stopImmediatePropagation(); // 立即停止事件传播，防止其他处理器执行
            handleTargetTableRenameClick($(this));
            return false;
        });
    } else {
        $tableGroupList.off('click', '.target-table-rename').on('click', '.target-table-rename', function(e) {
            e.stopPropagation(); // 阻止事件冒泡，避免触发整行点击
            e.preventDefault(); // 阻止默认行为
            e.stopImmediatePropagation(); // 立即停止事件传播，防止其他处理器执行
            handleTargetTableRenameClick($(this));
            return false;
        });
    }
}

// 处理目标表重命名点击
function handleTargetTableRenameClick($icon) {
    var tableGroupId = $icon.data('id');
    var currentName = $icon.data('current-name');
    var targetTablePK = $icon.data('target-table-pk');
    
    if (!tableGroupId) {
        bootGrowl("无法获取表映射ID", "danger");
        return false;
    }
    
    if (!currentName) {
        bootGrowl("无法获取当前表名", "danger");
        return false;
    }
    
    // 显示自定义模态框
    showTargetTableRenameModal(tableGroupId, currentName, targetTablePK, $icon);
    return false;
}

// 显示目标表重命名模态框
function showTargetTableRenameModal(tableGroupId, currentName, targetTablePK, $icon) {
    // 创建模态框HTML（如果不存在）
    if ($('#targetTableRenameModal').length === 0) {
        var modalHtml = '<div class="modal fade" id="targetTableRenameModal" tabindex="-1" role="dialog">' +
            '<div class="modal-dialog" role="document">' +
            '<div class="modal-content">' +
            '<div class="modal-header">' +
            '<button type="button" class="close" data-dismiss="modal" aria-label="Close">' +
            '<span aria-hidden="true">&times;</span>' +
            '</button>' +
            '<h4 class="modal-title">重命名目标表</h4>' +
            '</div>' +
            '<div class="modal-body">' +
            '<div class="form-group">' +
            '<label for="targetTableRenameInput">目标表名称 <span class="text-danger">*</span></label>' +
            '<input type="text" class="form-control" id="targetTableRenameInput" placeholder="请输入目标表名称" maxlength="128">' +
            '<small class="help-block text-muted">表名格式校验由目标数据库服务端处理</small>' +
            '</div>' +
            '</div>' +
            '<div class="modal-footer">' +
            '<button type="button" class="btn btn-default" data-dismiss="modal">取消</button>' +
            '<button type="button" class="btn btn-primary" id="targetTableRenameConfirmBtn">确定</button>' +
            '</div>' +
            '</div>' +
            '</div>' +
            '</div>';
        $('body').append(modalHtml);
    }
    
    // 设置输入框的值
    $('#targetTableRenameInput').val(currentName);
    
    // 绑定确认按钮事件
    $('#targetTableRenameConfirmBtn').off('click').on('click', function() {
        var newName = $('#targetTableRenameInput').val().trim();
        if (!newName || newName === '') {
            bootGrowl("目标表名称不能为空", "danger");
            $('#targetTableRenameInput').focus();
            return;
        }
        if (newName === currentName) {
            $('#targetTableRenameModal').modal('hide');
            return;
        }
        
        // 关闭模态框
        $('#targetTableRenameModal').modal('hide');
        
        // 更新目标表名称
        updateTargetTableName(tableGroupId, newName, targetTablePK, $icon);
    });
    
    // 回车键确认
    $('#targetTableRenameInput').off('keypress').on('keypress', function(e) {
        if (e.which === 13) { // Enter键
            $('#targetTableRenameConfirmBtn').click();
        }
    });
    
    // 显示模态框并聚焦输入框
    $('#targetTableRenameModal').modal('show');
    setTimeout(function() {
        $('#targetTableRenameInput').focus().select();
    }, 500);
}

// 更新目标表名称
function updateTargetTableName(tableGroupId, newTableName, targetTablePK, $icon) {
    // 构建更新参数
    // 注意：不传 fieldMapping 参数，后端会自动映射全部源表字段
    var params = {
        'id': tableGroupId,
        'targetTable': newTableName,
        'targetTablePK': targetTablePK || ''
    };
    
    // 缓存重命名参数，供 createTargetTable 后 retrySubmitMapping 重试使用
    window._pendingRenameParams = { params: params, $icon: $icon, newTableName: newTableName };

    // 调用编辑API更新目标表名称
    doPoster("/tableGroup/edit", params, function (response) {
        if (response.success == true) {
            bootGrowl("目标表名称更新成功!", "success");
            // 只更新显示的名称，不刷新页面
            $icon.siblings('.target-table-name').text(newTableName);
            $icon.data('current-name', newTableName);
            window._pendingRenameParams = null;
        } else {
            // 检查是否是目标表不存在的异常
            if (response.status == 400 && response.resultValue &&
                typeof response.resultValue === 'object' &&
                response.resultValue.errorCode === 'TARGET_TABLE_NOT_EXISTS') {
                // 补充 fieldMapping 字段（重命名场景没有字段映射配置，创建时从源表自动映射）
                if (response.resultValue.missingTables) {
                    response.resultValue.missingTables.forEach(function(mt) {
                        if (!mt.fieldMapping) mt.fieldMapping = "";
                    });
                }
                showCreateTableConfirmDialogForMapping(response.resultValue);
            } else {
                bootGrowl(response.resultValue || "更新失败", "danger");
                window._pendingRenameParams = null;
            }
        }
    });
}
