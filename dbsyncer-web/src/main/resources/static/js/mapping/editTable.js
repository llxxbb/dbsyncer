/**
 * 表组列表模块（从 edit.js 拆出，Stage 9）
 *
 * 职责：任务编辑页「映射关系 Tab」中表组列表的全部交互——
 *       复选框（全选/单选/计数）、行点击跳转、拖拽排序、
 *       删除、重新同步（含确认弹窗+清空选项）、刷新表、
 *       概况 Tab 中表组进度列表的动态渲染。
 *
 * 对应 HTML 片段：public/mapping/editTable.html
 *
 * 依赖（全部是全局，不是模块内定义）：
 *   - jQuery $
 *   - BootstrapDialog         插件（bootstrap-dialog）
 *   - iCheck                  插件
 *   - tableDnD                插件（jquery.tablednd）
 *   - doPoster                common.js
 *   - doLoader                common.js
 *   - doLoaderWithoutHashUpdate  common.js
 *   - updateHash              common.js
 *   - bootGrowl               common.js
 *   - bindTargetTableRenameClick  editRename.js（运行时调用，在 click 回调内）
 *   - checkFieldDifferencesForTableGroups  editFieldDiff.js（运行时调用）
 *
 * 导出（全局函数，供其他模块使用）：
 *   - getCheckedBoxSize($checkbox)    editPoller.js 在轮询回调中调用
 *   - updateTableGroups(mid, total, successCount, failCount)
 *                                      editPoller.js 在轮询回调中调用
 *
 * 入向调用点（edit.js 的 ready 块直接调用，必须前置加载）：
 *   - bindMappingTableGroupCheckBoxClick()
 *   - bindMappingTableGroupListClick()
 *   - bindMappingTableGroupDelClick()
 *   - bindMappingTableGroupResyncClick()
 *   - bindRefreshTablesClick()
 *
 * ★ 因此本文件必须在 edit.js **之前**加载。
 *
 * 本模块不声明任何全局变量，不创建任何定时器。
 */

// 绑定删除表关系复选框事件
function bindMappingTableGroupCheckBoxClick() {
    var $checkboxAll = $('.tableGroupCheckboxAll');
    var $checkbox = $('.tableGroupCheckbox');
    var $delBtn = $("#tableGroupDelBtn");
    var $resyncBtn = $("#tableGroupResyncBtn");
    var $batchConvertBtn = $("#batchConvertConfigBtn");
    var $checkCount = $("#tableGroupCheckCount");
    var totalCount = $checkbox.length;

    // 更新勾选数量显示
    function updateCheckCount() {
        var checkedCount = getCheckedBoxSize($checkbox).length;
        if (checkedCount > 0) {
            $checkCount.text('(' + checkedCount + '/' + totalCount + ')');
        } else {
            $checkCount.text('');
        }
    }

    $checkboxAll.iCheck({
        checkboxClass: 'icheckbox_square-red',
        labelHover: false,
        cursor: true
    }).on('ifChecked', function (event) {
        $checkbox.iCheck('check');
        updateCheckCount();
    }).on('ifUnchecked', function (event) {
        $checkbox.iCheck('uncheck');
        updateCheckCount();
    }).on('ifChanged', function (event) {
        var hasChecked = getCheckedBoxSize($checkbox).length > 0;
        $delBtn.prop('disabled', !hasChecked);
        $batchConvertBtn.prop('disabled', !hasChecked);
        updateResyncButtonState($resyncBtn, hasChecked);
        updateCheckCount();
    });

    // 初始化icheck插件
    $checkbox.iCheck({
        checkboxClass: 'icheckbox_square-red',
        cursor: true
    }).on('ifChanged', function (event) {
        var hasChecked = getCheckedBoxSize($checkbox).length > 0;
        $delBtn.prop('disabled', !hasChecked);
        $batchConvertBtn.prop('disabled', !hasChecked);
        updateResyncButtonState($resyncBtn, hasChecked);
        updateCheckCount();
    });
}

// 更新重新同步按钮状态
function updateResyncButtonState($resyncBtn, hasChecked) {
    var mappingState = $("#mappingState").text();
    var isStopped = mappingState.indexOf('未运行') !== -1 || mappingState.indexOf('异常') !== -1;
    $resyncBtn.prop('disabled', !hasChecked || !isStopped);
}

// 获取选择的CheckBox[value]
function getCheckedBoxSize($checkbox) {
    var checked = [];
    $checkbox.each(function () {
        if ($(this).prop('checked')) {
            checked.push($(this).val());
        }
    });
    return checked;
}

function bindMappingTableGroupListClick() {
    var $tableGroupList = $("#tableGroupList");
    if ($tableGroupList.length === 0) {
        setTimeout(function() {
            bindMappingTableGroupListClick();
        }, 100);
        return;
    }
    
    $tableGroupList.unbind("click");
    $tableGroupList.find("tr").bind('click', function (e) {
        if ($(e.target).hasClass('target-table-rename') || $(e.target).closest('.target-table-rename').length > 0) {
            return;
        }
        updateHash('/tableGroup/page/editTableGroup?id=' + $(this).attr("id"));
    });

    $tableGroupList.tableDnD({
        onDrop: function (table, row) {
            var newData = [];
            var $trList = $(table).find("tr");
            $.each($trList, function () {
                newData.push($(this).attr('id'));
            });
            $("#sortedTableGroupIds").val(newData.join('|'));
        }
    });
    
    bindTargetTableRenameClick();
    
    checkFieldDifferencesForTableGroups();
}

// 绑定删除表关系点击事件
function bindMappingTableGroupDelClick() {
    $("#tableGroupDelBtn").click(function () {
        var ids = getCheckedBoxSize($(".tableGroupCheckbox"));
        if (ids.length > 0) {
            var $mappingId = $(this).attr("mappingId");
            doPoster("/tableGroup/remove", { "mappingId": $mappingId, "ids": ids.join() }, function (data) {
                if (data.success == true) {
                    bootGrowl("删除映射关系成功!", "success");
                    // 直接重新加载页面内容，确保映射列表更新
                    var url = '/mapping/page/edit?id=' + $mappingId + "&classOn=1&refresh=" + new Date().getTime();
                    doLoaderWithoutHashUpdate(url);
                } else {
                    bootGrowl(data.resultValue, "danger");
                }
            });
        }
    });
}

// 绑定重新同步按钮点击事件
function bindMappingTableGroupResyncClick() {
    $("#tableGroupResyncBtn").click(function () {
        var ids = getCheckedBoxSize($(".tableGroupCheckbox"));
        if (ids.length > 0) {
            var $mappingId = $(this).attr("mappingId");
            showResyncConfirmDialog($mappingId, ids.join());
        }
    });
}

// 显示重新同步确认对话框
function showResyncConfirmDialog(mappingId, tableGroupIds) {
    var messageHtml = '<div style="padding: 10px;">' +
        '<p><strong>确认重新同步选中的表映射关系？</strong></p>' +
        '<p style="color: #999; font-size: 12px;">重新同步将重置选中表映射关系的同步状态，并自动启动任务。</p>' +
        '<div style="margin-top: 15px;">' +
        '<label style="cursor: pointer;">' +
        '<input type="checkbox" id="resyncTruncateTarget" style="margin-right: 5px;">' +
        '<span style="color: #d9534f; font-weight: bold;">同时清空目标源表数据（TRUNCATE）</span>' +
        '</label>' +
        '</div>' +
        '<p style="color: #999; font-size: 12px; margin-top: 5px;">注意：清空目标表数据将删除目标表中的所有记录，此操作不可恢复！</p>' +
        '</div>';

    BootstrapDialog.show({
        title: "重新同步确认",
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
            label: "确认重新同步",
            cssClass: "btn-warning",
            action: function (dialog) {
                var truncateTarget = $('#resyncTruncateTarget').is(':checked');
                dialog.close();
                executeResync(mappingId, tableGroupIds, truncateTarget);
            }
        }]
    });
}

// 执行重新同步
function executeResync(mappingId, tableGroupIds, truncateTarget) {
    bootGrowl("正在执行重新同步...", "info");
    
    doPoster("/tableGroup/resetTableGroups", {
        "mappingId": mappingId,
        "tableGroupIds": tableGroupIds,
        "truncateTarget": truncateTarget
    }, function (data) {
        if (data.success == true) {
            bootGrowl(data.resultValue, "success");
            // 刷新页面
            var url = '/mapping/page/edit?id=' + mappingId + "&classOn=1&refresh=" + new Date().getTime();
            doLoaderWithoutHashUpdate(url);
        } else {
            bootGrowl(data.resultValue, "danger");
        }
    });
}

// 绑定刷新表事件
function bindRefreshTablesClick() {
    let $refreshBtn = $("#refreshTableBtn");
    $refreshBtn.bind('click', function () {
        let id = $(this).attr("tableGroupId");
        doPoster("/mapping/refreshTables", { 'id': id }, function (data) {
            if (data.success == true) {
                bootGrowl("刷新表成功!", "success");
                // 使用doLoader重新加载页面内容，确保下拉框显示最新数据
                doLoader('/mapping/page/edit?id=' + id + "&classOn=1");
            } else {
                bootGrowl(data.resultValue, "danger");
            }
        });
    });
}

// 更新表映射关系数据（概况 Tab 中的表组进度列表）
function updateTableGroups(mappingId, total, successCount, failCount) {
    $.ajax({
        url: '/mapping/getTableGroups?id=' + mappingId,
        type: 'GET',
        success: function(dataJson) {
            var isSuccess = dataJson.success;
            var tableGroups = dataJson.resultValue;
            if (isSuccess && tableGroups) {
                var tbody = $("#tableGroupProgress tbody");
                tbody.empty();

                // 累加所有表组的同步速度，用于更新顶部面板
                var totalSpeed = 0;

                if (tableGroups.length > 0) {
                    $.each(tableGroups, function(index, tableGroup) {
                        var sourceTableName = tableGroup.sourceTable ? tableGroup.sourceTable.name : '';
                        var targetTableName = tableGroup.targetTable ? tableGroup.targetTable.name : '';

                        // 计算总同步数量
                        var totalSyncCount = tableGroup.success || 0;

                        // 获取状态
                        var status = tableGroup.status || '正常';
                        var statusClass = status === '异常' ? 'label-danger' : 'label-success';
                        
                        // 获取同步速度
                        var currentSpeed = tableGroup.currentSpeed || 0;
                        var lastSyncTime = tableGroup.lastSyncTime || 0;
                        var currentTime = new Date().getTime();
                        // 如果最后同步时间超过5秒，则显示0条/秒
                        var isActive = (currentTime - lastSyncTime <= 5000);
                        var speedDisplay = isActive ? currentSpeed.toFixed(0) : "0";

                        // 累加有效速度
                        if (isActive) {
                            totalSpeed += currentSpeed;
                        }

                        // 计算同步状态
                        var syncStatus = "未开始";
                        var syncSortValue = 0; // 排序值
                        if (lastSyncTime > 0) {
                            var timeDiff = currentTime - lastSyncTime;
                            if (timeDiff < 5000) {
                                // 5秒内有同步记录，显示进行中
                                syncStatus = "进行中";
                                syncSortValue = 1;
                            } else {
                                // 计算分钟数
                                var minutes = Math.floor(timeDiff / (1000 * 60));
                                syncStatus = "已同步" + minutes + "分钟前的数据";
                                syncSortValue = 2 + (minutes / 10000); // 加上分钟数的小数值，确保时间顺序
                            }
                        }

                        var tr = '<tr>' +
                            '<td>' + sourceTableName + '</td>' +
                            '<td>' + targetTableName + '</td>' +
                            '<td data-sort="' + totalSyncCount + '">' + totalSyncCount + '</td>' +
                            '<td data-sort="' + syncSortValue + '">' + syncStatus + '</td>' +
                            '<td data-sort="' + currentSpeed + '">' + speedDisplay + '</td>' +
                            '<td><span class="label ' + statusClass + '">' + status + '</span></td>' +
                            '</tr>';
                        tbody.append(tr);
                    });
                } else {
                    var tr = '<tr><td colspan="6" class="text-center">暂无表映射关系</td></tr>';
                    tbody.append(tr);
                }

                // 更新顶部面板的全体同步速度
                var speedText = totalSpeed > 0 ? (totalSpeed.toFixed(0) + " 条/秒") : "0 条/秒";
                $("#metaSpeed").find(".highlight-number").text(speedText);
            }
            
            // 添加表格排序功能
            addTableSorting();
        },
        error: function() {
            // alert('获取表映射关系失败');
        }
    });
}
