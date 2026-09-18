/**
 * 任务编辑页 —— 错误队列模块（从 edit.js 拆出，Stage 4/5）
 *
 * 职责：错误队列 tab 的数据查询与分页、错误详情/数据详情弹窗、单条与全部重做、
 *       删除、清空、以及计数对齐（bindAlignCountEvent）。
 *
 * 加载顺序约束（重要）：
 *   必须排在 edit.js 之前。edit.js 的 classOn ready 块在脚本求值期间就会同步调用
 *   initErrorQueue()（classOnVal == 3 时），SPA 外壳下 jQuery ready 是同步执行的，
 *   所以本模块必须先完成求值。
 *
 * 对外依赖（仅 common.js + 插件，完全自洽）：
 *   - common.js：doGetter / doPoster / bootGrowl / updateHash
 *   - 插件：jQuery、BootstrapDialog
 *
 *   本模块不声明任何全局变量，不读写 edit.js 的 currentMappingId，也不调用
 *   editLog.js 的 doQueryLog。与 edit.js 的关系是严格单向的：只有 edit.js 调用
 *   本模块的 initErrorQueue()。因此前置加载没有任何反向依赖风险。
 *
 * 说明：页面级的 shown.bs.tab 标签路由（同时服务 #mappingErrorQueue 与 #mappingLog）
 *       仍留在 edit.js，未随本模块搬出。本模块不创建任何定时器。
 */

//*********************************** 错误队列 开始位置***********************************//
// 格式化日期
function formatErrorQueueDate(time) {
    var date = new Date(time);
    var YY = date.getFullYear() + '-';
    var MM = (date.getMonth() + 1 < 10 ? '0' + (date.getMonth() + 1) : date.getMonth() + 1) + '-';
    var DD = (date.getDate() < 10 ? '0' + (date.getDate()) : date.getDate());
    var hh = (date.getHours() < 10 ? '0' + date.getHours() : date.getHours()) + ':';
    var mm = (date.getMinutes() < 10 ? '0' + date.getMinutes() : date.getMinutes()) + ':';
    var ss = (date.getSeconds() < 10 ? '0' + date.getSeconds() : date.getSeconds());
    return YY + MM + DD + " " + hh + mm + ss;
}

// 查看错误队列详细数据
function bindErrorQueueQueryDataDetailEvent() {
    let $queryData = $("#mappingErrorQueue .queryData");
    $queryData.unbind("click");
    $queryData.click(function () {
        let json = $(this).parent().find("div").text();
        BootstrapDialog.show({
            size: BootstrapDialog.SIZE_WIDE,
            title: "注意信息安全",
            type: BootstrapDialog.TYPE_INFO,
            message: function (dialog) {
                let $content = '<table class="table table-hover">';
                $content += '<thead><tr><th></th><th>字段</th><th>值</th></tr></thead>';
                $content += '<tbody id="dataDetail" tableGroupId="">';

                let jsonObj = $.parseJSON(json);
                let index = 1;
                $.each(jsonObj, function(name, value) {
                    $content += '<tr>';
                    $content += '<td>' + index + '</td>';
                    $content += '<td>' + name + '</td>';
                    $content += '<td class="driver_break_word">' + value + '</td>';
                    $content += '</tr>';
                    index++;
                });

                $content += '</tbody>';
                $content += '</table>';
                return $content;
            },
            buttons: [{
                label: "关闭",
                action: function (dialog) {
                    dialog.close();
                }
            }]
        });
    });
}

// 错误队列重试同步
function bindErrorQueueQueryDataRetryEvent() {
    let $retry = $("#mappingErrorQueue .retryData");
    $retry.unbind("click");
    $retry.click(function () {
        let metaId = $("#metaId").val();
        let messageId = $(this).attr("id");
        updateHash('/monitor/page/retry?metaId=' + metaId + '&messageId=' + messageId);
    });
}

// 查看错误队列异常详情
function bindErrorQueueQueryErrorDetailEvent() {
    var $queryData = $("#mappingErrorQueue .queryError");
    $queryData.unbind("click");
    $queryData.click(function () {
        var json = $(this).text();
        var html = '<div class="row driver_break_word">' + json + '</div>';
        BootstrapDialog.show({
            title: "异常详细",
            size: BootstrapDialog.SIZE_WIDE,
            message: html,
            type: BootstrapDialog.TYPE_WARNING,
            buttons: [{
                label: "关闭",
                action: function (dialog) {
                    dialog.close();
                }
            }]
        });
    });
}

// 错误队列删除单条数据
function bindErrorQueueQueryDataDeleteEvent() {
    let $delete = $("#mappingErrorQueue .deleteData");
    $delete.unbind("click");
    $delete.click(function () {
        let messageId = $(this).attr("id");
        let mappingId = $("#mappingId").val();
        BootstrapDialog.show({
            title: "提示",
            message: "确认删除该条数据？",
            type: BootstrapDialog.TYPE_WARNING,
            buttons: [{
                label: "取消",
                action: function (dialog) {
                    dialog.close();
                }
            }, {
                label: "确定",
                action: function (dialog) {
                    doPoster('/monitor/deleteData', {
                        "id": mappingId,
                        "messageId": messageId
                    }, function (data) {
                        if (data.success == true) {
                            bootGrowl("删除成功", "success");
                            $("#errorQueueQueryBtn").click();
                        } else {
                            bootGrowl(data.resultValue, "danger");
                        }
                    });
                    dialog.close();
                }
            }]
        });
    });
}

// 错误队列显示更多
function errorQueueShowMore($this, $url, $params, $call) {
    $params.pageNum = parseInt($this.attr("num")) + 1;
    $params.pageSize = 10;
    doGetter($url, $params, function (data) {
        if (data.success == true) {
            if (data.resultValue.data.length > 0) {
                $this.attr("num", $params.pageNum);
            }
            $call(data.resultValue);
        } else {
            bootGrowl(data.resultValue, "danger");
        }
    });
}

// 错误队列显示数据
function errorQueueShowData($dataList, arr, append) {
    var html = '';
    var size = arr.length;
    if (size > 0) {
        var start = append ? $dataList.find("tr").size() : 0;
        for (i = 0; i < size; i++) {
            html += '<tr>';
            html += '<td>' + (start + i + 1) + '</td>';
            html += '<td>' + arr[i].targetTableName + '</td>';
            html += '<td>' + arr[i].event + '</td>';
            html += '<td>' + (arr[i].success ? '<span class="label label-success">成功</span>' : '<span class="label label-warning">失败</span>') + '</td>';
            html += '<td style="max-width:100px;" class="dbsyncer_over_hidden"><a href="javascript:;" class="dbsyncer_pointer queryError">' + arr[i].error + '</a></td>';
            html += '<td>' + formatErrorQueueDate(arr[i].createTime) + '</td>';
            html += '<td><div class="hidden">' + arr[i].json + '</div><a href="javascript:;" class="label label-info queryData">查看数据</a>&nbsp;';
            html += (arr[i].success ? '' : '<a id="' + arr[i].id + '" href="javascript:;" class="label label-warning retryData">重试</a>');
            html += '&nbsp;<a id="' + arr[i].id + '" href="javascript:;" class="label label-danger deleteData">删除</a>';
            html += '</td>';
            html += '</tr>';
        }
    }
    return html;
}

// 错误队列刷新数据列表
function errorQueueRefreshDataList(resultValue, append) {
    var $dataList = $("#errorQueueDataList");
    var $dataTotal = $("#errorQueueDataTotal");
    var html = errorQueueShowData($dataList, resultValue.data, append);
    if (append) {
        $dataList.append(html);
    } else {
        $dataList.html(html);
        $("#errorQueueQueryMore").attr("num", 1);
    }
    $dataTotal.html(resultValue.total);
    bindErrorQueueQueryDataDetailEvent();
    bindErrorQueueQueryDataRetryEvent();
    bindErrorQueueQueryErrorDetailEvent();
    bindErrorQueueQueryDataDeleteEvent();
}

// 错误队列查询数据
function bindErrorQueueQueryDataEvent() {
    $("#errorQueueQueryBtn").click(function () {
        var keyword = $("#errorQueueKeyword").val();
        var id = $("#mappingId").val();
        var dataStatus = $("#errorQueueDataStatus").val();
        doGetter('/monitor/queryData', {
            "error": keyword,
            "dataStatus": dataStatus,
            "id": id,
            "pageNum": 1,
            "pageSize": 10
        }, function (data) {
            if (data.success == true) {
                errorQueueRefreshDataList(data.resultValue);
            } else {
                bootGrowl(data.resultValue, "danger");
            }
        });
    });
}

// 错误队列显示更多
function bindErrorQueueQueryDataMoreEvent() {
    $("#errorQueueQueryMore").click(function () {
        var keyword = $("#errorQueueKeyword").val();
        var id = $("#mappingId").val();
        var dataStatus = $("#errorQueueDataStatus").val();
        errorQueueShowMore($(this), '/monitor/queryData', {
            "error": keyword,
            "dataStatus": dataStatus,
            "id": id
        }, function (resultValue) {
            errorQueueRefreshDataList(resultValue, true)
        });
    });
}

// 错误队列清空数据
function bindErrorQueueClearDataEvent() {
    var $clearDataBtn = $("#mappingErrorQueue .clearDataBtn");
    $clearDataBtn.click(function () {
        var $id = $("#mappingId").val();
        var data = {"id": $id};
        BootstrapDialog.show({
            title: "警告",
            type: BootstrapDialog.TYPE_DANGER,
            message: "确认清空该任务的错误数据?",
            size: BootstrapDialog.SIZE_NORMAL,
            buttons: [{
                label: "确定",
                action: function (dialog) {
                    doPoster('/monitor/clearData', data, function (data) {
                        if (data.success == true) {
                            bootGrowl("清空数据成功!", "success");
                            // 重新查询数据
                            $("#errorQueueQueryBtn").click();
                        } else {
                            bootGrowl(data.resultValue, "danger");
                        }
                    });
                    dialog.close();
                }
            }, {
                label: "取消",
                action: function (dialog) {
                    dialog.close();
                }
            }]
        });
    });
}

// 错误队列全部重做
function bindErrorQueueRetryAllEvent() {
    var $retryAllBtn = $("#errorQueueRetryAllBtn");
    $retryAllBtn.click(function () {
        var metaId = $("#metaId").val();
        if (!metaId) {
            bootGrowl("metaId为空，无法执行全部重做", "danger");
            return;
        }

        // 显示确认对话框，总数以后端处理为准
        BootstrapDialog.show({
            title: "确认全部重做",
            type: BootstrapDialog.TYPE_WARNING,
            message: "确认要重试所有失败数据吗？",
            size: BootstrapDialog.SIZE_NORMAL,
            buttons: [{
                label: "确定",
                cssClass: "btn-primary",
                action: function (dialog) {
                    var $btn = $(this);
                    $btn.prop('disabled', true);
                    $btn.html('<i class="fa fa-spinner fa-spin"></i> 处理中...');

                    // 调用后端接口
                    doPoster('/monitor/retryAll', {"metaId": metaId}, function (result) {
                        $btn.prop('disabled', false);
                        $btn.html("确定");
                        dialog.close();

                        if (result.success == true) {
                            var retryResult = result.resultValue;
                            var message = "全部重做完成！总数: " + retryResult.total + 
                                          ", 成功: " + retryResult.success + 
                                          ", 失败: " + retryResult.fail;
                            bootGrowl(message, "success");
                            // 重新查询数据
                            $("#errorQueueQueryBtn").click();
                        } else {
                            bootGrowl(result.resultValue || "全部重做失败", "danger");
                        }
                    });
                }
            }, {
                label: "取消",
                action: function (dialog) {
                    dialog.close();
                }
            }]
        });
    });
}

// 错误队列计数对齐
function bindAlignCountEvent() {
    $("#alignCountBtn").click(function () {
        var metaId = $("#metaId").val();
        if (!metaId) {
            bootGrowl("metaId为空，无法执行计数对齐", "danger");
            return;
        }
        BootstrapDialog.show({
            title: "确认计数对齐",
            type: BootstrapDialog.TYPE_INFO,
            message: "以错误队列实际失败数为准，修正任务面板显示的失败计数？",
            size: BootstrapDialog.SIZE_NORMAL,
            buttons: [{
                label: "确定",
                cssClass: "btn-primary",
                action: function (dialog) {
                    doPoster('/monitor/alignCount', {"metaId": metaId}, function (result) {
                        if (result.success == true) {
                            bootGrowl("计数对齐完成", "success");
                            location.reload();
                        } else {
                            bootGrowl(result.resultValue || "计数对齐失败", "danger");
                        }
                    });
                    dialog.close();
                }
            }, {
                label: "取消",
                action: function (dialog) {
                    dialog.close();
                }
            }]
        });
    });
}

// 初始化错误队列
function initErrorQueue() {
    // 绑定查询数据事件
    bindErrorQueueQueryDataEvent();
    // 绑定显示更多事件
    bindErrorQueueQueryDataMoreEvent();
    // 绑定清空数据事件
    bindErrorQueueClearDataEvent();
    // 绑定全部重做事件
    bindErrorQueueRetryAllEvent();
    // 绑定计数对齐事件
    bindAlignCountEvent();
    // 绑定数据状态切换事件
    $("#errorQueueDataStatus").change(function () {
        $("#errorQueueQueryBtn").click();
    });
    // 默认查询数据
    $("#errorQueueQueryBtn").click();
}

//# sourceURL=/js/mapping/editErrorQueue.js
