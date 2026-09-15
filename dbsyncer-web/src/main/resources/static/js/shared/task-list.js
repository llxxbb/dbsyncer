/**
 * 统一任务列表模块（纯展示+交互，不含数据获取）
 * 供任务页、数据源页、异常任务页复用
 *
 * HTML 结构由 <template> 标签定义（见 shared/task-list-templates.html），
 * 本模块只负责：克隆模板 → 填数据 → 切换显隐 → 绑定事件。
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
     *   onStart: null              启动单个任务回调 (id, list)
     *   onStop: null               停止单个任务回调 (id, list)
     *   onDetail: null             查看详情回调 (id, list)，默认打开新窗口
     *   onBatchStart: null         批量启动回调 (ids, list)
     *   onBatchStop: null          批量停止回调 (ids, list)
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
        // 渲染前记住选中状态，渲染后恢复（防止自动刷新丢失选择）
        var selectedIds = this.getSelectedIds();
        this._renderBody();
        this._restoreSelection(selectedIds);
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

    // ==================== 构建骨架 ====================

    /**
     * 从 <template> 克隆表格骨架和工具栏，组装到容器中。
     * HTML 结构在 task-list-templates.html 里维护，这里只做条件裁剪。
     */
    TaskList.prototype._build = function() {
        this.$container.empty();

        // —— 工具栏（仅复选框模式） ——
        if (this.config.enableCheckbox) {
            var toolbar = this._cloneTemplate('tl-tpl-toolbar');
            if (this.config.enableBatchStart) {
                $(toolbar).find('.tl-batch-start').css('display', '');
            } else {
                $(toolbar).find('.tl-batch-start').remove();
            }
            if (this.config.enableBatchStop) {
                $(toolbar).find('.tl-batch-stop').css('display', '');
            } else {
                $(toolbar).find('.tl-batch-stop').remove();
            }
            this.$container.append(toolbar);
        }

        // —— 表格 ——
        var table = this._cloneTemplate('tl-tpl-table');
        if (!this.config.enableCheckbox) {
            // 移除表头中的复选框列
            $(table).find('[data-col="checkbox"]').remove();
        }
        this.$container.append(table);
        this._$tableBody = this.$container.find('tbody');
        this._bindToolbarEvents();
    };

    // ==================== 渲染 ====================

    TaskList.prototype._renderBody = function() {
        if (!this._$tableBody) return;
        this._$tableBody.empty();

        var tasks = this.data;
        if (!tasks || tasks.length === 0) {
            var emptyRow = this._cloneTemplate('tl-tpl-empty');
            $(emptyRow).find('.tl-empty-msg')
                .text(this.config.emptyMessage)
                .attr('colspan', this._getColumnCount());
            this._$tableBody.append(emptyRow);
            return;
        }

        // 批量插入：先收集到 DocumentFragment，一次 DOM 操作
        var fragment = document.createDocumentFragment();
        for (var i = 0; i < tasks.length; i++) {
            fragment.appendChild(this._renderRow(tasks[i]));
        }
        this._$tableBody[0].appendChild(fragment);
        this._bindRowEvents();
    };

    /**
     * 克隆行模板并填入数据。
     * 所有 HTML 结构在 <template id="tl-tpl-row"> 中定义，
     * 这里只负责：设属性 → 填文本 → 切显隐。
     */
    TaskList.prototype._renderRow = function(task) {
        var row = this._cloneTemplate('tl-tpl-row');

        // 行级属性
        row.setAttribute('data-id', task.id || '');
        if (this.config.rowClickAction) {
            row.style.cursor = 'pointer';
        }

        // 复选框列
        if (!this.config.enableCheckbox) {
            var cbCell = row.querySelector('[data-col="checkbox"]');
            if (cbCell) cbCell.parentNode.removeChild(cbCell);
        } else {
            row.querySelector('.tl-checkbox').value = task.id || '';
        }

        // 各列填数据
        this._fillNameCell(row, task);
        this._fillStateCell(row, task);
        this._fillFailCell(row, task);
        this._fillConnectorCell(row, task, 'source');
        this._fillConnectorCell(row, task, 'target');
        this._fillModelCell(row, task);
        this._fillReadWriteCell(row, task);
        this._fillActionCell(row, task);
        this._fillTimeCell(row, task);

        return row;
    };

    // ---- 各列填充 ----

    TaskList.prototype._fillNameCell = function(row, task) {
        var cell = row.querySelector('[data-cell="name"]');
        var name = task.name || '-';
        cell.textContent = name;
        cell.title = name;
    };

    TaskList.prototype._fillStateCell = function(row, task) {
        var cell = row.querySelector('[data-cell="state"]');
        var meta = task.meta || {};
        var state = meta.state != null ? parseInt(meta.state) : 0;

        // 显示对应状态的 label（模板中全部 display:none，按 state 显示）
        var label = cell.querySelector('.tl-state-' + state);
        if (label) label.style.display = '';

        // 异常时显示错误提示
        if (state === 3 && meta.errorMessage) {
            var tip = cell.querySelector('.tl-error-tip');
            tip.title = meta.errorMessage;
            tip.style.display = '';
        }
    };

    TaskList.prototype._fillFailCell = function(row, task) {
        var cell = row.querySelector('[data-cell="fail"]');
        var fail = (task.meta && task.meta.fail) || 0;
        cell.textContent = fail;
        if (fail > 0) {
            cell.style.color = '#d9534f';
            cell.style.fontWeight = 'bold';
        }
    };

    TaskList.prototype._fillConnectorCell = function(row, task, type) {
        var cell = row.querySelector('[data-cell="' + type + '"]');
        var conn = task[type + 'Connector'];
        if (!conn) {
            cell.textContent = '-';
            return;
        }

        var connType = conn.connectorType || 'default';
        cell.querySelector('.tl-conn-img').src = '/img/' + connType + '.png';

        var nameEl = cell.querySelector('.tl-conn-name');
        nameEl.textContent = conn.name || '-';
        nameEl.title = conn.name || '-';

        // 连接状态图标
        if (conn.running) {
            cell.querySelector('.tl-conn-ok').style.display = '';
        } else {
            cell.querySelector('.tl-conn-fail').style.display = '';
        }
    };

    TaskList.prototype._fillModelCell = function(row, task) {
        row.querySelector('[data-cell="model"]').textContent = _modelNames[task.model] || '-';
    };

    TaskList.prototype._fillReadWriteCell = function(row, task) {
        var meta = task.meta || {};
        row.querySelector('[data-cell="rw"]').textContent = (meta.total || 0) + ' / ' + (meta.success || 0);
    };

    TaskList.prototype._fillActionCell = function(row, task) {
        var cell = row.querySelector('[data-cell="action"]');
        var meta = task.meta || {};
        var state = meta.state != null ? parseInt(meta.state) : 0;
        var id = task.id || '';

        // 启动按钮：未运行(0) 或 异常(3)
        if (state === 0 || state === 3) {
            var startBtn = cell.querySelector('.tl-btn-start');
            startBtn.style.display = '';
            startBtn.setAttribute('data-id', id);
        }
        // 停止按钮：运行中(1)
        if (state === 1) {
            var stopBtn = cell.querySelector('.tl-btn-stop');
            stopBtn.style.display = '';
            stopBtn.setAttribute('data-id', id);
        }
        // 详情按钮始终显示
        cell.querySelector('.tl-btn-detail').setAttribute('data-id', id);
    };

    TaskList.prototype._fillTimeCell = function(row, task) {
        var cell = row.querySelector('[data-cell="time"]');
        var t = task.createTime || task.updateTime;
        if (!t) {
            cell.textContent = '-';
            return;
        }
        var d = new Date(t);
        var pad = function(n) { return n < 10 ? '0' + n : '' + n; };
        cell.textContent = d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) +
            ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
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

    /**
     * 恢复选中状态（自动刷新后调用）
     * @param ids 之前选中的 ID 数组
     */
    TaskList.prototype._restoreSelection = function(ids) {
        if (!ids || ids.length === 0) return;
        var self = this;
        ids.forEach(function(id) {
            self.$container.find('.tl-checkbox[value="' + id + '"]').prop('checked', true);
        });
        this._syncSelectAll();
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

    // ==================== 工具 ====================

    /**
     * 克隆 <template> 的内容，返回克隆后的顶层元素。
     * @param templateId 模板元素的 id
     * @returns {Node} 克隆后的 DOM 节点（已脱离 template，可自由操作）
     */
    TaskList.prototype._cloneTemplate = function(templateId) {
        var tpl = document.getElementById(templateId);
        if (!tpl) {
            console.error('[TaskList] 模板未找到: #' + templateId + '，请确认页面已引入 task-list-templates.html');
            return document.createElement('div');
        }
        var clone = tpl.content.cloneNode(true);
        // template.content 是 DocumentFragment，取第一个元素节点返回
        return clone.firstElementChild || clone.firstChild;
    };

    TaskList.prototype._getColumnCount = function() {
        // 名称 状态 失败数 起始源 目标源 同步类型 读取/写入 操作 创建时间 = 9
        return this.config.enableCheckbox ? 10 : 9;
    };

    // ==================== 导出 ====================

    window.TaskList = TaskList;

})(jQuery);
