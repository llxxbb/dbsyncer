/**
 * 任务面板模块（大容器）
 * 编排 TaskToolbar + TaskList + TaskCardList，处理子组件联动
 *
 * 职责：
 *   - 子组件生命周期管理（创建/销毁）
 *   - 视图切换（列表/卡片）+ 自动持久化用户偏好
 *   - 过滤选项从任务数据中自动提取（数据源名称+类型）
 *   - 搜索/过滤在前端执行（基于 setData 的完整数据）
 *   - 数据分发（setData 同时传给 list 和 cardList）
 *   - 启动/停止/批量操作内置处理（确认弹窗 → 请求 → 刷新）
 *
 * 不含：
 *   - 数据获取（外部通过 setData 传入完整数据）
 *   - 创建任务（页面跳转逻辑各异，通过 onCreate 回调）
 *   - 视图模式管理（内部自动记住，外部无需关心）
 *
 * 用法：
 *   var panel = new TaskPanel();
 *   panel.init('#panel', {
 *       onCreate: function() { ... },   // 唯一需要的外部回调
 *       onRefresh: function() { ... }   // 操作后刷新数据
 *   });
 *   panel.setData(tasks);  // 传入完整数据
 */
(function($) {
    'use strict';

    // ==================== 视图模式持久化 ====================

    var _savedViewMode = null;
    var VIEW_MODE_KEY = 'dbsyncerViewMode';

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

    function _saveViewMode(view) {
        _savedViewMode = view;
        try {
            localStorage.setItem(VIEW_MODE_KEY, view);
        } catch (e) { /* localStorage 不可用 */ }
    }

    // ==================== TaskPanel ====================

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
        this._fullData = [];  // 完整数据（搜索前）
    }

    // ==================== 公开 API ====================

    /**
     * 初始化面板
     * @param containerSelector 容器选择器
     * @param config 配置项
     *
     *   功能开关：
     *   enableCreate: true          创建按钮
     *   enableCheckbox: false       复选框列
     *   enableBatchStart: false     批量启动
     *   enableBatchStop: false      批量停止
     *
     *   回调：
     *   onCreate: null              创建任务（页面跳转）
     *   onRefresh: null             操作后刷新数据（必须，否则操作后列表不更新）
     *
     *   其他：
     *   emptyMessage: '没有找到任务'
     *
     *   注：
     *   - 搜索/过滤/卡片视图/过滤选项/视图模式 全部由组件内部管理。
     *   - 启动/停止/批量启动/批量停止 由组件内置处理，无需外部回调。
     *   - 外部只需 setData 传入完整数据，+ onRefresh 告诉组件怎么刷新。
     */
    TaskPanel.prototype.init = function(containerSelector, config) {
        this.$container = $(containerSelector);
        if (!this.$container.length) return this;

        this.config = $.extend({
            enableCreate: true,
            enableCheckbox: false,
            enableBatchStart: false,
            enableBatchStop: false,
            onCreate: null,
            onRefresh: null,
            emptyMessage: '没有找到任务'
        }, config || {});

        this._build();
        return this;
    };

    /**
     * 设置数据并渲染列表（核心方法）
     * 同时保存完整数据副本，供搜索/过滤使用
     * 自动从任务数据中提取过滤选项（数据源名称+类型）
     * @param tasks 任务数组（完整数据）
     */
    TaskPanel.prototype.setData = function(tasks) {
        this._fullData = tasks || [];
        this._extractFilterOptions();
        this._applyFilter();
        return this;
    };

    /**
     * 获取当前数据（从 list 取）
     */
    TaskPanel.prototype.getData = function() {
        return this.list ? this.list.getData() : [];
    };

    /**
     * 手动设置过滤选项（一般不需要，setData 会自动从任务数据中提取）
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
        var initialView = this.config.enableCardView !== false ? _loadViewMode() : 'list';
        this._currentView = initialView;

        // —— 工具栏 ——
        var $toolbarContainer = $('<div class="tp-toolbar-container"></div>');
        this.$container.append($toolbarContainer);

        this.toolbar = new TaskToolbar();
        this.toolbar.init($toolbarContainer[0], {
            enableCreate: this.config.enableCreate,
            initialView: initialView,
            onCreate: this.config.onCreate,
            onSearch: function(params) { this._applyFilter(params); }.bind(this),
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
            onStart: function(id) { this._doSingleAction(id, '/mapping/start?id=', '启动'); }.bind(this),
            onStop: function(id) { this._doSingleAction(id, '/mapping/stop?id=', '停止'); }.bind(this),
            onBatchStart: function(ids) { this._doBatchAction(ids, '/mapping/batchStart', '启动'); }.bind(this),
            onBatchStop: function(ids) { this._doBatchAction(ids, '/mapping/batchStop', '停止'); }.bind(this)
        });

        // 卡片视图
        if (this.config.enableCardView !== false) {
            this._$cardView = $('<div class="tp-card-view" style="padding-left:0;padding-right:0;"></div>');
            $viewsWrapper.append(this._$cardView);
            if (initialView !== 'card') this._$cardView.hide();

            this.cardList = new TaskCardList();
            this.cardList.init(this._$cardView[0], {
                emptyMessage: this.config.emptyMessage,
                onStart: function(id) { this._doSingleAction(id, '/mapping/start?id=', '启动'); }.bind(this),
                onStop: function(id) { this._doSingleAction(id, '/mapping/stop?id=', '停止'); }.bind(this)
            });
        }

        // 过滤选项在 setData 时自动从任务数据中提取
    };

    // ==================== 启动/停止操作（内置） ====================

    /**
     * 单个任务操作（启动/停止）
     * 弹窗确认 → POST → 刷新数据
     */
    TaskPanel.prototype._doSingleAction = function(id, urlPrefix, actionName) {
        var self = this;
        BootstrapDialog.show({
            title: '确认',
            type: BootstrapDialog.TYPE_PRIMARY,
            message: '确认' + actionName + '该任务？',
            size: BootstrapDialog.SIZE_NORMAL,
            buttons: [{
                label: '确定',
                action: function(dialog) {
                    $.loadingT(true);
                    $.post(urlPrefix + id, function(data) {
                        $.loadingT(false);
                        if (data && data.success) {
                            bootGrowl(data.resultValue || actionName + '成功', 'success');
                            self._refresh();
                        } else {
                            bootGrowl((data && data.resultValue) || actionName + '失败', 'danger');
                        }
                    }).error(function() {
                        $.loadingT(false);
                        bootGrowl(actionName + '失败', 'danger');
                    });
                    dialog.close();
                }
            }, {
                label: '取消',
                action: function(dialog) { dialog.close(); }
            }]
        });
    };

    /**
     * 批量操作（批量启动/批量停止）
     * 弹窗确认 → POST → 刷新数据
     */
    TaskPanel.prototype._doBatchAction = function(ids, url, actionName) {
        var self = this;
        BootstrapDialog.show({
            title: '确认',
            type: BootstrapDialog.TYPE_PRIMARY,
            message: '确认批量' + actionName + '选中的 ' + ids.length + ' 个任务？',
            size: BootstrapDialog.SIZE_NORMAL,
            buttons: [{
                label: '确定',
                action: function(dialog) {
                    $.loadingT(true);
                    $.post(url, { ids: ids.join(',') }, function(data) {
                        $.loadingT(false);
                        if (data && data.success) {
                            bootGrowl(data.resultValue || '批量' + actionName + '完成', 'success');
                            self._refresh();
                        } else {
                            bootGrowl((data && data.resultValue) || '批量' + actionName + '失败', 'danger');
                        }
                    }).error(function() {
                        $.loadingT(false);
                        bootGrowl('批量' + actionName + '失败', 'danger');
                    });
                    dialog.close();
                }
            }, {
                label: '取消',
                action: function(dialog) { dialog.close(); }
            }]
        });
    };

    /**
     * 刷新数据（调用外部 onRefresh 回调）
     */
    TaskPanel.prototype._refresh = function() {
        if (typeof this.config.onRefresh === 'function') {
            this.config.onRefresh();
        }
    };

    // ==================== 搜索/过滤（内部，纯前端） ====================

    /**
     * 从任务数据中提取过滤选项（数据源名称+类型，去重）
     */
    TaskPanel.prototype._extractFilterOptions = function() {
        if (!this.toolbar) return;

        var sourceMap = {};
        var targetMap = {};

        for (var i = 0; i < this._fullData.length; i++) {
            var task = this._fullData[i];
            var source = task.sourceConnector || {};
            var target = task.targetConnector || {};

            if (source.connectorType && source.name) {
                sourceMap[source.connectorType] = source.name;
            }
            if (target.connectorType && target.name) {
                targetMap[target.connectorType] = target.name;
            }
        }

        var sourceOptions = [];
        var targetOptions = [];

        for (var type in sourceMap) {
            sourceOptions.push({ connectorType: type, name: sourceMap[type] });
        }
        for (var type in targetMap) {
            targetOptions.push({ connectorType: type, name: targetMap[type] });
        }

        this.setFilterOptions({
            source: sourceOptions,
            target: targetOptions
        });
    };

    /**
     * 应用过滤（纯前端）
     * @param params 过滤参数，无参则显示全部数据
     */
    TaskPanel.prototype._applyFilter = function(params) {
        var data = this._fullData;

        // 无参数 → 显示全部
        if (!params) {
            this._renderData(data);
            return;
        }

        var keyword = (params.keyword || '').toLowerCase();
        var state = params.state;
        var sourceType = params.sourceConnectorType;
        var targetType = params.targetConnectorType;

        // 无任何过滤条件 → 显示全部
        if (!keyword && !state && !sourceType && !targetType) {
            this._renderData(data);
            return;
        }

        // 前端过滤
        var filtered = [];
        for (var i = 0; i < data.length; i++) {
            var task = data[i];
            var meta = task.meta || {};
            var source = task.sourceConnector || {};
            var target = task.targetConnector || {};

            // 关键词匹配（任务名称）
            if (keyword && (task.name || '').toLowerCase().indexOf(keyword) === -1) {
                continue;
            }

            // 状态匹配
            if (state && String(meta.state) !== String(state)) {
                continue;
            }

            // 源类型匹配
            if (sourceType && source.connectorType !== sourceType) {
                continue;
            }

            // 目标类型匹配
            if (targetType && target.connectorType !== targetType) {
                continue;
            }

            filtered.push(task);
        }

        this._renderData(filtered);
    };

    /**
     * 渲染数据到列表/卡片
     */
    TaskPanel.prototype._renderData = function(data) {
        if (this.list) this.list.setData(data);
        if (this.cardList) this.cardList.setData(data);
        if (this.toolbar) this.toolbar.setTaskCount(this._fullData.length);

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
    };

    // ==================== 视图切换 ====================

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
