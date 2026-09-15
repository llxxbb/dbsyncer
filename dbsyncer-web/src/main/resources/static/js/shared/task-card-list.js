/**
 * 任务卡片列表模块
 * 提供卡片视图渲染和交互
 *
 * HTML 结构由 <template> 标签定义（见 shared/task-card-templates.html），
 * 本模块只负责：克隆模板 → 填数据 → 绑定事件。
 *
 * 用法：
 *   var cardList = new TaskCardList();
 *   cardList.init('#container', {
 *       onStart: function(id) { ... },
 *       onStop: function(id) { ... },
 *       onDetail: function(id) { ... }
 *   });
 *   cardList.setData(tasks);
 */
(function($) {
    'use strict';

    var _modelNames = {
        'full': '全量',
        'increment': '增量',
        'fullIncrement': '混合'
    };

    function TaskCardList() {
        this.$container = null;
        this.config = {};
        this.data = [];
        this._$listContainer = null;
    }

    // ==================== 公开 API ====================

    /**
     * 初始化卡片列表
     * @param containerSelector 容器选择器
     * @param config 配置项
     *   onStart: null       启动按钮回调
     *   onStop: null        停止按钮回调
     *   onDetail: null      详情回调（默认打开详情页）
     *   emptyMessage: '暂无任务'  空数据提示
     */
    TaskCardList.prototype.init = function(containerSelector, config) {
        this.$container = $(containerSelector);
        if (!this.$container.length) return this;

        this.config = $.extend({
            onStart: null,
            onStop: null,
            onDetail: null,
            emptyMessage: '暂无任务'
        }, config || {});

        this._build();
        return this;
    };

    /**
     * 设置数据并渲染卡片列表
     * @param tasks 任务数组
     */
    TaskCardList.prototype.setData = function(tasks) {
        this.data = tasks || [];
        this._render();
        return this;
    };

    /**
     * 获取当前数据
     */
    TaskCardList.prototype.getData = function() {
        return this.data;
    };

    /**
     * 销毁卡片列表
     */
    TaskCardList.prototype.destroy = function() {
        this.$container.empty();
        this.data = [];
        return this;
    };

    // ==================== 构建 ====================

    TaskCardList.prototype._build = function() {
        this.$container.empty();
        var container = this._cloneTemplate('tc-tpl-container');
        this.$container.append(container);
        this._$listContainer = this.$container.find('.tc-card-list');
    };

    // ==================== 渲染 ====================

    TaskCardList.prototype._render = function() {
        if (!this._$listContainer) return;
        this._$listContainer.empty();

        var tasks = this.data;
        if (!tasks || tasks.length === 0) {
            var empty = this._cloneTemplate('tc-tpl-empty');
            $(empty).find('.tc-empty').text(this.config.emptyMessage);
            this._$listContainer.append(empty);
            return;
        }

        var fragment = document.createDocumentFragment();
        for (var i = 0; i < tasks.length; i++) {
            fragment.appendChild(this._renderCard(tasks[i]));
        }
        this._$listContainer[0].appendChild(fragment);
        this._bindCardEvents();
    };

    TaskCardList.prototype._renderCard = function(task) {
        var card = this._cloneTemplate('tc-tpl-card');
        var $card = $(card);

        // 卡片 ID
        $card.attr('data-id', task.id || '');

        // 任务名称
        var name = task.name || '-';
        $card.find('.tc-card-name').text(name).attr('title', name);

        // 连接信息
        this._fillConnector($card, '.tc-conn-source', task.sourceConnector);
        this._fillConnector($card, '.tc-conn-target', task.targetConnector);

        // 同步类型
        $card.find('.tc-model-name').text(_modelNames[task.model] || '-');

        // 统计数据
        var meta = task.meta || {};
        $card.find('.tc-total').text(meta.total || 0);
        $card.find('.tc-success').text(meta.success || 0);

        // 状态
        var state = meta.state != null ? parseInt(meta.state) : 0;
        var stateLabel = $card.find('.tc-state-' + state);
        if (stateLabel.length) stateLabel.css('display', '');

        // 异常提示
        if (state === 3 && meta.errorMessage) {
            var tip = $card.find('.tc-error-tip');
            tip.attr('title', meta.errorMessage).css('display', '');
        }

        // 失败数
        var fail = meta.fail || 0;
        if (fail > 0) {
            $card.find('.tc-fail-group').css('display', 'flex');
            $card.find('.tc-fail-count').text(fail);
        }

        // 操作按钮
        if (state === 0 || state === 3) {
            $card.find('.tc-btn-start').css('display', '').attr('data-id', task.id || '');
        }
        if (state === 1) {
            $card.find('.tc-btn-stop').css('display', '').attr('data-id', task.id || '');
        }

        return card;
    };

    TaskCardList.prototype._fillConnector = function($card, selector, conn) {
        var $cell = $card.find(selector);
        if (!conn) {
            $cell.find('.tc-conn-name').text('-');
            return;
        }

        var connType = conn.connectorType || 'default';
        $cell.find('.tc-conn-img').attr('src', '/img/' + connType + '.png');
        $cell.find('.tc-conn-name').text(conn.name || '-').attr('title', conn.name || '-');

        if (conn.running) {
            $cell.find('.tc-conn-ok').css('display', '');
        } else {
            $cell.find('.tc-conn-fail').css('display', '');
        }
    };

    // ==================== 事件绑定 ====================

    TaskCardList.prototype._bindCardEvents = function() {
        var self = this;

        // 卡片点击（打开详情）
        self._$listContainer.find('.mapping-card').off('click').on('click', function(e) {
            // 排除按钮点击
            if ($(e.target).closest('.tc-btn-start, .tc-btn-stop').length) return;

            var id = $(this).data('id');
            if (self.config.onDetail) {
                self.config.onDetail(id);
            } else {
                if (id) window.open('/#/mapping/page/edit?classOn=0&id=' + id, '_blank');
            }
        });

        // 启动按钮
        self._$listContainer.find('.tc-btn-start').off('click').on('click', function(e) {
            e.stopPropagation();
            var id = $(this).data('id');
            if (self.config.onStart) {
                self.config.onStart(id);
            }
        });

        // 停止按钮
        self._$listContainer.find('.tc-btn-stop').off('click').on('click', function(e) {
            e.stopPropagation();
            var id = $(this).data('id');
            if (self.config.onStop) {
                self.config.onStop(id);
            }
        });

        // 初始化 tooltip
        self._$listContainer.find('[data-toggle="tooltip"]').tooltip();
    };

    // ==================== 工具 ====================

    TaskCardList.prototype._cloneTemplate = function(templateId) {
        var tpl = document.getElementById(templateId);
        if (!tpl) {
            console.error('[TaskCardList] 模板未找到: #' + templateId);
            return document.createElement('div');
        }
        var clone = tpl.content.cloneNode(true);
        return clone.firstElementChild || clone.firstChild;
    };

    // ==================== 导出 ====================

    window.TaskCardList = TaskCardList;

})(jQuery);
