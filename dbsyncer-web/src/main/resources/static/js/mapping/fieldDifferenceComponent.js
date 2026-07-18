/**
 * 字段差异弹窗公共组件
 * 用于在多个页面中复用字段差异展示功能
 */

// 字段差异弹窗组件
var FieldDifferenceComponent = {
    currentData: null,
    _cachedData: null,
    _cachedTableGroupId: null,
    _fixResult: null,

    show: function(tableGroupId, options) {
        options = options || {};
        var showFix = options.showFixButton !== false;

        if (showFix) {
            $('#fixTargetTableBtn').show();
        } else {
            $('#fixTargetTableBtn').hide();
        }

        $('#fieldDifferenceModal').modal('show');
        $('#fieldDifferenceContent').html('<div class="text-center"><span class="fa fa-spinner fa-spin fa-2x"></span> 加载中...</div>');

        this.onFixCallback = options.onFix;

        var self = this;
        if (this._cachedData && this._cachedTableGroupId === tableGroupId) {
            self.currentData = this._cachedData;
            self.render(this._cachedData);
        } else {
            doPoster("/tableGroup/fieldDifference", {'id': tableGroupId}, function(data) {
                if (data.success == true) {
                    self.currentData = data.resultValue;
                    self._cachedData = data.resultValue;
                    self._cachedTableGroupId = tableGroupId;
                    self.render(data.resultValue);
                } else {
                    $('#fieldDifferenceContent').html('<div class="alert alert-danger">' + data.resultValue + '</div>');
                }
            });
        }
    },

    /**
     * 设置缓存数据（页面预加载差异数据时调用）
     */
    setCachedData: function(tableGroupId, data) {
        this._cachedData = data;
        this._cachedTableGroupId = tableGroupId;
    },

    /**
     * 提取所有差异字段名（供高亮和弹窗共用）
     */
    getDiffFieldNames: function(result) {
        var diffFieldNames = new Set();
        var fieldArrays = [
            result.mappingOnlyFields,
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
        return diffFieldNames;
    },

    /**
     * 渲染字段差异详情
     * @param {Object} result - 字段差异数据
     */
    render: function(result) {
        if (!result || !result.hasDifference) {
            $('#fieldDifferenceContent').html(
                '<div class="alert alert-success text-center">' +
                '<span class="fa fa-check-circle fa-2x"></span><br>' +
                '<strong>字段结构一致</strong><br>' +
                '<small>数据源表和目标源表的字段结构完全匹配</small>' +
                '</div>'
            );
            return;
        }

        var html = '<div class="field-difference-container">';

        if (result.mappingOnlyFields && result.mappingOnlyFields.length > 0) {
            html += this.buildSection(
                'Mapping 配置多余字段',
                'fa-exclamation-triangle',
                'danger',
                result.mappingOnlyFields.length,
                result.mappingOnlyFields,
                'mappingOnly'
            );
        }

        if (result.addedFields && result.addedFields.length > 0) {
            html += this.buildSection(
                '目标比源表多出的字段',
                'fa-plus-circle',
                'info',
                result.addedFields.length,
                result.addedFields,
                'added'
            );
        }

        if (result.missingFields && result.missingFields.length > 0) {
            html += this.buildSection(
                '目标比源表表缺少的字段',
                'fa-minus-circle',
                'warning',
                result.missingFields.length,
                result.missingFields,
                'missing'
            );
        }

        if (result.typeMismatched && result.typeMismatched.length > 0) {
            html += this.buildSection(
                '类型不匹配的字段',
                'fa-exchange',
                'danger',
                result.typeMismatched.length,
                result.typeMismatched,
                'type'
            );
        }

        if (result.lengthMismatched && result.lengthMismatched.length > 0) {
            html += this.buildSection(
                'VARCHAR长度差异',
                'fa-arrows-h',
                'primary',
                result.lengthMismatched.length,
                result.lengthMismatched,
                'length'
            );
        }

        html += '</div>';
        $('#fieldDifferenceContent').html(html);
    },

    /**
     * 构建差异区块HTML
     */
    buildSection: function(title, icon, alertClass, count, items, type) {
        var self = this;
        // 类型不匹配使用自定义样式替代 panel-danger
        var panelClass = type === 'type' ? 'panel-default type-mismatch-panel' : 'panel-' + alertClass;
        var html = '<div class="' + panelClass + '">' +
            '<div class="panel-heading">' +
            '<span class="fa ' + icon + '"></span> ' + title +
            ' <span class="badge">' + count + '</span>';

        // Mapping Only 区块：添加一键移除按钮
        if (type === 'mappingOnly') {
            html += ' <button type="button" class="btn btn-xs btn-danger pull-right" onclick="FieldDifferenceComponent.removeAllMappingOnly()">' +
                '<span class="fa fa-trash"></span> 一键移除全部</button>';
        }

        html += '</div>' +
            '<div class="panel-body">' +
            '<table class="table table-condensed table-hover">' +
            '<thead><tr><th>字段名</th>';

        if (type === 'added') {
            html += '<th>目标类型</th><th>目标长度</th>';
        } else if (type === 'missing') {
            html += '<th>源类型</th><th>源长度</th>';
        } else if (type === 'type') {
            html += '<th>源类型</th><th>目标类型</th>';
        } else if (type === 'length') {
            html += '<th>源长度</th><th>目标长度</th>';
        } else if (type === 'mappingOnly') {
            html += '<th>Target 类型</th><th>Target 长度</th>';
            html += '<th style="width:100px;">操作</th>';
        }

        html += '<th>说明</th></tr></thead><tbody>';

        items.forEach(function(item) {
            html += '<tr data-field-name="' + item.fieldName + '">';
            html += '<td><strong>' + item.fieldName + '</strong></td>';

            if (type === 'added') {
                html += '<td>' + (item.targetType || '-') + '</td>';
                html += '<td>' + (item.targetLength || '-') + '</td>';
            } else if (type === 'missing') {
                html += '<td>' + (item.sourceType || '-') + '</td>';
                html += '<td>' + (item.sourceLength || '-') + '</td>';
            } else if (type === 'type') {
                html += '<td>' + (item.sourceType || '-') + '</td>';
                html += '<td>' + (item.targetType || '-') + '</td>';
            } else if (type === 'length') {
                html += '<td>' + (item.sourceLength || '-') + '</td>';
                html += '<td>' + (item.targetLength || '-') + '</td>';
            } else if (type === 'mappingOnly') {
                html += '<td>' + (item.targetType || '-') + '</td>';
                html += '<td>' + (item.targetLength || '-') + '</td>';
                html += '<td>' +
                    '<button type="button" class="btn btn-xs btn-danger" ' +
                    'onclick="FieldDifferenceComponent.removeMappingOnly(\'' + item.fieldName + '\', this)">' +
                    '<span class="fa fa-trash"></span> 移除</button>' +
                    '</td>';
            }

            html += '<td>' + (item.description || '') + '</td>';
            html += '</tr>';
        });

        html += '</tbody></table></div></div>';
        return html;
    },

    /**
     * 获取当前字段差异数据
     */
    getCurrentData: function() {
        return this.currentData;
    },

    clear: function() {
        this.currentData = null;
        this.onFixCallback = null;
        this._cachedData = null;
        this._fixResult = null;
    },

    _onFixBtnClick: function() {
        var id = this._cachedTableGroupId;
        if (!id) return;
        this._showFixPreview(id);
    },

    _showFixPreview: function(id) {
        $('#fieldDiffFixModal').modal('show');
        $('#fieldDiffFixWarning').removeClass('alert-danger').addClass('alert-warning').html('<strong>加载中...</strong>').show();
        $('#fieldDiffFixInfoContent').html('');
        $('#fieldDiffFixSqlContent').text('');
        $('#confirmFieldDiffFixBtn').prop('disabled', true);

        var self = this;
        doPoster("/tableGroup/fieldDiffFixPreview", {'id': id}, function(data) {
            if (data.success === true) {
                self._renderFixPreview(data.resultValue);
            } else {
                $('#fieldDiffFixWarning').removeClass('alert-warning').addClass('alert-danger');
                $('#fieldDiffFixWarning').html('<strong>错误：</strong>' + (data.resultValue || '获取修复预览失败'));
            }
        });
    },

    _renderFixPreview: function(result) {
        this._fixResult = result;

        if (result.warning) {
            if (result.warning.indexOf('DROP') !== -1) {
                $('#fieldDiffFixWarning').removeClass('alert-warning').addClass('alert-danger');
            }
            $('#fieldDiffFixWarning').html('<strong>警告：</strong>' + result.warning);
        } else {
            $('#fieldDiffFixWarning').html('').hide();
        }

        var self = this;
        var infoHtml = '<div class="panel panel-default">' +
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
                    '<td><span class="label label-' + self._getDiffTypeLabel(item.diffType) + '">' + item.diffType + '</span></td>' +
                    '<td><span class="label label-' + self._getOperationLabel(item.operation) + '">' + item.operation + '</span></td>' +
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

        $('#selectAllDiffItems').off('change').on('change', function() {
            $('.diff-item-checkbox').prop('checked', $(this).prop('checked'));
            self._updateFixSelection();
        });

        $('.diff-item-checkbox').off('change').on('change', function() {
            self._updateFixSelection();
        });
    },

    _updateFixSelection: function() {
        if (this._fixResult && this._fixResult.items) {
            this._fixResult.items.forEach(function(item) {
                var $checkbox = $('.diff-item-checkbox[data-id="' + item.id + '"]');
                item.selected = $checkbox.prop('checked');
            });
        }
    },

    _executeFix: function() {
        var self = this;
        var id = this._cachedTableGroupId;
        var $confirmBtn = $("#confirmFieldDiffFixBtn");
        $confirmBtn.prop('disabled', true);
        $confirmBtn.html('<span class="fa fa-spinner fa-spin"></span> 执行中...');

        var selectedIds = [];
        if (this._fixResult && this._fixResult.items) {
            this._fixResult.items.forEach(function(item) {
                if (item.selected) {
                    selectedIds.push(item.id);
                }
            });
        }

        if (selectedIds.length === 0) {
            $('#fieldDiffFixWarning').removeClass('alert-warning').addClass('alert-danger').show();
            $('#fieldDiffFixWarning').html('<strong>错误：</strong>请至少选择一项进行修复');
            $confirmBtn.html('<span class="fa fa-check"></span> 确认执行');
            $confirmBtn.prop('disabled', false);
            return;
        }

        doPoster("/tableGroup/executeFieldDiffFixSelective", {
            'id': id,
            'selectedIds': JSON.stringify(selectedIds)
        }, function(data) {
            if (data.success === true) {
                $('#fieldDiffFixModal').modal('hide');
                $('#fieldDifferenceModal').modal('hide');
                $('.modal-backdrop').remove();
                $('body').removeClass('modal-open').css({ 'padding-right': '', 'overflow': '' });
                bootGrowl(data.resultValue, "success");
                self._configChanged = true;
            } else {
                $('#fieldDiffFixWarning').removeClass('alert-warning').addClass('alert-danger');
                $('#fieldDiffFixWarning').html('<strong>执行失败：</strong>' + (data.resultValue || '未知错误'));
                $confirmBtn.html('<span class="fa fa-check"></span> 确认执行');
                $confirmBtn.prop('disabled', false);
            }
        });
    },

    _getDiffTypeLabel: function(diffType) {
        switch(diffType) {
            case 'ADDED': return 'info';
            case 'MISSING': return 'warning';
            case 'TYPE_MISMATCH': return 'danger';
            case 'LENGTH_MISMATCH': return 'primary';
            default: return 'default';
        }
    },

    _getOperationLabel: function(operation) {
        switch(operation) {
            case 'ADD': return 'success';
            case 'DROP': return 'danger';
            case 'MODIFY': return 'warning';
            default: return 'default';
        }
    }
};

// 绑定修复按钮点击事件（全局只绑定一次）
$(function() {
    $(document).on('click', '#fixTargetTableBtn', function() {
        FieldDifferenceComponent._onFixBtnClick();
    });

    $(document).on('click', '#confirmFieldDiffFixBtn', function() {
        FieldDifferenceComponent._executeFix();
    });

    $('#fieldDifferenceModal').on('hidden.bs.modal', function() {
        if (FieldDifferenceComponent._configChanged) {
            FieldDifferenceComponent._configChanged = false;
            $(document).trigger('fieldDiffConfigChanged');
        }
    });
});

/**
 * 移除 Mapping 多余字段（由按钮 onclick 直接调用）
 * @param {string} fieldName - 字段名
 * @param {HTMLElement} btnEl - 按钮元素
 */
FieldDifferenceComponent.removeMappingOnly = function(fieldName, btnEl) {
    if (!fieldName) return;
    if (!confirm('确认移除多余字段 [ ' + fieldName + ' ] 的映射配置？')) return;

    var $btn = $(btnEl);
    $btn.prop('disabled', true).html('<span class="fa fa-spinner fa-spin"></span>');

    var fixItemId = fieldName + '_MAPPING_ONLY';
    var self = this;
    doPoster('/tableGroup/executeFieldDiffFixSelective', {
        'id': FieldDifferenceComponent._cachedTableGroupId,
        'selectedIds': JSON.stringify([fixItemId])
    }, function(data) {
        if (data.success == true) {
            // 标记已变更，本地更新缓存，不关闭弹窗
            FieldDifferenceComponent._configChanged = true;

            var cachedData = FieldDifferenceComponent._cachedData;
            if (cachedData && cachedData.mappingOnlyFields) {
                cachedData.mappingOnlyFields = cachedData.mappingOnlyFields.filter(function(item) {
                    return item.fieldName !== fieldName;
                });
            }
            if (FieldDifferenceComponent.currentData && FieldDifferenceComponent.currentData.mappingOnlyFields) {
                FieldDifferenceComponent.currentData.mappingOnlyFields = FieldDifferenceComponent.currentData.mappingOnlyFields.filter(function(item) {
                    return item.fieldName !== fieldName;
                });
            }

            // 移除对应行，如果该区块已无字段则整个移除
            var $row = $btn.closest('tr');
            $row.fadeOut(300, function() {
                $row.remove();
                var $tbody = $row.closest('tbody');
                if ($tbody.find('tr').length === 0) {
                    var $panel = $row.closest('.panel');
                    $panel.fadeOut(300, function() { $panel.remove(); });
                }
            });
        } else {
            $btn.prop('disabled', false).html('<span class="fa fa-trash"></span> 移除');
            alert(data.resultValue || '移除失败');
        }
    });
};

/**
 * 移除全部 Mapping 多余字段（由按钮 onclick 直接调用）
 */
FieldDifferenceComponent.removeAllMappingOnly = function() {
    var cachedData = FieldDifferenceComponent._cachedData;
    if (!cachedData || !cachedData.mappingOnlyFields || cachedData.mappingOnlyFields.length === 0) return;

    var count = cachedData.mappingOnlyFields.length;
    if (!confirm('确认移除全部 ' + count + ' 个多余字段的映射配置？')) return;

    var $btn = $('#removeAllMappingOnlyBtn');
    $btn.prop('disabled', true).html('<span class="fa fa-spinner fa-spin"></span> 移除中...');

    var fixItemIds = cachedData.mappingOnlyFields.map(function(item) {
        return item.fieldName + '_MAPPING_ONLY';
    });
    doPoster('/tableGroup/executeFieldDiffFixSelective', {
        'id': FieldDifferenceComponent._cachedTableGroupId,
        'selectedIds': JSON.stringify(fixItemIds)
    }, function(data) {
        if (data.success == true) {
            // 标记已变更，关闭弹窗
            FieldDifferenceComponent._configChanged = true;
            $('#fieldDifferenceModal').modal('hide');
        } else {
            $btn.prop('disabled', false).html('<span class="fa fa-trash"></span> 一键移除全部');
            alert(data.resultValue || '移除失败');
        }
    });
};

//# sourceURL=/js/mapping/fieldDifferenceComponent.js
