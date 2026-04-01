/**
 * 字段差异弹窗公共组件
 * 用于在多个页面中复用字段差异展示功能
 */

// 字段差异弹窗组件
var FieldDifferenceComponent = {
    // 当前显示的字段差异数据
    currentData: null,

    /**
     * 显示字段差异弹窗
     * @param {string} tableGroupId - 表映射关系ID
     * @param {Object} options - 配置选项
     * @param {boolean} options.showFixButton - 是否显示修复按钮，默认true
     * @param {Function} options.onFix - 修复按钮点击回调
     */
    show: function(tableGroupId, options) {
        options = options || {};
        var showFixButton = options.showFixButton !== false;

        // 显示弹窗
        $('#fieldDifferenceModal').modal('show');
        $('#fieldDifferenceContent').html('<div class="text-center"><span class="fa fa-spinner fa-spin fa-2x"></span> 加载中...</div>');

        // 控制修复按钮显示/隐藏
        if (showFixButton) {
            $('#fixTargetTableBtn').show();
        } else {
            $('#fixTargetTableBtn').hide();
        }

        // 保存回调
        this.onFixCallback = options.onFix;

        // 调用接口获取字段差异详情
        var self = this;
        doPoster("/tableGroup/fieldDifference", {'id': tableGroupId}, function(data) {
            if (data.success == true) {
                self.currentData = data.resultValue;
                self.render(data.resultValue);
            } else {
                $('#fieldDifferenceContent').html('<div class="alert alert-danger">' + data.resultValue + '</div>');
            }
        });
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

        if (result.addedFields && result.addedFields.length > 0) {
            html += this.buildSection(
                '目标表多出的字段',
                'fa-plus-circle',
                'info',
                result.addedFields.length,
                result.addedFields,
                'added'
            );
        }

        if (result.missingFields && result.missingFields.length > 0) {
            html += this.buildSection(
                '目标表缺少的字段',
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
        var html = '<div class="panel panel-' + alertClass + '">' +
            '<div class="panel-heading">' +
            '<span class="fa ' + icon + '"></span> ' + title +
            ' <span class="badge">' + count + '</span>' +
            '</div>' +
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
        }

        html += '<th>说明</th></tr></thead><tbody>';

        items.forEach(function(item) {
            html += '<tr>';
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

    /**
     * 清空当前数据
     */
    clear: function() {
        this.currentData = null;
        this.onFixCallback = null;
    }
};

// 绑定修复按钮点击事件（全局只绑定一次）
$(function() {
    $(document).on('click', '#fixTargetTableBtn', function() {
        if (FieldDifferenceComponent.onFixCallback) {
            FieldDifferenceComponent.onFixCallback(FieldDifferenceComponent.currentData);
        }
    });
});
