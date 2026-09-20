/**
 * 表选择/过滤模块（从 edit.js 拆出，Stage 3）
 *
 * 职责：任务编辑页上「映射关系」Tab 中源表选择器的初始化、过滤按钮的绑定——
 *       使用 SelectorManager 统一初始化双列表选择器，选择后自动添加表映射关系；
 *       过滤按钮控制是否显示已添加的表。
 *
 * 依赖（全部是全局，不是模块内定义）：
 *   - jQuery $
 *   - SelectorManager      dualListSelector.js
 *   - updateHash           common.js
 *   - refresh              edit.js（运行时调用，在 click 回调内）
 *   - doPoster             common.js
 *   - bootGrowl            common.js
 *   - doLoaderWithoutHashUpdate  common.js
 *
 * 入向调用点（两个）：edit.js 的 ready 块里直接调用 bindTableSelect() 和
 *   bindMultipleSelectFilterBtnClick()。
 * ★ 因此本文件必须在 edit.js **之前**加载：SPA 外壳中 jQuery 的 ready 回调
 *   是同步执行的，晚加载会 ReferenceError。
 *
 * 本模块不声明任何全局变量，不创建任何定时器。
 */

// 绑定下拉选择事件自动匹配相似表事件
function bindTableSelect() {
    // 使用 SelectorManager 统一初始化
    initTableSelectors();
}

// 绑定下拉过滤按钮点击事件
function bindMultipleSelectFilterBtnClick() {
    $(".actions-btn").parent().append('<button type="button" class="actions-btn bs-show-all btn btn-default" title="显示所有表，包含已添加的表">取消过滤</button><button type="button" class="actions-btn bs-exclude-all btn btn-default" title="不显示已添加的表">过滤</button>');
    $(".actions-btn").css('width', '25%');
    $("#bs-show-all").on("click", function () {
        updateHash('/mapping/page/edit?id=' + $("#mappingId").val() + '&exclude=1');
        bootGrowl("取消过滤成功!", "success");
    });
    $(".bs-exclude-all").bind("click", function () {
        refresh($("#mappingId").val(),1);
        bootGrowl("过滤成功!", "success");
    });
}

// 初始化表选择器 - 使用 SelectorManager 消除重复代码
function initTableSelectors() {
    SelectorManager.init({
        type: 'table',
        side: 'source',
        btnId: 'sourceTableBtn',
        selectId: 'sourceTable',
        modalId: 'sourceTableModal',
        title: '选择数据表',
        leftTitle: '可选表',
        rightTitle: '已选表',
        onConfirm: function(selectedIds) {
            if (selectedIds.length === 0) {
                return;
            }
            var mappingId = $("#mappingId").val();
            var sourceTable = selectedIds.join('|');
            doPoster("/tableGroup/add", {
                mappingId: mappingId,
                sourceTable: sourceTable,
                targetTable: sourceTable
            }, function (data) {
                if (data.success == true) {
                    bootGrowl("新增映射关系成功!", "success");
                    var url = '/mapping/page/edit?id=' + mappingId + "&classOn=1&refresh=" + new Date().getTime();
                    doLoaderWithoutHashUpdate(url);
                } else {
                    bootGrowl(data.resultValue, "danger");
                    if (data.status == 400) {
                        var url = '/mapping/page/edit?id=' + mappingId + "&classOn=1&refresh=" + new Date().getTime();
                        doLoaderWithoutHashUpdate(url);
                    }
                }
            });
        }
    });
}
