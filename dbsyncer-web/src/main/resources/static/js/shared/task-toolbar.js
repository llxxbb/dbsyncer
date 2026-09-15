/**
 * 任务工具栏模块
 * 提供搜索、过滤、创建按钮、视图切换功能
 *
 * HTML 结构由 <template> 标签定义（见 shared/task-panel-templates.html），
 * 本模块只负责：克隆模板 → 填数据 → 绑定事件 → 触发回调。
 *
 * 用法：
 *   var toolbar = new TaskToolbar();
 *   toolbar.init('#container', {
 *       enableCreate: true,
 *       enableSearch: true,
 *       enableFilter: true,
 *       enableCardView: true,
 *       initialView: 'list',          // 初始视图模式
 *       onCreate: function() { ... },
 *       onSearch: function(params) { ... },
 *       onViewChange: function(view) { ... }
 *   });
 */
(function($) {
    'use strict';

    function TaskToolbar() {
        this.$container = null;
        this.config = {};
        this._$toolbar = null;
        this._currentView = 'list'; // 'list' | 'card'
    }

    // ==================== 公开 API ====================

    /**
     * 初始化工具栏
     * @param containerSelector 容器选择器
     * @param config 配置项
     *   enableCreate: true        是否显示创建按钮
     *   enableSearch: true        是否显示搜索框
     *   enableFilter: true        是否显示过滤下拉
     *   enableCardView: true      是否显示视图切换按钮
     *   initialView: 'list'       初始视图模式 'list' | 'card'
     *   onCreate: null            创建按钮回调
     *   onSearch: null            搜索/过滤回调，参数 { keyword, state, sourceConnectorType, targetConnectorType }
     *   onViewChange: null        视图切换回调，参数 'list' | 'card'
     */
    TaskToolbar.prototype.init = function(containerSelector, config) {
        this.$container = $(containerSelector);
        if (!this.$container.length) return this;

        this.config = $.extend({
            enableCreate: true,
            enableSearch: true,
            enableFilter: true,
            enableCardView: true,
            initialView: 'list',
            onCreate: null,
            onSearch: null,
            onViewChange: null
        }, config || {});

        this._currentView = this.config.initialView;

        this._build();
        return this;
    };

    /**
     * 获取当前视图模式
     * @returns {'list'|'card'}
     */
    TaskToolbar.prototype.getView = function() {
        return this._currentView;
    };

    /**
     * 设置视图模式（同步按钮状态，不触发 onViewChange 回调）
     * @param view 'list' | 'card'
     */
    TaskToolbar.prototype.setView = function(view) {
        if (view === this._currentView) return;
        this._currentView = view;
        this._syncViewButtons();
    };

    /**
     * 设置过滤选项（数据源列表）
     * @param options { source: [...], target: [...] } connector 数组
     */
    TaskToolbar.prototype.setFilterOptions = function(options) {
        if (!this.config.enableFilter) return;

        var $sourceSelect = this.$container.find('.tp-filter-source');
        var $targetSelect = this.$container.find('.tp-filter-target');

        // 保留第一个"全部"选项
        $sourceSelect.find('option:not(:first)').remove();
        $targetSelect.find('option:not(:first)').remove();

        var connectorTypes = {};
        var connectors = options.source || options.target || [];

        if (Array.isArray(connectors)) {
            connectors.forEach(function(connector) {
                // /datasource/list 返回 Connector 对象，connectorType 嵌套在 config 下
                var type = (connector.config && connector.config.connectorType) || connector.connectorType || 'default';
                if (!connectorTypes[type]) connectorTypes[type] = true;
            });
        }

        for (var type in connectorTypes) {
            if (connectorTypes.hasOwnProperty(type)) {
                var option = '<option value="' + type + '">' + type + '</option>';
                $sourceSelect.append(option);
                $targetSelect.append(option);
            }
        }
    };

    /**
     * 设置任务数量（显示在创建按钮上）
     * @param count 数量
     */
    TaskToolbar.prototype.setTaskCount = function(count) {
        this.$container.find('.tp-task-count').text(count || 0);
    };

    /**
     * 获取当前搜索/过滤参数
     */
    TaskToolbar.prototype.getSearchParams = function() {
        return {
            keyword: this.$container.find('.tp-search-input').val().trim(),
            state: this.$container.find('.tp-filter-state').val(),
            sourceConnectorType: this.$container.find('.tp-filter-source').val(),
            targetConnectorType: this.$container.find('.tp-filter-target').val()
        };
    };

    /**
     * 恢复搜索/过滤参数（setFilterOptions 重建 option 后选中值会丢失，需恢复）
     * @param params getSearchParams() 返回的参数对象
     */
    TaskToolbar.prototype.restoreSearchParams = function(params) {
        if (!params) return;
        if (params.keyword) {
            this.$container.find('.tp-search-input').val(params.keyword);
        }
        if (params.state) {
            this.$container.find('.tp-filter-state').val(params.state);
        }
        if (params.sourceConnectorType) {
            this.$container.find('.tp-filter-source').val(params.sourceConnectorType);
        }
        if (params.targetConnectorType) {
            this.$container.find('.tp-filter-target').val(params.targetConnectorType);
        }
    };

    /**
     * 清空搜索/过滤
     */
    TaskToolbar.prototype.clearSearch = function() {
        this.$container.find('.tp-search-input').val('');
        this.$container.find('.tp-filter-state').val('');
        this.$container.find('.tp-filter-source').val('');
        this.$container.find('.tp-filter-target').val('');
    };

    /**
     * 销毁工具栏
     */
    TaskToolbar.prototype.destroy = function() {
        this.$container.empty();
        return this;
    };

    // ==================== 构建 ====================

    TaskToolbar.prototype._build = function() {
        this.$container.empty();

        var toolbar = this._cloneTemplate('tp-tpl-toolbar');
        var $toolbar = $(toolbar);

        // 按配置裁剪
        if (!this.config.enableCreate) {
            $toolbar.find('.tp-btn-create').remove();
        }
        if (!this.config.enableSearch) {
            $toolbar.find('.tp-search-group').remove();
        }
        if (!this.config.enableFilter) {
            $toolbar.find('.tp-filter-group').remove();
        }
        if (!this.config.enableCardView) {
            $toolbar.find('.tp-view-toggle').remove();
        }

        this.$container.append(toolbar);
        this._$toolbar = $toolbar;
        this._syncViewButtons();
        this._bindEvents();
    };

    /**
     * 同步视图切换按钮的 active 状态（根据 _currentView）
     */
    TaskToolbar.prototype._syncViewButtons = function() {
        if (!this._$toolbar) return;
        var $listBtn = this._$toolbar.find('.tp-btn-list');
        var $cardBtn = this._$toolbar.find('.tp-btn-card');
        if (this._currentView === 'card') {
            $cardBtn.addClass('active');
            $listBtn.removeClass('active');
        } else {
            $listBtn.addClass('active');
            $cardBtn.removeClass('active');
        }
    };

    // ==================== 事件绑定 ====================

    TaskToolbar.prototype._bindEvents = function() {
        var self = this;

        // 创建按钮
        self._$toolbar.find('.tp-btn-create').on('click', function() {
            if (self.config.onCreate) {
                self.config.onCreate();
            }
        });

        // 搜索框
        self._$toolbar.find('.tp-search-btn').on('click', function() {
            self._triggerSearch();
        });
        self._$toolbar.find('.tp-search-input').on('keypress', function(e) {
            if (e.which === 13) {
                e.preventDefault();
                self._triggerSearch();
            }
        });

        // 过滤下拉
        self._$toolbar.find('.tp-filter-state, .tp-filter-source, .tp-filter-target').on('change', function() {
            self._triggerSearch();
        });

        // 视图切换
        self._$toolbar.find('.tp-btn-list').on('click', function() {
            if (self._currentView !== 'list') {
                self._currentView = 'list';
                self._syncViewButtons();
                if (self.config.onViewChange) {
                    self.config.onViewChange('list');
                }
            }
        });
        self._$toolbar.find('.tp-btn-card').on('click', function() {
            if (self._currentView !== 'card') {
                self._currentView = 'card';
                self._syncViewButtons();
                if (self.config.onViewChange) {
                    self.config.onViewChange('card');
                }
            }
        });
    };

    TaskToolbar.prototype._triggerSearch = function() {
        if (this.config.onSearch) {
            this.config.onSearch(this.getSearchParams());
        }
    };

    // ==================== 工具 ====================

    TaskToolbar.prototype._cloneTemplate = function(templateId) {
        var tpl = document.getElementById(templateId);
        if (!tpl) {
            console.error('[TaskToolbar] 模板未找到: #' + templateId);
            return document.createElement('div');
        }
        var clone = tpl.content.cloneNode(true);
        return clone.firstElementChild || clone.firstChild;
    };

    // ==================== 导出 ====================

    window.TaskToolbar = TaskToolbar;

})(jQuery);
