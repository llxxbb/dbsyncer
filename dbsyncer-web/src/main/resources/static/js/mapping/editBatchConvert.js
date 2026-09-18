/**
 * 任务编辑页 —— 批量转换配置模块（从 edit.js 拆出，Stage 5/5）
 *
 * 职责：批量转换配置弹窗（#batchConvertConfigModal）的全部逻辑——类型/参数联动、
 *       应用范围计数、字段类型加载、逐表组下发转换配置（复用已有字段或新建字段）、
 *       以及转换类型的中文名映射。
 *
 * 加载顺序约束（重要）：
 *   必须排在 edit.js 之前。edit.js 的主 ready 块在脚本求值期间就会同步调用
 *   initBatchConvertConfig()，而 SPA 外壳里 jQuery 的 ready 回调是同步执行的，
 *   晚加载会 ReferenceError。
 *
 * 反向依赖（都在回调内、运行时解析，与加载顺序无关）：
 *   - edit.js：getCheckedBoxSize —— 页面共用的勾选收集工具（10 处调用点），
 *     故意留在 edit.js，不随本模块搬出。
 *   - editFilterAndConvert.js：getDatabaseFieldTypes —— 该文件排在 edit.js 之后，
 *     本模块只在用户点击后的 loadBatchFieldTypes() 里调用它，求值期不触碰。
 *   initBatchConvertConfig() 本身只注册事件处理器，求值期不调用任何外部函数，
 *   因此前置加载不会踩到上述两个「后加载」的依赖。
 *
 * 其他对外依赖：common.js 的 doPoster / bootGrowl，插件 jQuery / Bootstrap modal。
 *
 * 定时器：含 2 个一次性 setTimeout（1s / 2s），均以 location.reload() 整页刷新收尾，
 *   不持有句柄、不是轮询，因此不涉及 ADR-0032 的页面代际守卫问题。
 *
 * 本模块不声明任何全局变量。
 */

// 批量转换配置功能
function initBatchConvertConfig() {
    $('#batchConvertType').on('change', function() {
        var type = $(this).val();
        if (type === 'UUID') {
            $('#batchConvertArgsGroup').hide();
            $('#batchConvertArgs').val('');
        } else {
            $('#batchConvertArgsGroup').show();
        }
    });
    
    $('#batchConvertConfigBtn').on('click', function() {
        var selectedIds = getCheckedBoxSize($('.tableGroupCheckbox'));
        if (selectedIds.length === 0) {
            bootGrowl("请至少选中一个表组", "warning");
            return;
        }
        updateBatchConvertCounts();
        loadBatchFieldTypes();
        $('#batchConvertConfigModal').modal('show');
    });
    
    $('#batchConvertSubmitBtn').on('click', function() {
        submitBatchConvertConfig();
    });
}

function updateBatchConvertCounts() {
    // 弹窗底部只展示「已选中 N 个映射关系」（#selectedCount）。
    // 原先还往 #allTableGroupCount 写总数，但任何模板里都没有这个元素，属于死写入，已删除。
    var selectedCount = getCheckedBoxSize($('.tableGroupCheckbox')).length;
    $('#selectedCount').text(selectedCount);
}

function loadBatchFieldTypes() {
    var $fieldType = $('#batchConvertFieldType');
    $fieldType.selectpicker('destroy');
    $fieldType.empty().append('<option value="">请选择</option>');
    
    var connectorType = $('#batchConvertConfigBtn').data('connectorType');
    if (!connectorType) {
        var $targetImg = $('.panel-body img[src*="/img/"]').last();
        if ($targetImg.length > 0) {
            var targetSrc = $targetImg.attr('src');
            var match = targetSrc.match(/\/img\/([^.]+)\.png/);
            if (match && match[1]) {
                connectorType = match[1].toLowerCase();
            }
        }
    }
    
    if (!connectorType) {
        connectorType = 'mysql';
    }
    
    var allTypes = getDatabaseFieldTypes(connectorType);
    allTypes.forEach(function(type) {
        $fieldType.append('<option value="' + type + '">' + type + '</option>');
    });
    
    $fieldType.selectpicker({
        "title": "请选择",
        "liveSearch": true,
        "noneResultsText": "没有找到 {0}"
    });
}

function findJDBCTypeCodeByName(typeName) {
    var typeMap = {
        'VARCHAR': 12, 'NVARCHAR': -9, 'CHAR': 1, 'NCHAR': -15,
        'TEXT': -1, 'CLOB': 2005, 'NCLOB': 2011,
        'INT': 4, 'INTEGER': 4, 'BIGINT': -5, 'SMALLINT': 5, 'TINYINT': -6,
        'FLOAT': 6, 'DOUBLE': 8, 'REAL': 7, 'DECIMAL': 3, 'NUMERIC': 2,
        'BIT': -7, 'BOOLEAN': 16,
        'DATE': 91, 'TIME': 92, 'TIMESTAMP': 93, 'DATETIME': 93,
        'BLOB': 2004, 'BINARY': -2, 'VARBINARY': -3,
        'XML': 2009, 'JSON': 2005, 'UUID': 1,
        'NUMBER': 2, 'VARCHAR2': 12, 'NVARCHAR2': -9,
        'SERIAL': 4, 'BIGSERIAL': -5,
        'MONEY': 2, 'IMAGE': -4, 'UNIQUEIDENTIFIER': 1
    };
    return typeMap[typeName.toUpperCase()] || 12;
}

function submitBatchConvertConfig() {
    var targetFieldName = $('#batchConvertTargetField').val().trim();
    if (!targetFieldName) {
        bootGrowl("请输入目标字段名称", "danger");
        $('#batchConvertTargetField').focus();
        return;
    }
    
    var fieldType = $('#batchConvertFieldType').val();
    if (!fieldType) {
        bootGrowl("请选择字段类型", "danger");
        return;
    }
    
    var fieldSize = $('#batchConvertFieldSize').val().trim();
    var fieldScale = $('#batchConvertFieldScale').val().trim();
    var fieldNullable = $('#batchConvertFieldNullable').is(':checked');
    var fieldComment = $('#batchConvertFieldComment').val().trim();
    var setTempPK = $('#batchConvertSetTempPK').is(':checked');
    
    var convertConfig = {
        convertType: $('#batchConvertType').val(),
        args: $('#batchConvertArgs').val()
    };
    
    if (convertConfig.convertType !== 'UUID' && !convertConfig.args) {
        bootGrowl("请输入参数值", "danger");
        return;
    }
    
    var mappingId = $('#batchConvertConfigBtn').attr('mappingId');
    
    // 按选中处理，与删除/重新同步保持一致
    var selectedTableGroups = getCheckedBoxSize($('.tableGroupCheckbox'));
    
    if (selectedTableGroups.length === 0) {
        bootGrowl("请选择要操作的表组", "warning");
        return;
    }
    
    var fieldMetadata = {
        name: targetFieldName,
        typeName: fieldType,
        type: findJDBCTypeCodeByName(fieldType),
        columnSize: fieldSize ? parseInt(fieldSize) : 0,
        ratio: fieldScale ? parseInt(fieldScale) : 0,
        nullable: fieldNullable,
        comment: fieldComment,
        pk: false,
        autoincrement: false,
        isCustom: true
    };
    
    bootGrowl("正在批量添加转换配置，请稍候...", "info");
    $('#batchConvertSubmitBtn').prop('disabled', true);
    
    
    $.ajax({
        url: '/mapping/getTableGroups?id=' + mappingId,
        type: 'GET',
        success: function(data) {
            if (data.success && data.resultValue) {
                // 设置为主键时，先预确认
                if (setTempPK) {
                    var pkChanges = [];
                    selectedTableGroups.forEach(function(tgId) {
                        var tableGroup = data.resultValue.find(function(tg) { return tg.id === tgId; });
                        if (tableGroup) {
                            // 从 targetTable.primaryKeys 获取现有主键（而非 targetTablePK）
                            var existingPKs = (tableGroup.targetTable && tableGroup.targetTable.primaryKeys) ? tableGroup.targetTable.primaryKeys : [];
                            var oldPK = existingPKs.filter(function(s) { return s; }).join(',') || "(无)";
                            // 去重：只显示实际新增的字段（不区分大小写）
                            var pkArray = (oldPK === "(无)") ? [] : oldPK.split(',').map(function(s) { return s.trim(); }).filter(function(s) { return s; });
                            var exists = pkArray.some(function(pk) { return pk.toLowerCase() === targetFieldName.toLowerCase(); });
                            if (!exists) {
                                pkArray.push(targetFieldName);
                            }
                            var newPK = pkArray.length > 0 ? pkArray.join(',') : "(无)";
                            var sourceName = tableGroup.sourceTable ? tableGroup.sourceTable.name : '';
                            var targetName = tableGroup.targetTable ? tableGroup.targetTable.name : '';
                            // 仅当主键实际发生变化时才加入提示列表
                            if (oldPK !== newPK) {
                                pkChanges.push(sourceName + ' → ' + targetName + '：' + oldPK + ' → ' + newPK);
                            }
                        }
                    });
                    
                    if (pkChanges.length === 0) {
                        // 主键均已存在，无需修改，直接执行批量添加
                        batchAddConvertToTableGroups(data.resultValue, selectedTableGroups, convertConfig, targetFieldName, fieldMetadata, 0, 0, [], 0, 0, setTempPK);
                        return;
                    }
                    
                    var message = '<strong>以下表组的主键将被修改：</strong><br><br>' + pkChanges.join('<br>');
                    
                    BootstrapDialog.confirm({
                        title: "确认修改主键",
                        message: message,
                        type: BootstrapDialog.TYPE_WARNING,
                        btnOKLabel: "确认",
                        btnCancelLabel: "取消",
                        callback: function(result) {
                            if (result) {
                                // 确认后执行批量添加
                                batchAddConvertToTableGroups(data.resultValue, selectedTableGroups, convertConfig, targetFieldName, fieldMetadata, 0, 0, [], 0, 0, setTempPK);
                            } else {
                                $('#batchConvertSubmitBtn').prop('disabled', false);
                            }
                        }
                    });
                } else {
                    // 不设置主键，直接执行
                    batchAddConvertToTableGroups(data.resultValue, selectedTableGroups, convertConfig, targetFieldName, fieldMetadata, 0, 0, [], 0, 0, setTempPK);
                }
            } else {
                bootGrowl("获取表组列表失败", "danger");
                $('#batchConvertSubmitBtn').prop('disabled', false);
            }
        },
        error: function() {
            bootGrowl("获取表组列表失败", "danger");
            $('#batchConvertSubmitBtn').prop('disabled', false);
        }
    });
}

function batchAddConvertToTableGroups(allTableGroups, tableGroupIds, convertConfig, targetFieldName, fieldMetadata, currentIndex, successCount, failMessages, useExistingCount, createNewCount, setTempPK) {
    if (currentIndex >= tableGroupIds.length) {
        var totalCount = tableGroupIds.length;
        var failCount = failMessages.length;
        
        if (failCount === 0) {
            var message = "批量转换配置添加成功！共处理 " + totalCount + " 个表组";
            if (useExistingCount > 0 || createNewCount > 0) {
                message += "（复用已有字段：" + useExistingCount + " 个，创建新字段：" + createNewCount + " 个）";
            }
            bootGrowl(message, "success");
            $('#batchConvertConfigModal').modal('hide');
            setTimeout(function() {
                location.reload();
            }, 1000);
        } else {
            var message = "批量处理完成：成功 " + successCount + " 个，失败 " + failCount + " 个";
            if (useExistingCount > 0 || createNewCount > 0) {
                message += "（复用已有字段：" + useExistingCount + " 个，创建新字段：" + createNewCount + " 个）";
            }
            if (failMessages.length > 0) {
                var showMessages = failMessages.length <= 5 ? failMessages : failMessages.slice(0, 5);
                message += "\n失败详情：" + showMessages.join(", ");
                if (failMessages.length > 5) {
                    message += " ...等" + failMessages.length + "个";
                }
            }
            bootGrowl(message, failCount === totalCount ? "danger" : "warning");
            $('#batchConvertSubmitBtn').prop('disabled', false);
            setTimeout(function() {
                location.reload();
            }, 2000);
        }
        return;
    }
    
    var tableGroupId = tableGroupIds[currentIndex];
    var tableGroup = null;
    
    for (var i = 0; i < allTableGroups.length; i++) {
        if (allTableGroups[i].id === tableGroupId) {
            tableGroup = allTableGroups[i];
            break;
        }
    }
    
    if (tableGroup) {
        addConvertToSingleTableGroup(tableGroup, convertConfig, targetFieldName, fieldMetadata, setTempPK, function(success, errorMsg, actionType) {
            if (success) {
                successCount++;
                if (actionType === 'use_existing') {
                    useExistingCount++;
                } else if (actionType === 'create_new') {
                    createNewCount++;
                }
            } else {
                failMessages.push((tableGroup.targetTable ? tableGroup.targetTable.name : tableGroupId) + ": " + (errorMsg || "未知错误"));
            }
            batchAddConvertToTableGroups(allTableGroups, tableGroupIds, convertConfig, targetFieldName, fieldMetadata, currentIndex + 1, successCount, failMessages, useExistingCount, createNewCount, setTempPK);
        });
    } else {
        failMessages.push(tableGroupId + ": 获取表组失败");
        batchAddConvertToTableGroups(allTableGroups, tableGroupIds, convertConfig, targetFieldName, fieldMetadata, currentIndex + 1, successCount, failMessages, useExistingCount, createNewCount, setTempPK);
    }
}

function addConvertToSingleTableGroup(tableGroup, convertConfig, targetFieldName, userFieldMetadata, setTempPK, callback) {
    var converts = tableGroup.convert || [];
    var targetTable = tableGroup.targetTable;
    var existingField = null;
    
    // 前端检查：目标表是否已有该字段（仅基于名称精确匹配）
    if (targetTable && targetTable.column) {
        for (var i = 0; i < targetTable.column.length; i++) {
            if (targetTable.column[i].name === targetFieldName) {
                existingField = targetTable.column[i];
                break;
            }
        }
    }
    
    // 前端决定：使用哪个字段元数据
    var finalFieldMetadata;
    if (existingField) {
        // 字段已存在 → 使用目标表的实际字段信息
        // 但仍然标记为 isCustom: true，因为这是用户手动添加的转换配置
        finalFieldMetadata = {
            name: existingField.name,
            typeName: existingField.typeName,
            type: existingField.type,
            columnSize: existingField.columnSize,
            ratio: existingField.ratio,
            nullable: existingField.nullable,
            comment: existingField.comment,
            pk: existingField.pk,
            autoincrement: existingField.autoincrement,
            isCustom: true
        };
    } else {
        // 字段不存在 → 使用用户输入的信息（用于创建新字段）
        finalFieldMetadata = userFieldMetadata;
        finalFieldMetadata.isCustom = true;
    }
    
    // 前端检查：转换配置是否重复（基于字段名 + 转换类型，不区分大小写）
    var exists = false;
    for (var i = 0; i < converts.length; i++) {
        if (converts[i].name.toLowerCase() === targetFieldName.toLowerCase() && converts[i].convertCode === convertConfig.convertType) {
            exists = true;
            break;
        }
    }
    
    // 前端构造：新的转换配置
    if (!exists) {
        var newConvert = {
            id: String(converts.length),
            name: targetFieldName,
            convertName: getConvertTypeName(convertConfig.convertType),
            convertCode: convertConfig.convertType,
            args: convertConfig.args || "",
            isRoot: true,
            fieldMetadata: finalFieldMetadata
        };
        
        converts.push(newConvert);
    }
    
    // ADR-0011: FieldMapping 的传输格式只有 source/target 两个字段名（字符串），不再持有
    // Field 对象；后端 TableGroupChecker.setFieldMapping() 与 FieldMapping 的反序列化器
    // 也都只读这两个键，因此这里不再携带 pk（主键另由 targetTablePK 参数传递）。
    var fieldMappingData = [];
    if (tableGroup.fieldMapping) {
        tableGroup.fieldMapping.forEach(function(fm) {
            fieldMappingData.push({
                source: fm.source || "",
                target: fm.target || ""
            });
        });
    }
    
    // 新增自定义字段时，同时添加到字段映射列表（source 为空表示纯目标字段）
    if (!exists) {
        fieldMappingData.push({
            source: "",
            target: targetFieldName
        });
    }
    
    // 前端构造：提交参数
    var params = {
        'id': tableGroup.id,
        'targetTable': (tableGroup.targetTable && tableGroup.targetTable.name) ? tableGroup.targetTable.name : '',
        'fieldMapping': JSON.stringify(fieldMappingData),
        'convert': JSON.stringify(converts)
    };
    
    // 设置为主键：将新字段追加到 targetTablePK（去重，不区分大小写）
    if (setTempPK) {
        // 从 targetTable.primaryKeys 获取现有主键数组
        var existingPKs = (tableGroup.targetTable && tableGroup.targetTable.primaryKeys) ? tableGroup.targetTable.primaryKeys : [];
        var pkArray = existingPKs.map(function(s) { return typeof s === 'string' ? s.trim() : ''; }).filter(function(s) { return s; });
        // 不区分大小写检查是否已存在
        var exists = pkArray.some(function(pk) { return pk.toLowerCase() === targetFieldName.toLowerCase(); });
        if (!exists) {
            pkArray.push(targetFieldName);
        }
        params['targetTablePK'] = pkArray.join(',');
        params['confirmPrimaryKeyChange'] = 'true';
    } else {
        // 不设置主键时，也需要传递当前主键，避免被覆盖
        var existingPKs = (tableGroup.targetTable && tableGroup.targetTable.primaryKeys) ? tableGroup.targetTable.primaryKeys : [];
        params['targetTablePK'] = existingPKs.filter(function(s) { return s; }).join(',');
    }
    
    // 调用现有后端接口保存
    doPoster("/tableGroup/edit", params, function(response) {
        if (response.success) {
            callback(true, null, existingField ? 'use_existing' : 'create_new');
        } else {
            callback(false, response.resultValue || "添加转换配置失败", null);
        }
    });
}

function getConvertTypeName(convertCode) {
    var typeMap = {
        'DEFAULT': '默认值',
        'FIXED': '固定值',
        'UUID': 'UUID',
        'TEMPLATE': '表达式',
        'SHA1': 'SHA1加密',
        'DATE': '日期格式化'
    };
    return typeMap[convertCode] || convertCode;
}

//# sourceURL=/js/mapping/editBatchConvert.js
