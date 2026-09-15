/**
 * 统一任务列表模块（纯展示+交互，不含数据获取）
 * 供任务页、数据源页、异常任务页复用
 *
 * 用法：
 *   var list = new TaskList();
 *   list.init('#container', { enableCheckbox: true, ... });
 *   list.setData(tasks);   // 传入数据，渲染列表
 */
(function($) {
    'use strict';

    var _modelNames = {
        'full': '全量',
        'increment': '增量',
        'fullIncrement': '混合'
    };

    function TaskList() {
        this.$container = null;
        this.config = {};
        this.data = [];
        this._$tableBody = null;
    }

    // ==================== 公开 API ====================

    /**
     * 初始化列表 DOM 结构
     * @param containerSelector 容器选择器
     * @param config 配置项
     *   enableCheckbox: true       是否显示复选框列
     *   enableBatchStart: true     是否显示批量启动按钮
     *   enableBatchStop: false     是否显示批量停止按钮
     *   rowClickAction: null       行点击行为: 'detail' | null
     *   emptyMessage: '没有找到任务'  空数据提示
     */
    TaskList.prototype.init = function(containerSelector, config) {
        this.$container = $(containerSelector);
        if (!this.$container.length) return this;

        this.config = $.extend({
            enableCheckbox: true,
            enableBatchStart: true,
            enableBatchStop: false,
            rowClickAction: null,
            emptyMessage: '没有找到任务'
        }, config || {});

        this._build();
        return this;
    };

    /**
     * 设置数据并渲染列表（核心方法）
     * @param tasks 任务数组
     */
    TaskList.prototype.setData = function(tasks) {
        this.data = tasks || [];
        this._renderBody();
        this._updateBatchState();
        return this;
    };

    /**
     * 获取当前数据
     */
    TaskList.prototype.getData = function() {
        return this.data;
    };

    /**
     * 获取选中 ID 列表
     */
    TaskList.prototype.getSelectedIds = function() {
        if (!this.config.enableCheckbox) return [];
        var ids = [];
        this.$container.find('.tl-checkbox:checked').each(function() {
            ids.push($(this).val());
        });
        return ids;
    };

    /**
     * 全选/取消全选
     */
    TaskList.prototype.selectAll = function(checked) {
        this.$container.find('.tl-checkbox').prop('checked', !!checked);
        this.$container.find('.tl-select-all').prop('checked', !!checked);
        this._updateBatchState();
        return this;
    };

    /**
     * 销毁列表
     */
    TaskList.prototype.destroy = function() {
        this.$container.empty();
        this.data = [];
        return this;
    };

    // ==================== 渲染 ====================

    TaskList.prototype._renderBody = function() {
        if (!this._$tableBody) return;

        var tasks = this.data;
        if (!tasks || tasks.length === 0) {
            var colCount = this._getColumnCount();
            this._$tableBody.html(
                '<tr><td colspan="' + colCount + '" style="text-align:center;padding:30px;color:#999;">' +
                this._esc(this.config.emptyMessage) + '</td></tr>'
            );
            return;
        }

        var html = '';
        for (var i = 0; i < tasks.length; i++) {
            html += this._renderRow(tasks[i]);
        }
        this._$tableBody.html(html);
        this._bindRowEvents();
    };

    TaskList.prototype._renderRow = function(task) {
        var h = '';
        h += '<tr data-id="' + (task.id || '') + '"';
        if (this.config.rowClickAction) {
            h += ' style="cursor:pointer;"';
        }
        h += '>';

        if (this.config.enableCheckbox) {
            h += '<td><input type="checkbox" class="tl-checkbox" value="' + (task.id || '') + '" /></td>';
        }

        h += this._renderNameCell(task);
        h += this._renderStateCell(task);
        h += this._renderFailCell(task);
        h += this._renderConnectorCell(task, 'source');
        h += this._renderConnectorCell(task, 'target');
        h += this._renderModelCell(task);
        h += this._renderReadWriteCell(task);
        h += this._renderActionCell(task);
        h += this._renderTimeCell(task);

        h += '</tr>';
        return h;
    };

    // ---- 各列渲染 ----

    TaskList.prototype._renderNameCell = function(task) {
        var name = task.name || '-';
        return '<td style="max-width:150px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" title="' +
            this._esc(name) + '">' + this._esc(name) + '</td>';
    };

    TaskList.prototype._renderStateCell = function(task) {
        var meta = task.meta || {};
        var state = meta.state != null ? parseInt(meta.state) : 0;
        var h = '<td>';
        if (state === 0) {
            h += '<span class="label label-info">未运行</span>';
        } else if (state === 1) {
            h += '<span class="label label-success">运行中</span>';
        } else if (state === 2) {
            h += '<span class="label label-warning">停止中</span>';
        } else if (state === 3) {
            h += '<span class="label label-danger">异常</span>';
            if (meta.errorMessage) {
                h += ' <span title="' + this._esc(meta.errorMessage) +
                    '" class="mapping-error-sign" data-toggle="tooltip" data-placement="top">' +
                    '<i class="fa fa-exclamation-triangle"></i></span>';
            }
        }
        h += '</td>';
        return h;
    };

    TaskList.prototype._renderFailCell = function(task) {
        var fail = (task.meta && task.meta.fail) || 0;
        if (fail > 0) {
            return '<td><span style="color:#d9534f;font-weight:bold;">' + fail + '</span></td>';
        }
        return '<td>0</td>';
    };

    TaskList.prototype._renderConnectorCell = function(task, type) {
        var conn = task[type + 'Connector'];
        if (!conn) return '<td>-</td>';

        // connectorType 由后端 ConnectorBriefVo 平铺下发（不再嵌套整个 config，其中含数据库明文密码）
        var connType = conn.connectorType || 'default';
        var img = '/img/' + connType + '.png';
        var name = conn.name || '-';

        var h = '<td><div style="display:flex;align-items:center;">';
        h += '<img draggable="false" src="' + img + '" style="width:20px;height:20px;margin-right:5px;">';
        h += '<span title="' + this._esc(name) + '">' + this._esc(name) + '</span>';
        if (conn.running) {
            h += '<i class="fa fa-circle well-sign-green" title="连接正常" style="margin-left:5px;"></i>';
        } else {
            h += '<i class="fa fa-times-circle-o well-sign-red" title="连接异常" style="margin-left:5px;"></i>';
        }
        h += '</div></td>';
        return h;
    };

    TaskList.prototype._renderModelCell = function(task) {
        return '<td>' + (_modelNames[task.model] || '-') + '</td>';
    };

    TaskList.prototype._renderReadWriteCell = function(task) {
        var meta = task.meta || {};
        return '<td>' + (meta.total || 0) + ' / ' + (meta.success || 0) + '</td>';
    };

    TaskList.prototype._renderActionCell = function(task) {
        var meta = task.meta || {};
        var state = meta.state != null ? parseInt(meta.state) : 0;
        var id = task.id || '';

        var h = '<td><div class="operation-buttons">';
        // 启动按钮：未运行(0) 或 异常(3)
        if (state === 0 || state === 3) {
            h += '<a href="javascript:;" class="btn btn-xs btn-primary tl-btn-start" data-id="' + id +
                '" style="margin-right:3px;"><i class="fa fa-play"></i> 启动</a>';
        }
        // 停止按钮：运行中(1)
        if (state === 1) {
            h += '<a href="javascript:;" class="btn btn-xs btn-danger tl-btn-stop" data-id="' + id +
                '" style="margin-right:3px;"><i class="fa fa-stop"></i> 停止</a>';
        }
        // 详情按钮
        h += '<a href="javascript:;" class="btn btn-xs btn-success tl-btn-detail" data-id="' + id +
            '"><i class="fa fa-eye"></i> 详情</a>';
        h += '</div></td>';
        return h;
    };

    TaskList.prototype._renderTimeCell = function(task) {
        var t = task.createTime || task.updateTime;
        if (!t) return '<td>-</td>';
        var d = new Date(t);
        var pad = function(n) { return n < 10 ? '0' + n : '' + n; };
        return '<td>' + d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) +
            ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds()) + '</td>';
    };

    // ==================== 事件绑定 ====================

    TaskList.prototype._bindRowEvents = function() {
        var self = this;

        // 复选框
        if (self.config.enableCheckbox) {
            self.$container.find('.tl-checkbox').off('click').on('click', function(e) {
                e.stopPropagation();
                self._syncSelectAll();
                self._updateBatchState();
            });
        }

        // 行点击
        if (self.config.rowClickAction === 'detail') {
            self.$container.find('tr[data-id]').off('click').on('click', function() {
                var id = $(this).data('id');
                if (id) window.open('/#/mapping/page/edit?id=' + id, '_blank');
            });
        }

        // 启动按钮
        self.$container.find('.tl-btn-start').off('click').on('click', function(e) {
            e.stopPropagation();
            var id = $(this).data('id');
            if (self.config.onStart) {
                self.config.onStart(id, self);
            }
        });

        // 停止按钮
        self.$container.find('.tl-btn-stop').off('click').on('click', function(e) {
            e.stopPropagation();
            var id = $(this).data('id');
            if (self.config.onStop) {
                self.config.onStop(id, self);
            }
        });

        // 详情按钮
        self.$container.find('.tl-btn-detail').off('click').on('click', function(e) {
            e.stopPropagation();
            var id = $(this).data('id');
            if (self.config.onDetail) {
                self.config.onDetail(id, self);
            } else {
                if (id) window.open('/#/mapping/page/edit?id=' + id, '_blank');
            }
        });

        // 初始化 tooltip
        self.$container.find('[data-toggle="tooltip"]').tooltip();
    };

    // ==================== 选择 & 批量 ====================

    TaskList.prototype._syncSelectAll = function() {
        var $all = this.$container.find('.tl-checkbox');
        var $checked = this.$container.find('.tl-checkbox:checked');
        this.$container.find('.tl-select-all').prop('checked', $all.length > 0 && $all.length === $checked.length);
    };

    TaskList.prototype._updateBatchState = function() {
        if (!this.config.enableCheckbox) return;
        var count = this.$container.find('.tl-checkbox:checked').length;
        this.$container.find('.tl-batch-start, .tl-batch-stop').prop('disabled', count === 0);
        this.$container.find('.tl-selected-count').text(count > 0 ? '已选 ' + count + ' 个任务' : '');
    };

    TaskList.prototype._bindToolbarEvents = function() {
        var self = this;

        // 表头全选
        self.$container.find('.tl-select-all').off('click').on('click', function(e) {
            e.stopPropagation();
            var checked = $(this).prop('checked');
            self.$container.find('.tl-checkbox').prop('checked', checked);
            self._updateBatchState();
        });

        // 全选按钮
        self.$container.find('.tl-btn-select-all').off('click').on('click', function() {
            var $all = self.$container.find('.tl-checkbox');
            var $checked = self.$container.find('.tl-checkbox:checked');
            var newState = !($all.length > 0 && $all.length === $checked.length);
            $all.prop('checked', newState);
            self.$container.find('.tl-select-all').prop('checked', newState);
            self._updateBatchState();
        });

        // 批量启动
        self.$container.find('.tl-batch-start').off('click').on('click', function() {
            var ids = self.getSelectedIds();
            if (ids.length === 0) return;
            if (self.config.onBatchStart) {
                self.config.onBatchStart(ids, self);
            }
        });

        // 批量停止
        self.$container.find('.tl-batch-stop').off('click').on('click', function() {
            var ids = self.getSelectedIds();
            if (ids.length === 0) return;
            if (self.config.onBatchStop) {
                self.config.onBatchStop(ids, self);
            }
        });
    };

    // ==================== 构建 DOM ====================

    TaskList.prototype._build = function() {
        this.$container.empty();

        // 工具栏
        if (this.config.enableCheckbox) {
            var tb = '';
            tb += '<div class="tl-toolbar" style="margin-bottom:10px;display:flex;align-items:center;gap:12px;padding:8px 0;">';
            if (this.config.enableBatchStart) {
                tb += '<button class="btn btn-primary btn-sm tl-batch-start" disabled><span class="fa fa-play"></span> 批量启动</button>';
            }
            if (this.config.enableBatchStop) {
                tb += '<button class="btn btn-warning btn-sm tl-batch-stop" disabled><span class="fa fa-stop"></span> 批量停止</button>';
            }
            tb += '<span class="tl-selected-count" style="color:#888;font-size:12px;"></span>';
            tb += '<button class="btn btn-default btn-sm tl-btn-select-all" style="margin-left:auto;"><span class="fa fa-check-square-o"></span> 全选</button>';
            tb += '</div>';
            this.$container.append(tb);
        }

        // 表格
        var table = '';
        table += '<div class="panel panel-default"><div class="panel-body">';
        table += '<div class="table-responsive">';
        table += '<table class="table table-hover table-striped"><thead><tr>';

        if (this.config.enableCheckbox) {
            table += '<th style="width:36px;"><input type="checkbox" class="tl-select-all" title="全选/取消全选" /></th>';
        }
        table += '<th>名称</th>';
        table += '<th>状态</th>';
        table += '<th>失败数</th>';
        table += '<th>起始源</th>';
        table += '<th>目标源</th>';
        table += '<th>同步类型</th>';
        table += '<th>读取/写入</th>';
        table += '<th>操作</th>';
        table += '<th>创建时间</th>';
        table += '</tr></thead><tbody></tbody></table>';
        table += '</div></div></div>';

        this.$container.append(table);
        this._$tableBody = this.$container.find('tbody');
        this._bindToolbarEvents();
    };

    TaskList.prototype._getColumnCount = function() {
        // 名称 状态 失败数 起始源 目标源 同步类型 读取/写入 操作 创建时间 = 9
        return this.config.enableCheckbox ? 10 : 9;
    };

    // ==================== 工具 ====================

    TaskList.prototype._esc = function(str) {
        if (!str) return '';
        return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    };

    // ==================== 导出 ====================

    window.TaskList = TaskList;

})(jQuery);
