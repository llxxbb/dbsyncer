/**
 * 字段差异检查模块（从 edit.js 拆出，Stage 4）
 *
 * 职责：任务编辑页上「映射关系」Tab 中表组的字段差异检测——批量检查所有表组的
 *       字段差异状态并在列表中显示，绑定字段差异列的点击事件（打开差异配置弹窗），
 *       监听 fieldDiffConfigChanged 事件以在配置变更后重新检测。
 *
 * 依赖（全部是全局，不是模块内定义）：
 *   - jQuery $
 *   - doPoster                common.js
 *   - FieldDifferenceComponent  fieldDifferenceComponent.js
 *
 * 入向调用点（多个）：
 *   - edit.js 的 ready 块中 setTimeout 内调用 checkFieldDifferencesForTableGroups(true)
 *   - edit.js 的 bindMappingTableGroupListClick() 中调用 checkFieldDifferencesForTableGroups()
 *   - fieldDiffConfigChanged 事件处理器中调用 checkFieldDifferencesForTableGroups()
 *
 * ★ 本文件必须在 edit.js **之前**加载：
 *   - $(document).on('fieldDiffConfigChanged', ...) 是顶层代码，在脚本加载时执行
 *   - edit.js 的 ready 块中会调用 checkFieldDifferencesForTableGroups()
 *
 * 本模块不声明任何全局变量（除了通过 $(document).on 注册的事件监听器）。
 */

// 检查表映射组的字段差异并在列表中显示
// 参数 refreshBeforeCheck: 是否在检测前先刷新字段元数据，默认true
function checkFieldDifferencesForTableGroups(refreshBeforeCheck) {
    refreshBeforeCheck = refreshBeforeCheck !== false; // 默认为true

    var $tableGroupList = $("#tableGroupList");
    if ($tableGroupList.length === 0) {
        return;
    }

    // 收集所有映射组ID
    var ids = [];
    $tableGroupList.find("tr").each(function() {
        var tableGroupId = $(this).attr("id");
        if (tableGroupId) {
            ids.push(tableGroupId);
        }
    });

    if (ids.length === 0) {
        return;
    }

    // 显示检测中状态
    if (refreshBeforeCheck) {
        $tableGroupList.find('.field-diff-status').each(function() {
            $(this).html('<span class="label label-default"><i class="fa fa-spinner fa-spin"></i> 检测中...</span>');
        });
    }

    // 使用批量接口一次请求，根据参数决定是否先刷新字段
    doPoster("/tableGroup/fieldDifferenceBatch", {
        'ids': ids.join(','),
        'refreshBeforeCheck': refreshBeforeCheck
    }, function(data) {
        if (data.success === true && data.resultValue) {
            // resultValue 是 Map<tableGroupId, hasDifference>
            $.each(data.resultValue, function(tableGroupId, hasDiff) {
                var $row = $("#" + tableGroupId);
                var $diffStatusCell = $row.find('.field-diff-status');

                if (hasDiff) {
                    // 添加高亮样式
                    $row.addClass("field-diff-warning");
                    // 更新字段差异列显示
                    $diffStatusCell.html('<span class="label label-warning"><i class="fa fa-exclamation-triangle"></i> 有差异</span>');
                } else {
                    // 更新字段差异列显示为正常
                    $diffStatusCell.html('<span class="label label-success"><i class="fa fa-check"></i> 一致</span>');
                }
            });
            // 绑定字段差异列点击事件
            bindFieldDiffStatusClick();
        } else {
            // 接口调用失败，显示检测失败
            $tableGroupList.find('.field-diff-status').each(function() {
                $(this).html('<span class="label label-default">检测失败</span>');
            });
        }
    });
}

// 绑定字段差异状态列点击事件
function bindFieldDiffStatusClick() {
    $('.field-diff-status').off('click').on('click', function(e) {
        e.stopPropagation();
        var tableGroupId = $(this).data('id');
        if (tableGroupId) {
            FieldDifferenceComponent.show(tableGroupId);
        }
    });
}

// 监听字段差异配置变更事件，重新检测
$(document).on('fieldDiffConfigChanged', function() {
    if ($('#tableGroupList').length === 0) return;
    $('.modal-backdrop').remove();
    $('body').removeClass('modal-open').css({ 'padding-right': '', 'overflow': '' });
    FieldDifferenceComponent.setCachedData(null, null);
    checkFieldDifferencesForTableGroups();
});
