function submit(formData) {
    //保存驱动配置
    doPoster("/tableGroup/edit", formData, function (data) {
        if (data.success == true) {
            // 检查是否需要确认主键变更
            if (data.resultValue && data.resultValue.needConfirm) {
                // 有差异，显示确认弹窗
                // 传递原始表单数据和差异信息
                showPrimaryKeyDifferenceModal(data.resultValue.difference, formData);
            } else {
                // 无差异，正常保存成功
                bootGrowl("保存表映射关系成功!", "success");
                backMappingPage($("#tableGroupSubmitBtn"));
            }
        } else {
            bootGrowl(data.resultValue, "danger");
        }
    });
}

// 初始化映射关系参数
function initFieldMappingParams(){
    // 生成JSON参数
    let row = [];
    let $fieldMappingList = $("#fieldMappingList");
    $fieldMappingList.find("tr").each(function(k,v){
        let $pk = $(this).find("td:eq(2)").html();
        row.push({
            "source":$(this).find("td:eq(0)").text(),
            "target":$(this).find("td:eq(1)").text(),
            "pk":($pk != "" || $.trim($pk).length > 0)
        });
    });
    let $fieldMappingTable = $("#fieldMappingTable");
    if (0 >= row.length) {
        $fieldMappingTable.addClass("hidden");
    } else {
        $fieldMappingTable.removeClass("hidden");
    }
    $("#fieldMapping").val(JSON.stringify(row));
}

// 绑定表格拖拽事件
function bindFieldMappingDrop() {
    $("#fieldMappingList").tableDnD({
        onDrop: function(table, row) {
            initFieldMappingParams();
        }
    });
}

// 获取选择的CheckBox[value]
function getCheckedBoxElements($checkbox){
    let checked = [];
    $checkbox.each(function(){
        if($(this).prop('checked')){
            checked.push($(this).parent().parent().parent());
        }
    });
    return checked;
}
// 绑定刷新表字段事件
function bindRefreshTableFieldsClick() {
    let $refreshBtn = $("#refreshTableFieldBtn");
    $refreshBtn.bind('click', function(){
        let id = $(this).attr("tableGroupId");
        doPoster("/tableGroup/refreshFields", {'id': id}, function (data) {
            if (data.success == true) {
                bootGrowl("刷新字段成功!", "success");
                doLoader('/tableGroup/page/editTableGroup?id=' + id);
            } else {
                bootGrowl(data.resultValue, "danger");
            }
        });
    });
}
// 绑定删除表字段复选框事件
function bindFieldMappingCheckBoxClick(){
    let $checkboxAll = $('.fieldMappingDeleteCheckboxAll');
    let $checkbox = $('.fieldMappingDeleteCheckbox');
    let $delBtn = $("#fieldMappingDelBtn");
    $checkboxAll.iCheck({
        checkboxClass: 'icheckbox_square-red',
        labelHover: false,
        cursor: true
    }).on('ifChecked', function (event) {
        $checkbox.iCheck('check');
    }).on('ifUnchecked', function (event) {
        $checkbox.iCheck('uncheck');
    }).on('ifChanged', function (event) {
        $delBtn.prop('disabled', getCheckedBoxElements($checkbox).length < 1);
    });

    // 初始化icheck插件
    $checkbox.iCheck({
        checkboxClass: 'icheckbox_square-red',
        cursor: true
    }).on('ifChanged', function (event) {
        // 阻止tr触发click事件
        event.stopPropagation();
        event.cancelBubble=true;
        $delBtn.prop('disabled', getCheckedBoxElements($checkbox).length < 1);
    });
}
// 绑定字段映射表格点击事件
function bindFieldMappingListClick(){
    // 行双击事件
    let $tr = $("#fieldMappingList tr");
    $tr.unbind("dblclick");
    $tr.bind('dblclick', function () {
        let $pk = $(this).find("td:eq(2)");
        let $text = $pk.html();
        let isPk = $text == "" || $.trim($text).length == 0;
        $pk.html(isPk ? '<i title="主键" class="fa fa-key fa-fw fa-rotate-90 text-warning"></i>' : '');
        
        // 同步更新 targetTablePK
        updateTargetTablePKFromFieldMapping();
        
        initFieldMappingParams();
    });
}
// 绑定下拉选择事件自动匹配相似字段事件
function bindTableFieldSelect(){
    // 使用 SelectorManager 统一初始化
    initFieldSelectors();
}

// 初始化字段选择器 - 使用 SelectorManager 消除重复代码
function initFieldSelectors() {
    // 数据源字段选择器
    SelectorManager.init({
        type: 'field',
        side: 'source',
        btnId: 'sourceFieldBtn',
        selectId: 'sourceTableField',
        modalId: 'sourceFieldModal',
        title: '选择数据源字段',
        leftTitle: '可选字段',
        rightTitle: '已选字段',
        onConfirm: function(selectedIds, data) {
            // 自动同步目标字段选择
            const targetSelected = $('#targetTableField').val();
            if (selectedIds.length === 0) {
                // 源字段清空时，同步清空目标字段
                SelectorManager.get('targetFieldSelector').setSelected([]);
                SelectorManager.updateButtonDisplay('target', 'field', [], data);
                $('#targetTableField').val([]).trigger('change');
            } else if (!targetSelected || targetSelected.length === 0) {
                // 目标字段未选择时，自动同步源字段选择
                SelectorManager.get('targetFieldSelector').setSelected(selectedIds);
                SelectorManager.updateButtonDisplay('target', 'field', selectedIds, data);
                $('#targetTableField').val(selectedIds).trigger('change');
            }
        }
    });

    // 目标源字段选择器
    SelectorManager.init({
        type: 'field',
        side: 'target',
        btnId: 'targetFieldBtn',
        selectId: 'targetTableField',
        modalId: 'targetFieldModal',
        title: '选择目标源字段',
        leftTitle: '可选字段',
        rightTitle: '已选字段'
    });

    // 绑定添加按钮事件
    bindFieldMappingAddClick();
}

// 修复 bootstrap-select 选中值不显示的问题
function fixBootstrapSelectDisplay() {
    // 延迟执行，确保 DOM 完全渲染
    setTimeout(function() {
        // 刷新所有 select-control-table 类型的下拉框
        $('.select-control-table').each(function() {
            var $select = $(this);
            // 获取当前选中的值
            var selectedValues = $select.selectpicker('val');
            // 刷新 selectpicker
            $select.selectpicker('refresh');
            // 如果有选中的值，确保显示正确
            if (selectedValues && selectedValues.length > 0) {
                $select.selectpicker('val', selectedValues);
            }
        });
    }, 100);
}

// 绑定下拉过滤按钮点击事件
function bindFieldSelectFilterBtnClick() {
    // 查找操作按钮容器
    var $sourceActionsBox = findActionsBox('#sourceTableField');
    var $targetActionsBox = findActionsBox('#targetTableField');
    
    // 添加过滤按钮
    if ($sourceActionsBox && $sourceActionsBox.length > 0) {
        addFilterButtons($sourceActionsBox, 'source');
    }
    
    if ($targetActionsBox && $targetActionsBox.length > 0) {
        addFilterButtons($targetActionsBox, 'target');
    }
    
    // 绑定事件（使用事件委托）
    $(document).off('click', '.bs-show-all-source, .bs-exclude-all-source, .bs-show-all-target, .bs-exclude-all-target');
    
    $(document).on('click', '.bs-show-all-source', function (e) {
        e.preventDefault();
        window.refreshFieldsWithFilter('source', false);
        bootGrowl("取消过滤成功!", "success");
    });
    
    $(document).on('click', '.bs-exclude-all-source', function (e) {
        e.preventDefault();
        window.refreshFieldsWithFilter('source', true);
        bootGrowl("过滤成功!", "success");
    });
    
    $(document).on('click', '.bs-show-all-target', function (e) {
        e.preventDefault();
        window.refreshFieldsWithFilter('target', false);
        bootGrowl("取消过滤成功!", "success");
    });
    
    $(document).on('click', '.bs-exclude-all-target', function (e) {
        e.preventDefault();
        window.refreshFieldsWithFilter('target', true);
        bootGrowl("过滤成功!", "success");
    });
}

// 查找操作按钮容器的辅助函数
function findActionsBox(selectId) {
    var $element = $(selectId);
    
    // 方法1：查找同级的bootstrap-select容器内的bs-actionsbox
    var $actionsBox = $element.siblings('.bootstrap-select').find('.bs-actionsbox .btn-group');
    if ($actionsBox.length > 0) {
        return $actionsBox;
    }
    
    // 方法2：查找父级容器内的bs-actionsbox
    $actionsBox = $element.parent().find('.bs-actionsbox .btn-group');
    if ($actionsBox.length > 0) {
        return $actionsBox;
    }
    
    // 方法3：查找整个文档中对应的bs-actionsbox（通过data-id匹配）
    var selectElement = $element[0];
    if (selectElement) {
        var selectId = selectElement.id;
        $actionsBox = $('[aria-owns*="' + selectId + '"]').siblings('.dropdown-menu').find('.bs-actionsbox .btn-group');
        if ($actionsBox.length > 0) {
            return $actionsBox;
        }
    }
    
    // 方法4：直接查找页面中的bs-actionsbox（作为最后手段）
    $actionsBox = $('.bs-actionsbox .btn-group');
    if ($actionsBox.length > 0) {
        return $actionsBox.first(); // 只返回第一个
    }
    
    return null;
}

// 添加过滤按钮的辅助函数
function addFilterButtons($actionsBox, fieldType) {
    // 检查是否已经添加过按钮
    var existingButtons = $actionsBox.find('.bs-show-all-' + fieldType + ', .bs-exclude-all-' + fieldType);
    if (existingButtons.length > 0) {
        return;
    }
    
    // 添加过滤按钮（使用双重事件机制：事件委托 + inline onclick 确保可靠性）
    $actionsBox.append(
        '<button type="button" class="actions-btn bs-show-all-' + fieldType + ' btn btn-default" title="显示所有字段，包含已添加的字段" onclick="window.refreshFieldsWithFilter(\'' + fieldType + '\', false);">取消过滤</button>' +
        '<button type="button" class="actions-btn bs-exclude-all-' + fieldType + ' btn btn-default" title="不显示已添加的字段" onclick="window.refreshFieldsWithFilter(\'' + fieldType + '\', true);">过滤</button>'
    );
    
    // 调整按钮宽度为20%
    $actionsBox.find('.actions-btn').css('width', '20%');
}

// 刷新字段并应用过滤（设置为全局函数，供内联事件调用）
window.refreshFieldsWithFilter = function(fieldType, excludeAdded) {
    // 获取已添加的字段映射
    let addedFields = [];
    let $fieldMappingList = $("#fieldMappingList");
    
    $fieldMappingList.find("tr").each(function(){
        if (fieldType === 'source') {
            let sourceField = $(this).find("td:eq(0)").text().trim();
            if (sourceField) {
                addedFields.push(sourceField);
            }
        } else {
            let targetField = $(this).find("td:eq(1)").text().trim();
            if (targetField) {
                addedFields.push(targetField);
            }
        }
    });
    
    // 获取对应的select元素
    let $select = fieldType === 'source' ? $("#sourceTableField") : $("#targetTableField");
    
    // 使用Bootstrap Select的方式过滤选项
    $select.find('option').each(function() {
        let $option = $(this);
        let optionValue = $option.val();
        let isAdded = addedFields.includes(optionValue);
        
        if (excludeAdded && isAdded) {
            // 禁用并隐藏选项
            $option.prop('disabled', true);
            $option.attr('data-hidden', 'true');
        } else {
            // 启用并显示选项
            $option.prop('disabled', false);
            $option.removeAttr('data-hidden');
        }
    });
    
    // 重新初始化selectpicker以应用变更
    $select.selectpicker('destroy').selectpicker({
        "title": "请选择",
        "actionsBox": true,
        "liveSearch": true,
        "selectAllText": "全选",
        "deselectAllText": "取消全选",
        "noneResultsText": "没有找到 {0}",
        "selectedTextFormat": "count > 10"
    });
    
    // 重新绑定过滤按钮（因为重新初始化后按钮会消失）
    setTimeout(function() {
        if (fieldType === 'source') {
            var $actionsBox = findActionsBox('#sourceTableField');
            // 只在按钮不存在时才添加，避免重复添加
            if ($actionsBox && $actionsBox.find('.bs-show-all-source').length === 0) {
                addFilterButtons($actionsBox, 'source');
            }
        } else {
            var $actionsBox = findActionsBox('#targetTableField');
            // 只在按钮不存在时才添加，避免重复添加
            if ($actionsBox && $actionsBox.find('.bs-show-all-target').length === 0) {
                addFilterButtons($actionsBox, 'target');
            }
        }
    }, 100);
}
// 检查字段是否为主键
function isSourceFieldPrimaryKey(fieldName) {
    let $sourceSelect = $("#sourceTableField");
    let $option = $sourceSelect.find('option[value="' + fieldName + '"]');
    return $option.length > 0 && $option.attr('data-pk') === 'true';
}

// 绑定添加字段映射点击事件
function bindFieldMappingAddClick(){
    let $btn = $("#fieldMappingAddBtn");
    $btn.bind('click', function(){
        // 从隐藏的select元素获取选中值
        let sFields = $("#sourceTableField").val();
        let tFields = $("#targetTableField").val();

        sFields = sFields == null ? [] : (Array.isArray(sFields) ? sFields : [sFields]);
        tFields = tFields == null ? [] : (Array.isArray(tFields) ? tFields : [tFields]);
        
        // 非空检查
        if(sFields.length == 0 && tFields.length == 0){
            bootGrowl("至少选择一个表字段.", "danger");
            return;
        }

        // 如果未选择目标字段，则使用源字段作为目标字段
        if(sFields.length > 0 && tFields.length == 0){
            tFields = [...sFields];
        }

        // 如果未选择源字段，则使用目标字段作为源字段
        if(sFields.length == 0 && tFields.length > 0){
            sFields = [...tFields];
        }
        
        // 检查数量是否一致
        if(sFields.length != tFields.length){
            bootGrowl("源字段和目标字段数量不一致，请检查选择.", "danger");
            return;
        }

        let $fieldMappingList = $("#fieldMappingList");
        let $tr = $fieldMappingList.find("tr");
        let addedCount = 0;
        
        // 批量添加字段映射
        for(let i = 0; i < sFields.length; i++){
            let sField = sFields[i];
            let tField = tFields[i];
            
            // 检查重复字段
            let repeated = false;
            $tr.each(function(k,v){
                let sf = $(this).find("td:eq(0)").text();
                let tf = $(this).find("td:eq(1)").text();
                if (repeated = (sField == sf && tField == tf)) {
                    return false;
                }
            });
            
            if(!repeated){
                let index = $tr.size() + addedCount + 1;
                
                // 检查源字段是否为主键，如果是则自动标记为主键
                let isPrimaryKey = isSourceFieldPrimaryKey(sField);
                let pkIcon = isPrimaryKey ? '<i title="主键" class="fa fa-key fa-fw fa-rotate-90 text-warning"></i>' : '';
                
                let trHtml = "<tr id='fieldMapping_"+ index +"' title='双击设置/取消主键'><td>" + sField + "</td><td>" + tField + "</td><td>" + pkIcon + "</td><td><input type='checkbox' class='fieldMappingDeleteCheckbox' /></td></tr>";
                $fieldMappingList.append(trHtml);
                addedCount++;
            }
        }
        
        if(addedCount > 0){
            // 统计自动标记为主键的字段数量
            let autoPkCount = 0;
            for(let i = 0; i < sFields.length; i++){
                if(isSourceFieldPrimaryKey(sFields[i])){
                    autoPkCount++;
                }
            }
            
            let message = "成功添加 " + addedCount + " 个字段映射关系!";
            if(autoPkCount > 0){
                message += " 其中 " + autoPkCount + " 个源主键字段已自动标记为主键。";
            }
            bootGrowl(message, "success");
            
            initFieldMappingParams();
            bindFieldMappingDrop();
            bindFieldMappingListClick();
            bindFieldMappingCheckBoxClick();
            bindFieldMappingDelClick();
            
            // 清空选择
            $sourceSelect.selectpicker('deselectAll');
            $targetSelect.selectpicker('deselectAll');
        } else {
            bootGrowl("没有新的映射关系需要添加.", "info");
        }
    });
}
// 绑定删除字段映射点击事件
function bindFieldMappingDelClick(){
    let $fieldMappingDelBtn = $("#fieldMappingDelBtn");
    $fieldMappingDelBtn.unbind("click");
    $fieldMappingDelBtn.click(function () {
        let elements = getCheckedBoxElements($('.fieldMappingDeleteCheckbox'));
        if (elements.length > 0) {
            let len = elements.length;
            for(let i = 0; i < len; i++){
                elements[i].remove();
            }
            $fieldMappingDelBtn.prop('disabled', true);
            initFieldMappingParams();
        }
    });
}
// 返回驱动配置页面
function backMappingPage($this){
    // 返回接口时增加classOn=1，edit页面接到后默认展开映射关系tab
    doLoader('/mapping/page/edit?classOn=1&id=' + $this.attr("mappingId"));
}

function doTableGroupSubmit() {
   //保存
   let $form = $("#tableGroupModifyForm");
   if ($form.formValidate() == true) {
       let formData = $form.serializeJson();
       submit(formData);
   }
}

// 绑定主键顺序按钮
function bindPrimaryKeyOrderBtn() {
    $('#primaryKeyOrderBtn').on('click', function() {
        // 获取当前主键列表
        let currentPKs = $('#targetTablePK').val() || '';
        
        // 设置 tooltip
        $(this).attr('title', '当前主键顺序：' + (currentPKs || '无'));
        $('[data-toggle="tooltip"]').tooltip('fixTitle').tooltip('show');
        setTimeout(function() { $('[data-toggle="tooltip"]').tooltip('hide'); }, 2000);
        
        // 打开对话框
        showPrimaryKeyOrderModal(currentPKs);
    });
    
    // 绑定保存按钮
    $('#savePrimaryKeyOrderBtn').on('click', function() {
        // 收集新的主键顺序
        let newPKs = collectPrimaryKeyOrder();
        $('#targetTablePK').val(newPKs);
        
        // 更新字段映射表格中的主键标记
        updatePrimaryKeyMarkers(newPKs);
        
        // 关闭对话框
        $('#primaryKeyOrderModal').modal('hide');
        
        bootGrowl("主键顺序已更新!", "success");
    });
}

// 显示主键顺序对话框
function showPrimaryKeyOrderModal(currentPKs) {
    // 解析当前主键
    let pkArray = currentPKs ? currentPKs.split(',').map(s => s.trim()) : [];
    
    // 生成可拖拽的主键列表
    let html = '<div class="list-group" id="primaryKeyDragList">';
    pkArray.forEach((pk, index) => {
        html += '<div class="list-group-item" style="cursor: move;" draggable="true" data-pk="' + pk + '">' +
                '<i class="fa fa-arrows"></i> ' + (index + 1) + '. ' + pk +
                '</div>';
    });
    html += '</div>';
    
    $('#primaryKeyList').html(html);
    
    // 绑定拖拽事件
    bindPrimaryKeyDragDrop();
    
    // 显示对话框
    $('#primaryKeyOrderModal').modal('show');
}

// 绑定拖拽事件
function bindPrimaryKeyDragDrop() {
    let dragSrcEl = null;
    
    $('#primaryKeyDragList .list-group-item').on('dragstart', function(e) {
        dragSrcEl = this;
        e.originalEvent.dataTransfer.effectAllowed = 'move';
        e.originalEvent.dataTransfer.setData('text/html', this.innerHTML);
        $(this).addClass('dragging');
    });
    
    $('#primaryKeyDragList .list-group-item').on('dragover', function(e) {
        if (e.preventDefault) e.preventDefault();
        e.originalEvent.dataTransfer.dropEffect = 'move';
        return false;
    });
    
    $('#primaryKeyDragList .list-group-item').on('dragenter', function(e) {
        $(this).addClass('over');
    });
    
    $('#primaryKeyDragList .list-group-item').on('dragleave', function(e) {
        $(this).removeClass('over');
    });
    
    $('#primaryKeyDragList .list-group-item').on('drop', function(e) {
        if (e.stopPropagation) e.stopPropagation();
        
        if (dragSrcEl !== this) {
            // 交换元素位置
            let srcIndex = $(dragSrcEl).index();
            let targetIndex = $(this).index();
            
            if (srcIndex < targetIndex) {
                $(this).after($(dragSrcEl));
            } else {
                $(this).before($(dragSrcEl));
            }
            
            // 更新显示顺序
            updatePrimaryKeyOrderDisplay();
        }
        
        $('.list-group-item').removeClass('dragging over');
        return false;
    });
    
    $('#primaryKeyDragList .list-group-item').on('dragend', function(e) {
        $(this).removeClass('dragging');
        $('.list-group-item').removeClass('over');
    });
}

// 更新主键顺序显示
function updatePrimaryKeyOrderDisplay() {
    let newPKs = collectPrimaryKeyOrder();
    // 不再显示新主键顺序（删除了 UI 元素）
}

// 从字段映射表格同步更新 targetTablePK
function updateTargetTablePKFromFieldMapping() {
    let pks = [];
    $('#fieldMappingList tr').each(function() {
        let $pkCell = $(this).find('td:eq(2)');
        let targetField = $(this).find('td:eq(1)').text().trim();
        
        // 检查是否有主键图标
        if ($pkCell.find('i.fa-key').length > 0) {
            pks.push(targetField);
        }
    });
    
    // 更新隐藏字段
    $('#targetTablePK').val(pks.join(','));
}

// 收集新的主键顺序
function collectPrimaryKeyOrder() {
    let pks = [];
    $('#primaryKeyDragList .list-group-item').each(function() {
        pks.push($(this).data('pk'));
    });
    return pks.join(',');
}

// 更新字段映射表格中的主键标记
function updatePrimaryKeyMarkers(pkString) {
    let pkArray = pkString ? pkString.split(',').map(s => s.trim()) : [];
    
    $('#fieldMappingList tr').each(function() {
        let targetField = $(this).find('td:eq(1)').text().trim();
        let $pkCell = $(this).find('td:eq(2)');
        
        if (pkArray.includes(targetField)) {
            if ($pkCell.find('i').length === 0) {
                $pkCell.html('<i title="主键" class="fa fa-key fa-fw text-warning"></i>');
            }
        } else {
            $pkCell.html('');
        }
    });
}

function checkFieldMappingDifferences() {
    let tableGroupId = $("#fieldDifferenceBtn").attr("tableGroupId");
    if (!tableGroupId) {
        return;
    }

    doPoster("/tableGroup/fieldDifference", {'id': tableGroupId}, function (data) {
        if (data.success == true && data.resultValue) {
            highlightFieldMappingDifferences(data.resultValue);
        }
    });
}

function highlightFieldMappingDifferences(result) {
    if (!result.hasDifference) {
        return;
    }
    
    let diffFieldNames = new Set();
    let fieldArrays = [
        result.addedFields,
        result.missingFields,
        result.typeMismatched,
        result.lengthMismatched
    ];
    
    fieldArrays.forEach(function(fields) {
        if (fields && fields.length > 0) {
            fields.forEach(function(item) {
                diffFieldNames.add(item.fieldName);
            });
        }
    });
    
    if (diffFieldNames.size === 0) {
        return;
    }
    
    let $fieldMappingList = $("#fieldMappingList");
    $fieldMappingList.find("tr").each(function() {
        let $tr = $(this);
        let sourceField = $tr.find("td:eq(0)").text().trim();
        let targetField = $tr.find("td:eq(1)").text().trim();
        
        if (diffFieldNames.has(sourceField) || diffFieldNames.has(targetField)) {
            $tr.addClass("field-diff-warning");
        }
    });
}

$(function() {
    // 初始化select插件
    initSelectIndex($(".select-control-table"), -1);
    
    // 修复 bootstrap-select 选中值不显示的问题
    fixBootstrapSelectDisplay();
    
    // 绑定表字段关系点击事件
    initFieldMappingParams();
    // 绑定表格拖拽事件
    bindFieldMappingDrop();
    // 绑定下拉选择事件自动匹配相似字段事件
    bindTableFieldSelect();
    
    // 延迟初始化过滤按钮，等待 selectpicker 完全初始化
    setTimeout(function() {
        // 只在没有按钮时才执行绑定，避免重复绑定
        if ($('.bs-show-all-source').length === 0 && $('.bs-show-all-target').length === 0) {
            bindFieldSelectFilterBtnClick();
        }
    }, 1000); // 增加到1秒
    
    // 绑定刷新表字段事件
    bindRefreshTableFieldsClick();
    // 绑定删除表字段映射事件
    bindFieldMappingCheckBoxClick();
    bindFieldMappingListClick();
    bindFieldMappingDelClick();
    
    // 绑定主键顺序按钮
    bindPrimaryKeyOrderBtn();

    // 返回按钮，跳转至上个页面
    $("#tableGroupBackBtn").bind('click', function(){
        backMappingPage($(this));
    });

    // 绑定字段差异按钮点击事件
    bindFieldDifferenceClick();
    
    // 绑定字段差异修复按钮点击事件
    bindFieldDiffFixClick();
    
    checkFieldMappingDifferences();
});

let fieldDifferenceData = null;

function bindFieldDifferenceClick() {
    let $diffBtn = $("#fieldDifferenceBtn");
    $diffBtn.bind('click', function(){
        let id = $(this).attr("tableGroupId");
        // 使用公共组件显示字段差异弹窗
        FieldDifferenceComponent.show(id, {
            showFixButton: true,
            onFix: function(data) {
                showFieldDiffFixPreview(id);
            }
        });
    });
}

function bindFieldDiffFixClick() {
    let $fixTargetBtn = $("#fixTargetTableBtn");
    let $confirmBtn = $("#confirmFieldDiffFixBtn");
    
    $fixTargetBtn.bind('click', function(){
        let id = $("#fieldDifferenceBtn").attr("tableGroupId");
        showFieldDiffFixPreview(id);
    });
    
    $confirmBtn.bind('click', function(){
        let id = $("#fieldDifferenceBtn").attr("tableGroupId");
        executeFieldDiffFix(id);
    });
}

function showFieldDiffFixPreview(id) {
    $('#fieldDiffFixModal').modal('show');
    $('#fieldDiffFixWarning').removeClass('alert-danger').addClass('alert-warning');
    $('#fieldDiffFixWarning').html('<strong>加载中...</strong>');
    $('#fieldDiffFixInfoContent').html('');
    $('#fieldDiffFixSqlContent').text('');
    $('#confirmFieldDiffFixBtn').prop('disabled', true);
    
    doPoster("/tableGroup/fieldDiffFixPreview", {'id': id}, function (data) {
        if (data.success === true) {
            renderFieldDiffFixPreview(data.resultValue);
        } else {
            $('#fieldDiffFixWarning').removeClass('alert-warning').addClass('alert-danger');
            $('#fieldDiffFixWarning').html('<strong>错误：</strong>' + (data.resultValue || '获取修复预览失败'));
        }
    });
}

function renderFieldDiffFixPreview(result) {
    window.currentFieldDiffFixResult = result;
    
    if (result.warning) {
        if (result.warning.indexOf('DROP') !== -1) {
            $('#fieldDiffFixWarning').removeClass('alert-warning').addClass('alert-danger');
        }
        $('#fieldDiffFixWarning').html('<strong>警告：</strong>' + result.warning);
    } else {
        $('#fieldDiffFixWarning').html('');
        $('#fieldDiffFixWarning').hide();
    }

    let infoHtml = '<div class="panel panel-default">' +
        '<div class="panel-heading">修复信息</div>' +
        '<div class="panel-body">' +
        '<p><strong>源表：</strong>' + result.sourceTableName + '</p>' +
        '<p><strong>目标表：</strong>' + result.targetTableName + '</p>' +
        '<p><strong>修复方向：</strong>以源表为基准修复目标表</p>' +
        '</div></div>';

    if (result.items && result.items.length > 0) {
        infoHtml += '<div class="panel panel-info">' +
            '<div class="panel-heading">修复项列表 <span class="badge">' + result.items.length + '</span></div>' +
            '<div class="panel-body" style="max-height: 250px; overflow-y: auto;">' +
            '<table class="table table-condensed">' +
            '<thead><tr><th><input type="checkbox" id="selectAllDiffItems" checked/></th><th>字段名</th><th>差异类型</th><th>操作</th><th>说明</th></tr></thead>' +
            '<tbody>';

        result.items.forEach(function(item) {
            infoHtml += '<tr>' +
                '<td><input type="checkbox" class="diff-item-checkbox" data-id="' + item.id + '" checked/></td>' +
                '<td><strong>' + item.fieldName + '</strong></td>' +
                '<td><span class="label label-' + getDiffTypeLabel(item.diffType) + '">' + item.diffType + '</span></td>' +
                '<td><span class="label label-' + getOperationLabel(item.operation) + '">' + item.operation + '</span></td>' +
                '<td>' + item.description + '</td>' +
                '</tr>';
        });

        infoHtml += '</tbody></table></div></div>';
    }

    $('#fieldDiffFixInfoContent').html(infoHtml);

    if (result.sqlStatements && result.sqlStatements.length > 0) {
        $('#fieldDiffFixSqlContent').text(result.sqlStatements.join('\n\n'));
        $('#confirmFieldDiffFixBtn').prop('disabled', false);
    } else {
        $('#fieldDiffFixSqlContent').text('无 SQL 语句需要执行');
        $('#confirmFieldDiffFixBtn').prop('disabled', true);
    }

    $('#selectAllDiffItems').on('change', function() {
        $('.diff-item-checkbox').prop('checked', $(this).prop('checked'));
        updateCurrentResultSelection();
    });

    $('.diff-item-checkbox').on('change', function() {
        updateCurrentResultSelection();
    });
}

function updateCurrentResultSelection() {
    if (window.currentFieldDiffFixResult && window.currentFieldDiffFixResult.items) {
        window.currentFieldDiffFixResult.items.forEach(function(item) {
            let $checkbox = $('.diff-item-checkbox[data-id="' + item.id + '"]');
            item.selected = $checkbox.prop('checked');
        });
    }
}

function getDiffTypeLabel(diffType) {
    switch(diffType) {
        case 'ADDED': return 'info';
        case 'MISSING': return 'warning';
        case 'TYPE_MISMATCH': return 'danger';
        case 'LENGTH_MISMATCH': return 'primary';
        default: return 'default';
    }
}

function getOperationLabel(operation) {
    switch(operation) {
        case 'ADD': return 'success';
        case 'DROP': return 'danger';
        case 'MODIFY': return 'warning';
        default: return 'default';
    }
}

function executeFieldDiffFix(id) {
    let $confirmBtn = $("#confirmFieldDiffFixBtn");
    $confirmBtn.prop('disabled', true);
    $confirmBtn.html('<span class="fa fa-spinner fa-spin"></span> 执行中...');
    
    let selectedIds = [];
    if (window.currentFieldDiffFixResult && window.currentFieldDiffFixResult.items) {
        window.currentFieldDiffFixResult.items.forEach(function(item) {
            if (item.selected) {
                selectedIds.push(item.id);
            }
        });
    }
    
    if (selectedIds.length === 0) {
        $('#fieldDiffFixWarning').removeClass('alert-warning').addClass('alert-danger');
        $('#fieldDiffFixWarning').html('<strong>错误：</strong>请至少选择一项进行修复');
        $confirmBtn.html('<span class="fa fa-check"></span> 确认执行');
        $confirmBtn.prop('disabled', false);
        return;
    }
    
    let params = {
        'id': id,
        'selectedIds': JSON.stringify(selectedIds)
    };
    
    doPoster("/tableGroup/executeFieldDiffFixSelective", params, function (data) {
        if (data.success === true) {
            $('#fieldDiffFixModal').modal('hide');
            $('#fieldDifferenceModal').modal('hide');
            
            $('.modal-backdrop').remove();
            $('body').removeClass('modal-open');
            $('body').css('padding-right', '');
            $('body').css('overflow', '');
            
            bootGrowl(data.resultValue, "success");
            
            let tableGroupId = $("#fieldDifferenceBtn").attr("tableGroupId");
            doLoader('/tableGroup/page/editTableGroup?id=' + tableGroupId);
        } else {
            $('#fieldDiffFixWarning').removeClass('alert-warning').addClass('alert-danger');
            $('#fieldDiffFixWarning').html('<strong>执行失败：</strong>' + (data.resultValue || '未知错误'));
            $confirmBtn.html('<span class="fa fa-check"></span> 确认执行');
            $confirmBtn.prop('disabled', false);
        }
    });
}

// ========== 主键差异确认弹窗相关函数 ==========

function showPrimaryKeyDifferenceModal(diffResult, originalData) {
    // 渲染差异详情
    let html = renderPkDifferenceHtml(diffResult);
    $('#pkDifferenceDetails').html(html);
    $('#pkDifferenceError').addClass('hidden');
    $('#primaryKeyDifferenceModal').modal('show');
    
    // 绑定按钮事件（使用 off 避免重复绑定）
    $('#pkDiffCancelBtn').off('click').on('click', function() {
        // 取消：直接回退到任务详情页面，不执行任何保存操作
        $('#primaryKeyDifferenceModal').modal('hide');
        
        // 清理 modal backdrop，防止页面变暗
        $('.modal-backdrop').remove();
        $('body').removeClass('modal-open');
        $('body').css('padding-right', '');
        $('body').css('overflow', '');
        
        backMappingPage($("#tableGroupSubmitBtn"));
    });
    
    $('#pkDiffExecuteBtn').off('click').on('click', function() {
        saveWithPrimaryKeyDDL(originalData);
    });
}

function renderPkDifferenceHtml(diffResult) {
    let html = '<div class="panel panel-default">' +
        '<div class="panel-heading">主键差异详情</div>' +
        '<div class="panel-body">' +
        '<p><strong>目标表：</strong>' + diffResult.tableName + '</p>';
    
    if (diffResult.addedPKs && diffResult.addedPKs.length > 0) {
        html += '<p class="text-success"><i class="fa fa-plus"></i> <strong>新增主键：</strong>' + 
            diffResult.addedPKs.join(', ') + '</p>';
    }
    
    if (diffResult.removedPKs && diffResult.removedPKs.length > 0) {
        html += '<p class="text-danger"><i class="fa fa-minus"></i> <strong>移除主键：</strong>' + 
            diffResult.removedPKs.join(', ') + '</p>';
    }
    
    html += '<hr/>' +
        '<p><strong>当前配置主键：</strong>' + (diffResult.configuredPKs || []).join(', ') + '</p>' +
        '<p><strong>目标表实际主键：</strong>' + (diffResult.actualPKs || []).join(', ') + '</p>' +
        '</div></div>';
    
    return html;
}

function saveWithPrimaryKeyDDL(originalData) {
    let $executeBtn = $("#pkDiffExecuteBtn");
    $executeBtn.prop('disabled', true);
    $executeBtn.html('<span class="fa fa-spinner fa-spin"></span> 执行中...');
    
    // 添加确认参数
    originalData.confirmPrimaryKeyChange = 'true';
    
    // 注意：originalData 现在包含完整的表单数据（包括 id）
    doPoster("/tableGroup/edit", originalData, function(data) {
        if (data.success == true) {
            $('#primaryKeyDifferenceModal').modal('hide');
            
            // 清理 modal backdrop，防止页面变暗
            $('.modal-backdrop').remove();
            $('body').removeClass('modal-open');
            $('body').css('padding-right', '');
            $('body').css('overflow', '');
            
            bootGrowl("保存成功!", "success");
            backMappingPage($("#tableGroupSubmitBtn"));
        } else {
            showPkDiffError(data.resultValue);
            $executeBtn.prop('disabled', false);
            $executeBtn.html('<i class="fa fa-check"></i> 执行（修改主键约束）');
        }
    });
}

function showPkDiffError(errorMessage) {
    $('#pkDifferenceError').removeClass('hidden');
    $('#pkDifferenceError').html('<strong>操作失败：</strong>' + errorMessage);
}