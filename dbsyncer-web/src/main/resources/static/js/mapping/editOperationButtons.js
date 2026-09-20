/**
 * 操作按钮模块（从 edit.js 拆出，Stage 7）
 *
 * 职责：任务编辑页顶部操作按钮（复制/删除/重置/启动/停止）的点击交互——
 *       确认对话框、清除目标表数据选项、POST 请求处理及页面刷新。
 *
 * 依赖（全部是全局，不是模块内定义）：
 *   - jQuery $
 *   - BootstrapDialog         插件（bootstrap-dialog）
 *   - doPoster                common.js
 *   - bootGrowl               common.js
 *   - backIndexPage           common.js
 *   - mappingPoller           edit.js（运行时调用，在回调内）
 *   - refresh                 edit.js（运行时调用，在回调内）
 *   - showCopyMappingDialog   editCopy.js（运行时调用，在 click 回调内）
 *
 * 入向调用点（唯一）：edit.js 的 ready 块里直接调用 bindEditPageOperationButtons()。
 * ★ 因此本文件必须在 edit.js **之前**加载：SPA 外壳中 jQuery 的 ready 回调
 *   是同步执行的，晚加载会 ReferenceError。
 *
 * 本模块不声明任何全局变量，不创建任何定时器。
 */

// 绑定编辑页面操作按钮事件
function bindEditPageOperationButtons() {
    $('.mapping-operation-buttons a').click(function (e) {
        e.preventDefault();
        e.stopPropagation();

        var $url = $(this).data('url') || $(this).attr("th:url") || $(this).attr("href");
        $url = $url ? $url.replace('javascript:;', '') : '';
        var $confirm = $(this).attr("confirm");
        var $confirmMessage = $(this).attr("confirmMessage");
        var $isReset = $url && $url.indexOf('/mapping/reset') !== -1;
        var $isCopy = $url && $url.indexOf('/mapping/copy?id=') !== -1;

        // 如果是复制按钮，显示复制配置弹窗
        if ($isCopy) {
            var mappingId = $url.split('id=')[1];
            showCopyMappingDialog(mappingId);
            return;
        }

        if ("true" == $confirm) {
            BootstrapDialog.show({
                title: "警告",
                type: BootstrapDialog.TYPE_DANGER,
                message: $confirmMessage,
                size: BootstrapDialog.SIZE_NORMAL,
                buttons: [{
                    label: "确定",
                    action: function (dialog) {
                        dialog.close();
                        // 如果是重置按钮，显示第二个确认对话框
                        if ($isReset) {
                            showTruncateTargetConfirmDialog($url);
                        } else {
                            doPostForEditPage($url);
                        }
                    }
                }, {
                    label: "取消",
                    action: function (dialog) {
                        dialog.close();
                    }
                }]
            });
            return;
        }

        doPostForEditPage($url);
    });

    // 绑定启动/停止按钮事件
    $('#mappingStartBtn, #mappingStopBtn').click(function (e) {
        e.preventDefault();
        e.stopPropagation();

        var $url = $(this).data('url');
        if ($url) {
            doPostForEditPage($url);
        }
    });
}

// 显示清除目标源表确认对话框
function showTruncateTargetConfirmDialog(resetUrl) {
    BootstrapDialog.show({
        title: "清除目标源表",
        type: BootstrapDialog.TYPE_WARNING,
        message: '<div style="padding: 10px;">' +
                 '<p><strong>是否同时清除目标源表数据？</strong></p>' +
                 '<p style="color: #999; font-size: 12px;">选择"是"将使用 TRUNCATE 清空所有目标源表的数据</p>' +
                 '<p style="color: #999; font-size: 12px;">选择"否"仅重置任务状态，不清除目标源表数据</p>' +
                 '</div>',
        size: BootstrapDialog.SIZE_NORMAL,
        buttons: [{
            label: "是，清除目标表",
            cssClass: "btn-danger",
            action: function (dialog) {
                dialog.close();
                doPostForEditPage(resetUrl + '&truncateTarget=true');
            }
        }, {
            label: "否，仅重置状态",
            cssClass: "btn-primary",
            action: function (dialog) {
                dialog.close();
                doPostForEditPage(resetUrl + '&truncateTarget=false');
            }
        }, {
            label: "取消",
            cssClass: "btn-default",
            action: function (dialog) {
                dialog.close();
            }
        }]
    });
}

// 编辑页面专用的POST请求处理
function doPostForEditPage(url) {
    doPoster(url, null, function (data) {
        if (data.success == true) {
            var projectGroup = $("#projectGroup").val() || '';
            bootGrowl(data.resultValue, "success");
            
            if (url.indexOf('/mapping/remove') !== -1) {
                // 停止轮询，避免删除后仍尝试获取任务状态
                mappingPoller.stop();
                backIndexPage(projectGroup);
            } else {
                var mappingId = $("#mappingId").val();
                if (mappingId) {
                    // 启动/停止操作后，根据当前激活的标签页设置classOn值
                    // 获取当前激活的标签页ID
                    var activeTabId = $('.tab-pane.active').attr('id');
                    var classOnValue = 0;
                    // 映射关系标签页
                    if (activeTabId === 'mappingBaseConfig') {
                        classOnValue = 1;
                    }
                    // 高级配置标签页
                    else if (activeTabId === 'mappingSuperConfig') {
                        classOnValue = 2;
                    }
                    // 错误队列标签页
                    else if (activeTabId === 'mappingErrorQueue') {
                        classOnValue = 3;
                    }
                    // 概括标签页（mappingSummary）或其他情况，保持classOn=0
                    refresh(mappingId, classOnValue);
                }
            }
        } else {
            bootGrowl(data.resultValue, "danger");
        }
    });
}
