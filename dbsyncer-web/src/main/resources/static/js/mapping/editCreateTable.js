/**
 * 目标表创建模块（从 edit.js 拆出，Stage 6）
 *
 * 职责：当保存映射关系或重命名目标表时，如果目标表不存在，弹出确认对话框
 *       让用户选择是否自动创建目标表，创建完成后重试保存操作。
 *
 * 依赖（全部是全局，不是模块内定义）：
 *   - jQuery $
 *   - BootstrapDialog         插件（bootstrap-dialog）
 *   - bootGrowl               common.js
 *   - doPoster                common.js
 *   - submit                  edit.js（运行时调用，在回调内）
 *   - window._pendingRenameParams  全局变量（跨模块共享）
 *
 * 入向调用点（两个）：
 *   - edit.js 的 submit() 中调用 showCreateTableConfirmDialogForMapping()
 *   - editRename.js 的 updateTargetTableName() 中调用 showCreateTableConfirmDialogForMapping()
 *   两者都是运行时回调（非求值期），加载顺序无严格要求。
 *
 * 本模块不声明任何全局变量（window._pendingRenameParams 是跨模块共享的）。
 */

/**
 * 显示创建表确认对话框（保存 mapping 场景）
 * @param errorInfo 错误信息对象，包含 errorCode, message, sourceTable, targetTable, missingTables 等
 */
function showCreateTableConfirmDialogForMapping(errorInfo) {
    let messageHtml = '<div style="padding: 10px;">';
    let missingTables = errorInfo.missingTables || [];
    let isMultiple = missingTables.length > 1;
    
    // 检查是否有多个缺失的表
    if (isMultiple) {
        // 多个表场景
        messageHtml += '<p><strong>以下 ' + missingTables.length + ' 个目标表不存在：</strong></p>';
        messageHtml += '<ul style="margin: 10px 0; padding-left: 20px; max-height: 300px; overflow-y: auto;">';
        for (let i = 0; i < missingTables.length; i++) {
            let mapping = missingTables[i];
            messageHtml += '<li>源表: <strong>' + mapping.sourceTable + '</strong> → 目标表: <strong>' + mapping.targetTable + '</strong></li>';
        }
        messageHtml += '</ul>';
        messageHtml += '<p>是否基于源表结构自动创建这些目标表？</p>';
    } else {
        // 单个表场景
        let mapping = missingTables[0] || null;
        if (mapping) {
            messageHtml += '<p><strong>目标表不存在：</strong>' + mapping.targetTable + '</p>';
            messageHtml += '<p style="color: #999; font-size: 12px;">源表：' + mapping.sourceTable + '</p>';
            messageHtml += '<p>是否基于源表结构自动创建目标表？</p>';
        }
    }
    
    messageHtml += '</div>';
    
    let dialog = BootstrapDialog.show({
        title: "目标表不存在",
        type: BootstrapDialog.TYPE_WARNING,
        message: messageHtml,
        size: BootstrapDialog.SIZE_NORMAL,
        buttons: [{
            label: "取消",
            cssClass: "btn-default",
            action: function (dialog) {
                dialog.close();
            }
        }, {
            label: isMultiple ? "批量创建表" : "创建表",
            cssClass: "btn-primary",
            action: function (dialog) {
                dialog.close();
                createTargetTablesAndRetry(errorInfo);
            }
        }]
    });
}

/**
 * 创建目标表并重试保存 mapping
 * @param errorInfo 错误信息对象，包含 missingTables
 */
function createTargetTablesAndRetry(errorInfo) {
    let missingTables = errorInfo.missingTables || [];
    if (missingTables.length === 0) {
        bootGrowl("没有需要创建的表", "warning");
        return;
    }
    
    let mappingId = $("#mappingId").val();
    if (!mappingId) {
        bootGrowl("无法获取映射ID", "danger");
        return;
    }
    
    // 显示加载提示
    let isMultiple = missingTables.length > 1;
    bootGrowl(isMultiple ? "正在批量创建目标表..." : "正在创建目标表...", "info");
    
    // 批量创建表（使用回调方式）
    let successCount = 0;
    let failCount = 0;
    let failMessages = [];
    let completedCount = 0;
    let totalCount = missingTables.length;
    
    // 如果没有表需要创建，直接返回
    if (totalCount === 0) {
        bootGrowl("没有需要创建的表", "warning");
        return;
    }
    
    // 逐个创建表
    function createNextTable(index) {
        if (index >= totalCount) {
            // 所有表创建完成，处理结果
            handleCreateResult();
            return;
        }
        
        let mapping = missingTables[index];
        let createParams = {
            mappingId: mappingId,
            sourceTable: mapping.sourceTable,
            targetTable: mapping.targetTable,
            targetTablePK: mapping.targetTablePK,
            fieldMapping: mapping.fieldMapping || ""
        };
        
        doPoster("/tableGroup/createTargetTable", createParams, function (data) {
            completedCount++;
            if (data.success == true) {
                successCount++;
            } else {
                failCount++;
                let errorMsg = typeof data.resultValue === 'string' 
                    ? data.resultValue 
                    : (data.resultValue && data.resultValue.message ? data.resultValue.message : '创建表失败');
                failMessages.push(mapping.targetTable + ": " + errorMsg);
            }
            
            // 继续创建下一个表
            createNextTable(index + 1);
        });
    }
    
    // 处理创建结果
    function handleCreateResult() {
        if (failCount === 0) {
            bootGrowl("成功创建 " + successCount + " 个目标表，正在保存映射关系...", "success");
            // 所有表创建成功，重新提交保存 mapping
            setTimeout(function() {
                retrySubmitMapping();
            }, 500);
        } else {
            let message = "创建表完成：成功 " + successCount + " 个，失败 " + failCount + " 个";
            if (failMessages.length > 0) {
                message += "\n失败详情：\n" + failMessages.join("\n");
            }
            bootGrowl(message, failCount === totalCount ? "danger" : "warning");
            
            // 如果有部分成功，仍然尝试保存（后端会再次检查表是否存在）
            if (successCount > 0) {
                setTimeout(function() {
                    retrySubmitMapping();
                }, 1000);
            }
        }
    }
    
    // 开始创建第一个表
    createNextTable(0);
}

/**
 * 重试提交保存 mapping
 */
function retrySubmitMapping() {
    // 如果有待重试的重命名操作，先重试重命名
    if (window._pendingRenameParams) {
        var renameParams = window._pendingRenameParams;
        window._pendingRenameParams = null;
        doPoster("/tableGroup/edit", renameParams.params, function (response) {
            if (response.success == true) {
                bootGrowl("目标表名称更新成功!", "success");
                if (renameParams.$icon) {
                    renameParams.$icon.siblings('.target-table-name').text(renameParams.newTableName);
                    renameParams.$icon.data('current-name', renameParams.newTableName);
                }
            } else {
                // 如果仍然失败，回退到表单提交
                window._pendingRenameParams = renameParams;
                doFallbackRetrySubmit();
            }
        });
        return;
    }
    doFallbackRetrySubmit();
}

function doFallbackRetrySubmit() {
    let $form = $("#mappingModifyForm");
    if ($form.length === 0) {
        bootGrowl("无法找到表单", "danger");
        return;
    }
    
    if ($form.formValidate() == true) {
        let data = $form.serializeJson();
        submit(data);
    } else {
        bootGrowl("表单验证失败", "danger");
    }
}
