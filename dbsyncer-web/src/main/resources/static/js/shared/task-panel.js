/**
 * 任务面板模块（大容器）
 * 编排 TaskToolbar + TaskList + TaskCardList，处理子组件联动
 *
 * 职责：
 *   - 子组件生命周期管理（创建/销毁）
 *   - 视图切换（列表/卡片）+ 自动持久化用户偏好
 *   - 搜索/过滤参数收集，回调外部
 *   - 数据分发（setData 同时传给 list 和 cardList）
 *
 * 不含：
 *   - 数据获取（外部通过 setData 传入）
 *   - 业务逻辑（创建/启动/停止等通过回调由外部处理）
 *   - 视图模式管理（内部自动记住，外部无需关心）
 *
 * 用法：
 *   var panel = new TaskPanel();
 *   panel.init('#panel', {
 *       enableSearch: true,
 *       enableFilter: true,
 *       enableCreate: true,
 *       enableCardView: true,
 *       enableCheckbox: false,
 *       onCreate: function() { ... },
 *       onSearch: function(params) { ... },
 *       onStart: function(id) { ... },
 *       onStop: function(id) { ... }
 *   });
 *   panel.setData(tasks);
 */
(function($) {
    'use strict';

    // 模块级变量：跨实例共享视图模式偏好
    var _savedViewMode = null;
    var VIEW_MODE_KEY = 'dbsyncerViewMode';

    /**
     * 读取持久化的视图模式（模块变量 > localStorage > 默认 'list'）
     */
    function _loadViewMode() {
        if (_savedViewMode) return _savedViewMode;
        try {
            var stored = localStorage.getItem(VIEW_MODE_KEY);
            if (stored === 'list' || stored === 'card') {
                _savedViewMode = stored;
                return stored;
            }
        } catch (e) { /* localStorage 不可用 */ }
        return 'list';
    }

    /**
     * 持久化视图模式（模块变量 + localStorage）
     */
    function _saveViewMode(view) {
        _savedViewMode = view;
        try {
            localStorage.setItem(VIEW_MODE_KEY, view);
        } catch (e) { /* localStorage 不可用 */ }
    }

    function TaskPanel() {
        this.$container = null;
        this.config = {};
        this.toolbar = null;
        this.list = null;
        this.cardList = null;
        this._currentView = 'list';
        this._$listView = null;
        this._$cardView = null;
        this._$noResults = null;
    }

    // ==================== 公开 API ====================

    /**
     * 初始化面板
     * @param containerSelector 容器选择器
     * @param config 配置项
     *
     *   功能开关：
     *   enableSearch: true        搜索框
     *   enableFilter: true        过滤下拉
     *   enableCreate: true        创建按钮
     *   enableCardView: true      卡片视图
     *   enableCheckbox: false     复选框列
     *   enableBatchStart: false   批量启动
     *   enableBatchStop: false    批量停止
     *
     *   回调：
     *   onCreate: null            创建任务
     *   onSearch: null            搜索/过滤 { keyword, state, sourceConnectorType, targetConnectorType }
     *   onStart: null             启动单个任务
     *   onStop: null              停止单个任务
     *   onBatchStart: null        批量启动
     *   onBatchStop: null         批量停止
     *   onDetail: null            查看详情（默认打开新窗口）
     *
     *   其他：
     *   emptyMessage: '没有找到任务'
     *
     *   注：视图模式（列表/卡片）由组件内部自动持久化，外部无需传入。
     */
    TaskPanel.prototype.init = function(containerSelector, config) {
        this.$container = $(containerSelector);
        if (!this.$container.length) return this;

        this.config = $.extend({
            enableSearch: true,
            enableFilter: true,
            enableCreate: true,
            enableCardView: true,
            enableCheckbox: false,
            enableBatchStart: false,
            enableBatchStop: false,
            onCreate: null,
            onSearch: null,
            onStart: null,
            onStop: null,
            onBatchStart: null,
            onBatchStop: null,
            onDetail: null,
            emptyMessage: '没有找到任务'
        }, config || {});

        this._build();
        return this;
    };

    /**
     * 设置数据并渲染列表（核心方法）
     * @param tasks 任务数组
     */
    TaskPanel.prototype.setData = function(tasks) {
        var data = tasks || [];
        if (this.list) this.list.setData(data);
        if (this.cardList) this.cardList.setData(data);
        if (this.toolbar) this.toolbar.setTaskCount(data.length);

        // 搜索无结果提示
        if (this._$noResults) {
            if (data.length === 0 && this.toolbar) {
                var params = this.toolbar.getSearchParams();
                var hasFilter = params.keyword || params.state || params.sourceConnectorType || params.targetConnectorType;
                this._$noResults.toggle(!!hasFilter);
            } else {
                this._$noResults.hide();
            }
        }
        return this;
    };

    /**
     * 获取当前数据（从 list 取）
     */
    TaskPanel.prototype.getData = function() {
        return this.list ? this.list.getData() : [];
    };

    /**
     * 设置过滤选项（数据源列表）
     * @param options { source: [...], target: [...] }
     */
    TaskPanel.prototype.setFilterOptions = function(options) {
        if (this.toolbar) this.toolbar.setFilterOptions(options);
        return this;
    };

    /**
     * 获取选中 ID 列表（仅列表视图）
     */
    TaskPanel.prototype.getSelectedIds = function() {
        return this.list ? this.list.getSelectedIds() : [];
    };

    /**
     * 销毁面板
     */
    TaskPanel.prototype.destroy = function() {
        if (this.toolbar) this.toolbar.destroy();
        if (this.list) this.list.destroy();
        if (this.cardList) this.cardList.destroy();
        this.$container.empty();
        return this;
    };

    // ==================== 构建 ====================

    TaskPanel.prototype._build = function() {
        this.$container.empty();

        // 读取持久化的视图模式
        var initialView = this.config.enableCardView ? _loadViewMode() : 'list';
        this._currentView = initialView;

        // —— 工具栏 ——
        var $toolbarContainer = $('<div class="tp-toolbar-container"></div>');
        this.$container.append($toolbarContainer);

        this.toolbar = new TaskToolbar();
        this.toolbar.init($toolbarContainer[0], {
            enableCreate: this.config.enableCreate,
            enableSearch: this.config.enableSearch,
            enableFilter: this.config.enableFilter,
            enableCardView: this.config.enableCardView,
            initialView: initialView,
            onCreate: this.config.onCreate,
            onSearch: this.config.onSearch,
            onViewChange: function(view) { this._onViewChange(view); }.bind(this)
        });

        // —— 无搜索结果提示 ——
        var noResults = this._cloneTemplate('tp-tpl-no-results');
        this.$container.append(noResults);
        this._$noResults = this.$container.find('.tp-no-results');

        // —— 视图容器 ——
        var $viewsWrapper = $('<div class="tp-views-wrapper"></div>');
        this.$container.append($viewsWrapper);

        // 列表视图
        this._$listView = $('<div class="tp-list-view"></div>');
        $viewsWrapper.append(this._$listView);
        if (initialView !== 'list') this._$listView.hide();

        this.list = new TaskList();
        this.list.init(this._$listView[0], {
            enableCheckbox: this.config.enableCheckbox,
            enableBatchStart: this.config.enableBatchStart,
            enableBatchStop: this.config.enableBatchStop,
            emptyMessage: this.config.emptyMessage,
            onStart: this.config.onStart,
            onStop: this.config.onStop,
            onDetail: this.config.onDetail,
            onBatchStart: this.config.onBatchStart,
            onBatchStop: this.config.onBatchStop
        });

        // 卡片视图
        if (this.config.enableCardView) {
            this._$cardView = $('<div class="tp-card-view" style="padding-left:0;padding-right:0;"></div>');
            $viewsWrapper.append(this._$cardView);
            if (initialView !== 'card') this._$cardView.hide();

            this.cardList = new TaskCardList();
            this.cardList.init(this._$cardView[0], {
                emptyMessage: this.config.emptyMessage,
                onStart: this.config.onStart,
                onStop: this.config.onStop,
                onDetail: this.config.onDetail
            });
        }
    };

    // ==================== 视图切换 ====================

    /**
     * toolbar 通知视图变化 → 切换视图容器 + 持久化
     */
    TaskPanel.prototype._onViewChange = function(view) {
        if (view === this._currentView) return;
        this._currentView = view;

        if (view === 'list') {
            this._$listView.show();
            if (this._$cardView) this._$cardView.hide();
        } else {
            this._$listView.hide();
            if (this._$cardView) this._$cardView.show();
        }

        _saveViewMode(view);
    };

    // ==================== 工具 ====================

    TaskPanel.prototype._cloneTemplate = function(templateId) {
        var tpl = document.getElementById(templateId);
        if (!tpl) {
            console.error('[TaskPanel] 模板未找到: #' + templateId);
            return document.createElement('div');
        }
        var clone = tpl.content.cloneNode(true);
        return clone.firstElementChild || clone.firstChild;
    };

    // ==================== 导出 ====================

    window.TaskPanel = TaskPanel;

})(jQuery);
