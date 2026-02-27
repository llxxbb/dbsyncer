// 绑定表格点击删除事件
function bindConfigListClick($del, $callback){
    $del.unbind("click");
    $del.bind('click', function(){
        // 阻止tr触发click事件
        event.cancelBubble=true;
        $(this).parent().parent().remove();
        $callback();
    });
    $callback();
}

// 初始化组合条件事件
function initConditionOperation() {
    const $conditionOperation = initSelectIndex($("#conditionOperation"));
    // 绑定插件下拉选择事件
    $conditionOperation.on('changed.bs.select', function (e) {
        const $isSql = "sql" == $(this).selectpicker('val');
        $("#conditionList").find("tr").each(function (k, v) {
            const $opr = $(this).find("td:eq(0)").text();
            if ($isSql) {
                $(this).remove();
            } else if (!$isSql && $opr == 'sql') {
                $(this).remove();
            }
        });
        initFilterParams();
    });
}

// 初始化过滤条件点击事件
function initFilter(){
    bindConfigListClick($(".conditionDelete"), function(){ initFilterParams(); });
}

// 初始化转换配置点击事件
function initConvert(){
    bindConfigListClick($(".convertDelete"), function(){ initConvertParams(); });
}

// 初始化映射关系参数
function initFilterParams(){
    // 生成JSON参数
    var row = [];
    var $conditionList = $("#conditionList");
    $conditionList.find("tr").each(function(k,v){
        var opt = $(this).find("td:eq(0)").text();
        var sf = $(this).find("td:eq(1)").text();
        var filter = $(this).find("td:eq(2)").text();
        var arg = $(this).find("td:eq(3)").text();
        row.push({
           "name": sf,
           "operation": opt,
           "filter": filter,
           "value": arg
         });
    });
    var $conditionTable = $("#conditionTable");
    if (0 >= row.length) {
        $conditionTable.addClass("hidden");
    } else {
        $conditionTable.removeClass("hidden");
    }
    $("#filter").val(JSON.stringify(row));
}

// 初始化转换配置参数
function initConvertParams(){
    // 生成JSON参数
    var row = [];
    var $convertList = $("#convertList");
    var existingConverts = [];
    
    // 收集现有的转换器
    $convertList.find("tr").each(function(k,v){
        var convert = $(this).find("td:eq(0)");
        var convertCode = convert.attr("value");
        var convertName = convert.text().replace(/\n/g,'').trim();
        var tf = $(this).find("td:eq(1)").text();
        var args = $(this).find("td:eq(2)").text();
        var isRoot = $(this).attr("data-isroot") === "true";
        
        // 获取 fieldMetadata（如果有）
        var fieldMetadataStr = $(this).attr("data-fieldmetadata");
        var fieldMetadata = null;
        if (fieldMetadataStr) {
            try {
                fieldMetadata = JSON.parse(fieldMetadataStr);
            } catch (e) {
                console.warn("解析 fieldMetadata 失败:", e);
            }
        }
        
        existingConverts.push({
            id: $(this).attr("data-id"),
            name: tf,
            convertName: convertName,
            convertCode: convertCode,
            args: args,
            isRoot: isRoot,
            fieldMetadata: fieldMetadata
        });
    });
    
    // 更新所有转换器的 isRoot 状态：最后一个新增的是根
    updateAllConvertRoots(existingConverts);
    
    // 生成JSON
    existingConverts.forEach(function(c) {
        var convertData = {
            id: c.id,
            name: c.name,
            convertName: c.convertName,
            convertCode: c.convertCode,
            args: c.args,
            isRoot: c.isRoot
        };
        
        // 如果有 fieldMetadata，添加到数据中
        if (c.fieldMetadata) {
            convertData.fieldMetadata = c.fieldMetadata;
        }
        
        row.push(convertData);
    });
    
    var $convertTable = $("#convertTable");
    if (0 >= row.length) {
        $convertTable.addClass("hidden");
    } else {
        $convertTable.removeClass("hidden");
    }
    $("#convert").val(JSON.stringify(row));
}

// 更新所有转换器的根状态
function updateAllConvertRoots(converts) {
    if (converts.length === 0) return;
    
    // 最后一个是根
    for (var i = 0; i < converts.length; i++) {
        converts[i].isRoot = (i === converts.length - 1);
    }
}

// 生成下一个数字ID
function generateNextId($convertList) {
    var maxId = -1;
    $convertList.find("tr").each(function() {
        var id = $(this).attr("data-id");
        if (id) {
            var num = parseInt(id);
            if (num > maxId) maxId = num;
        }
    });
    return String(maxId + 1);
}

// 绑定添加源字段按钮点击事件
function bindAddSourceFieldClick() {
    $("#addSourceFieldBtn").on('click', function() {
        var $select = $("#conditionSourceField");
        var customFieldName = prompt("请输入自定义字段名称：", "");
        if (customFieldName && customFieldName.trim() !== '') {
            var fieldName = customFieldName.trim();
            // 检查是否已存在
            if ($select.find('option[value="' + fieldName + '"]').length > 0) {
                bootGrowl("字段已存在，已为您选中", "info");
                $select.selectpicker('val', fieldName);
                return;
            }
            // 添加新选项
            $select.append(new Option(fieldName + ' (自定义)', fieldName, false, true));
            // 刷新selectpicker
            $select.selectpicker('refresh');
            // 选中新添加的选项
            $select.selectpicker('val', fieldName);
            bootGrowl("字段添加成功", "success");
        }
    });
}

// 绑定添加目标字段按钮点击事件
function bindAddTargetFieldClick() {
    $("#addTargetFieldBtn").on('click', function() {
        openFieldEditDialog(null);
    });
}

// 打开字段编辑对话框
function openFieldEditDialog(existingField) {
    // 重置表单
    $('#fieldEditForm')[0].reset();
    
    // 如果是编辑现有字段，填充数据
    if (existingField) {
        $('#fieldName').val(existingField.name);
        $('#fieldType').selectpicker('val', existingField.typeName);
        $('#fieldSize').val(existingField.columnSize || '');
        $('#fieldScale').val(existingField.ratio || '');
        $('#fieldNullable').prop('checked', existingField.nullable !== false);
        $('#fieldComment').val(existingField.comment || '');
    }
    
    // 初始化类型选择联动
    initFieldTypeChange();
    
    // 显示对话框
    $('#fieldEditDialog').modal('show');
}

// 初始化字段类型变化联动
function initFieldTypeChange() {
    var $fieldType = $('#fieldType');
    $fieldType.off('changed.bs.select').on('changed.bs.select', function() {
        var typeName = $(this).val();
        // 控制长度输入框显示
        var needSize = ['VARCHAR', 'CHAR', 'NVARCHAR', 'NCHAR', 'DECIMAL', 'NUMERIC'].indexOf(typeName) >= 0;
        if (needSize) {
            $('#fieldSizeGroup').show();
        } else {
            $('#fieldSizeGroup').hide();
            $('#fieldSize').val('');
        }
        // 控制精度输入框显示
        var needScale = ['DECIMAL', 'NUMERIC'].indexOf(typeName) >= 0;
        if (needScale) {
            $('#fieldScaleGroup').show();
        } else {
            $('#fieldScaleGroup').hide();
            $('#fieldScale').val('');
        }
    });
    // 触发一次变化事件
    $fieldType.trigger('changed.bs.select');
}

// 保存自定义字段
function saveCustomField() {
    var fieldName = $('#fieldName').val().trim();
    var fieldType = $('#fieldType').val();
    var fieldSize = $('#fieldSize').val();
    var fieldScale = $('#fieldScale').val();
    var fieldNullable = $('#fieldNullable').is(':checked');
    var fieldComment = $('#fieldComment').val().trim();
    
    // 验证必填字段
    if (!fieldName) {
        bootGrowl("字段名称不能为空", "danger");
        $('#fieldName').focus();
        return;
    }
    
    if (!fieldType) {
        bootGrowl("请选择字段类型", "danger");
        return;
    }
    
    // 类型编码映射
    var typeCodeMap = {
        'VARCHAR': 12,
        'CHAR': 1,
        'INT': 4,
        'BIGINT': -5,
        'DECIMAL': 3,
        'NUMERIC': 2,
        'FLOAT': 6,
        'DOUBLE': 8,
        'DATE': 91,
        'TIME': 92,
        'TIMESTAMP': 93,
        'BOOLEAN': 16,
        'TEXT': 2005
    };
    
    // 构建 Field 对象
    var fieldMetadata = {
        name: fieldName,
        typeName: fieldType,
        type: typeCodeMap[fieldType] || 12,
        columnSize: fieldSize ? parseInt(fieldSize) : 0,
        ratio: fieldScale ? parseInt(fieldScale) : 0,
        nullable: fieldNullable,
        comment: fieldComment,
        pk: false,
        autoincrement: false
    };
    
    // 添加到下拉列表
    var $select = $("#convertTargetField");
    var optionExists = $select.find('option[value="' + fieldName + '"]').length > 0;
    
    if (optionExists) {
        bootGrowl("字段已存在，已为您选中", "info");
        $select.selectpicker('val', fieldName);
    } else {
        // 创建新选项，将 fieldMetadata 存储在 data 属性中
        var $option = new Option(fieldName + ' (' + fieldType + ')', fieldName, false, true);
        $select.append($option);
        $($option).data('fieldMetadata', fieldMetadata);
        $select.selectpicker('refresh');
        $select.selectpicker('val', fieldName);
        bootGrowl("字段添加成功", "success");
    }
    
    $('#fieldEditDialog').modal('hide');
}

// 绑定新增条件点击事件
function bindConditionAddClick() {
    var $conditionAdd = $("#conditionAdd");
    $conditionAdd.unbind("click");
    $conditionAdd.bind('click', function () {
        var conditionOperation = $("#conditionOperation").selectpicker("val");
        var conditionSourceField = $("#conditionSourceField").selectpicker("val");
        var conditionFilter = $("#conditionFilter").selectpicker("val");
        var conditionArg = $("#conditionArg").val();
        var $conditionList = $("#conditionList");
        // 自定义SQL
        if (conditionOperation == 'sql') {
            if (isBlank(conditionArg)) {
                bootGrowl("参数不能空.", "danger");
                return;
            }
            $conditionList.html('');
            conditionSourceField = '';
            conditionFilter = '';
        }
        // 非空检查
        else if(isBlank(conditionSourceField)){
            bootGrowl("数据源表字段不能空.", "danger");
            return;
        }

        // 检查重复字段
        var repeated = false;
        $conditionList.find("tr").each(function(k,v){
             var opr = $(this).find("td:eq(0)").text();
             var sf = $(this).find("td:eq(1)").text();
             var filter = $(this).find("td:eq(2)").text();
             var arg = $(this).find("td:eq(3)").text();
             if(repeated = (opr==conditionOperation && sf==conditionSourceField && filter==conditionFilter && arg==conditionArg)){
                bootGrowl("过滤条件已存在.", "danger");
                // break;
                return false;
             }
        });
        if(repeated){ return; }

        var trHtml = "<tr>";
        trHtml += "<td>" + conditionOperation + "</td>";
        trHtml += "<td>" + conditionSourceField + "</td>";
        trHtml += "<td>" + conditionFilter + "</td>";
        trHtml += "<td>" + conditionArg + "</td>";
        trHtml += "<td><a class='fa fa-remove fa-2x conditionDelete dbsyncer_pointer' title='删除' ></a></td>";
        trHtml += "</tr>";
        $conditionList.append(trHtml);
        // 清空参数
        $("#conditionArg").val("");
        initFilter();
    });
}

// 绑定添加定时系统参数
function bindConditionQuartzFilterAddClick(){
    const $conditionQuartzFilter = $(".conditionQuartzFilter");
    $conditionQuartzFilter.unbind("click");
    $conditionQuartzFilter.bind('click', function () {
        const $tmpVal = $("#conditionArg");
        $tmpVal.val($tmpVal.val() + $(this).text());
    });
}

// 绑定新增转换点击事件
function bindConvertAddClick() {
    var $convertAdd = $("#convertAdd");
    $convertAdd.unbind("click");
    $convertAdd.bind('click', function () {
        var $convertList = $("#convertList");
        var $convertOperator = $("#convertOperator");
        var convertOperatorVal = $convertOperator.selectpicker("val");
        var convertOperatorText = $convertOperator.find("option:selected").text();
        var $selectedOption = $convertOperator.find("option:selected");
        // Thymeleaf 渲染后，th:argNum 会变成 argNum 属性
        var argNum = parseInt($selectedOption.attr("argNum") || "0");
        var convertTargetField = $("#convertTargetField").selectpicker("val");
        
        // 处理多选情况，只取第一个值
        if (Array.isArray(convertTargetField)) {
            convertTargetField = convertTargetField[0];
        }
        
        // 统一读取参数值到 args（表达式、固定值、普通参数都存储在 args 中）
        var convertArg = "";
        if (argNum === -1) {
            // 表达式/固定值：从 argNum == -1 的 input 读取
            convertArg = $("[data-arg-num='-1'] .convertParam").val();
            if (!convertArg || convertArg.trim() === '') {
                bootGrowl("表达式/固定值不能为空.", "danger");
                return;
            }
        } else if (argNum >= 1) {
            // 普通参数：从对应 argNum 的 input 读取
            var params = [];
            for (var i = 1; i <= argNum; i++) {
                var val = $("[data-arg-num='" + i + "'] .convertParam").val() || "";
                params.push(val);
            }
            convertArg = params.join(',');
        }
        
        // 非空检查
        if(convertTargetField == null || convertTargetField == undefined || convertTargetField == ''){
            bootGrowl("目标源表字段不能空.", "danger");
            return;
        }

        // 生成下一个ID（纯数字）
        var nextId = generateNextId($convertList);
        
        // 检查重复字段
        var repeated = false;
        $convertList.find("tr").each(function(k,v){
             var opr = $(this).find("td:eq(0)").text();
             var tf = $(this).find("td:eq(1)").text();
             var arg = $(this).find("td:eq(2)").text();
             if(repeated = (opr==convertOperatorText && tf==convertTargetField && arg==convertArg)){
                 bootGrowl("转换配置已存在.", "danger");
                 // break;
                 return false;
             }
        });
        if(repeated){ return; }

        // 获取选中选项的 fieldMetadata（如果有）
        var $selectedFieldOption = $("#convertTargetField option:selected");
        var fieldMetadata = $selectedFieldOption.data('fieldMetadata');
        
        var trHtml = "<tr";
        trHtml += " data-id='" + nextId + "'";
        trHtml += " data-isroot='true'";
        if (fieldMetadata) {
            trHtml += " data-fieldmetadata='" + JSON.stringify(fieldMetadata).replace(/'/g, "\\'") + "'";
        }
        trHtml += ">";
        trHtml += "<td value='" + convertOperatorVal + "'>" + convertOperatorText + "</td>";
        trHtml += "<td>" + convertTargetField + "</td>";
        trHtml += "<td>" + convertArg + "</td>";
        trHtml += "<td><a class='fa fa-remove fa-2x convertDelete dbsyncer_pointer' title='删除' ></a></td>";
        trHtml += "</tr>";
        $convertList.append(trHtml);
        // 清空参数
        $(".convertParam").val("");
        initConvert();
    });
}

// 根据 argNum 更新参数输入框的显示状态
function updateConvertParamsVisibility(argNum) {
    // 隐藏所有输入框
    $("[data-arg-num]").hide();
    
    // 根据 argNum 显示对应的输入框，并控制整个参数组的显示
    var $paramsGroup = $("#convertParamsGroup");
    if (argNum === -1) {
        // 显示表达式输入框（全宽）
        $("[data-arg-num='-1']").show();
        $paramsGroup.show();
    } else if (argNum >= 1) {
        // 显示对应数量的参数输入框
        for (var i = 1; i <= argNum && i <= 2; i++) {
            $("[data-arg-num='" + i + "']").show();
        }
        $paramsGroup.show();
    } else {
        // argNum 为 0 或无效时，隐藏整个参数组
        $paramsGroup.hide();
    }
}

// 绑定转换类型选择事件（根据 argNum 动态显示参数输入框）
function bindConvertOperatorChange() {
    var $convertOperator = $("#convertOperator");
    $convertOperator.on('changed.bs.select', function (e) {
        var $selectedOption = $(this).find("option:selected");
        // Thymeleaf 渲染后，th:argNum 会变成 argNum 属性
        var argNum = parseInt($selectedOption.attr("argNum") || "0");
        updateConvertParamsVisibility(argNum);
    });
}

// 初始化转换类型参数显示（页面加载时调用）
function initConvertParamsVisibility() {
    var $convertOperator = $("#convertOperator");
    if ($convertOperator.length > 0) {
        // 从原始 select 元素中获取选中的 option（不依赖 selectpicker UI）
        var selectedValue = $convertOperator.selectpicker('val');
        var $selectedOption = $convertOperator.find("option[value='" + selectedValue + "']");
        // 如果找不到，则使用第一个选中的 option
        if ($selectedOption.length === 0) {
            $selectedOption = $convertOperator.find("option:selected");
        }
        // 如果还是找不到，则使用第一个 option
        if ($selectedOption.length === 0) {
            $selectedOption = $convertOperator.find("option").first();
        }
        // Thymeleaf 渲染后，th:argNum 会变成 argNum 属性
        var argNum = parseInt($selectedOption.attr("argNum") || "0");
        updateConvertParamsVisibility(argNum);
    }
}

// 显示转换配置帮助弹窗
function showConvertHelpDialog() {
    // 从隐藏表格中读取转换类型数据
    var convertData = [];
    $("#convertHelpData tbody tr").each(function() {
        var $row = $(this);
        convertData.push({
            code: $row.find(".convert-code").text(),
            name: $row.find(".convert-name").text(),
            argNum: $row.find(".convert-argNum").text(),
            description: $row.find(".convert-description").text(),
            example: $row.find(".convert-example").html()
        });
    });
    
    // 构建描述文字部分
    var descriptionHtml = '<div style="padding: 10px 0;">' +
        '<p><strong>转换配置：为目标字段指定转换规则</strong></p>' +
        '<p class="text-info" style="margin: 10px 0;">' +
        '• 字段有值：对已有字段值进行转换（如：AES 加密、类型转换）<br>' +
        '• 字段无值：生成新字段值（如：固定值、默认值）<br>' +
        '• 支持选择任何目标表字段，包括无源字段映射的字段' +
        '</p>' +
        '</div>';
    
    // 构建转换类型说明表格
    var tableHtml = '<div class="table-responsive" style="margin-top: 15px;">' +
        '<table class="table table-bordered table-condensed">' +
        '<thead>' +
        '<tr>' +
        '<th style="width: 20%;">转换类型</th>' +
        '<th style="width: 15%;">参数个数</th>' +
        '<th style="width: 30%;">说明</th>' +
        '<th style="width: 35%;">示例</th>' +
        '</tr>' +
        '</thead>' +
        '<tbody>';
    
    // 添加表格行
    for (var i = 0; i < convertData.length; i++) {
        var c = convertData[i];
        tableHtml += '<tr>' +
            '<td><strong>' + escapeHtml(c.name) + '</strong></td>' +
            '<td>' + escapeHtml(c.argNum) + '</td>' +
            '<td>' + escapeHtml(c.description) + '</td>' +
            '<td>' + c.example + '</td>' +
            '</tr>';
    }
    
    tableHtml += '</tbody></table></div>';
    
    // 组合完整内容
    var contentHtml = descriptionHtml + tableHtml;
    
    // 显示弹窗
    BootstrapDialog.show({
        title: "转换配置说明",
        type: BootstrapDialog.TYPE_INFO,
        message: contentHtml,
        size: BootstrapDialog.SIZE_WIDE,
        buttons: [{
            label: "关闭",
            cssClass: "btn-default",
            action: function (dialog) {
                dialog.close();
            }
        }]
    });
}

// HTML 转义函数
function escapeHtml(text) {
    var map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    return text ? text.replace(/[&<>"']/g, function(m) { return map[m]; }) : '';
}

// 绑定转换配置帮助图标点击事件
function bindConvertHelpIconClick() {
    $("#convertHelpIcon").unbind("click");
    $("#convertHelpIcon").bind('click', function () {
        showConvertHelpDialog();
    });
}

// 页面初始化
$(function() {
    initSelectIndex($(".select-control"), 1);
    initConditionOperation();
    // 过滤条件
    initFilter();
    bindConditionAddClick();
    bindConditionQuartzFilterAddClick();
    // 处理自定义字段选择
    bindAddSourceFieldClick();
    bindAddTargetFieldClick();
    // 转换配置
    initConvert();
    bindConvertAddClick();
    bindConvertOperatorChange();
    bindConvertHelpIconClick();
    // 初始化转换类型参数显示（需要在 selectpicker 初始化后调用）
    // 使用 setTimeout 确保 selectpicker 完全初始化完成
    setTimeout(function() {
        initConvertParamsVisibility();
    }, 200);
    
    // 绑定字段编辑对话框确认按钮
    $('#confirmFieldEdit').on('click', function() {
        saveCustomField();
    });
});
