/**
 * 双列表选择器组件
 * 用于表选择、字段选择等场景，支持已选/未选分离展示
 *
 * 使用示例:
 * const selector = new DualListSelector({
 *     modalId: 'myModal',
 *     title: '选择项目',
 *     data: [{id: '1', name: 'Item 1', type: 'TABLE'}],
 *     onConfirm: (selectedIds) => console.log(selectedIds)
 * });
 * selector.show();
 */
class DualListSelector {
    constructor(options) {
        this.options = $.extend({
            modalId: 'dualListModal',
            title: '选择项目',
            leftTitle: '可选列表',
            rightTitle: '已选列表',
            data: [],
            selected: [],
            displayField: 'name',
            typeField: 'type',
            onConfirm: null,
            onCancel: null
        }, options);

        this.selectedItems = [...this.options.selected];
        this.init();
    }

    init() {
        this._idPrefix = this.options.modalId.replace(/[^a-zA-Z0-9]/g, '_');
        this.createModal();
        this.bindEvents();
        this.render();
    }

    unbindEvents($modal) {
        if (!$modal || !$modal.length) return;
        const p = this._idPrefix;
        $modal.find(`#${p}-availableList`).off();
        $modal.find(`#${p}-selectedList`).off();
        $modal.find(`#${p}-moveRight`).off();
        $modal.find(`#${p}-moveLeft`).off();
        $modal.find(`#${p}-selectAllAvailable`).off();
        $modal.find(`#${p}-removeAllSelected`).off();
        $modal.find(`#${p}-confirmSelection`).off();
        $modal.find(`#${p}-availableSearch`).off();
        $modal.find(`#${p}-selectedSearch`).off();
        $modal.find(`#${p}-availableMatchMode`).off();
        $modal.off('hidden.bs.modal');
    }

    destroy() {
        this.unbindEvents(this.$modal);
    }

    createModal() {
        const modalId = this.options.modalId;
        const existingModal = $(`#${modalId}`);
        
        if (existingModal.length > 0) {
            this.unbindEvents(existingModal);
            existingModal.find(`#${this._idPrefix}-availableList`).empty();
            existingModal.find(`#${this._idPrefix}-selectedList`).empty();
            existingModal.find(`#${this._idPrefix}-availableSearch`).val('');
            existingModal.find(`#${this._idPrefix}-selectedSearch`).val('');
        }

        const html = `
            <div class="modal fade" id="${modalId}" tabindex="-1" role="dialog">
                <div class="modal-dialog modal-lg" role="document">
                    <div class="modal-content">
                        <div class="modal-header">
                            <button type="button" class="close" data-dismiss="modal">&times;</button>
                            <h4 class="modal-title">${this.options.title}</h4>
                        </div>
                        <div class="modal-body">
                            <div class="row">
                                <div class="col-md-5">
                                    <div class="panel panel-default">
                                        <div class="panel-heading">
                                            ${this.options.leftTitle}
                                            <span class="badge pull-right" id="${this._idPrefix}-availableCount">0</span>
                                        </div>
                                        <div class="panel-body">
                                            <div style="display: flex; gap: 5px;">
                                                <input type="text" class="form-control input-sm"
                                                       id="${this._idPrefix}-availableSearch" placeholder="搜索..." style="flex: 1;">
                                                <select class="form-control input-sm" id="${this._idPrefix}-availableMatchMode" style="width: 70px; padding: 2px;">
                                                    <option value="keyword">关键字</option>
                                                    <option value="exact">全词</option>
                                                </select>
                                            </div>
                                            <ul class="list-group dual-list" id="${this._idPrefix}-availableList"
                                                style="max-height: 240px; overflow-y: auto; margin-top: 8px;">
                                            </ul>
                                        </div>
                                    </div>
                                </div>
                                <div class="col-md-2 text-center" style="padding-top: 60px;">
                                    <button type="button" class="btn btn-default btn-block btn-sm"
                                            id="${this._idPrefix}-selectAllAvailable" title="全选">
                                        <i class="fa fa-angle-double-right"></i>
                                    </button>
                                    <button type="button" class="btn btn-default btn-block btn-sm"
                                            id="${this._idPrefix}-moveRight" title="添加选中" style="margin-top: 10px;">
                                        <i class="fa fa-chevron-right"></i>
                                    </button>
                                    <button type="button" class="btn btn-default btn-block btn-sm"
                                            id="${this._idPrefix}-moveLeft" title="移除选中" style="margin-top: 10px;">
                                        <i class="fa fa-chevron-left"></i>
                                    </button>
                                    <button type="button" class="btn btn-default btn-block btn-sm"
                                            id="${this._idPrefix}-removeAllSelected" title="全部移除" style="margin-top: 10px;">
                                        <i class="fa fa-angle-double-left"></i>
                                    </button>
                                </div>
                                <div class="col-md-5">
                                    <div class="panel panel-success">
                                        <div class="panel-heading">
                                            ${this.options.rightTitle}
                                            <span class="badge pull-right" id="${this._idPrefix}-selectedCount">0</span>
                                        </div>
                                        <div class="panel-body">
                                            <input type="text" class="form-control input-sm"
                                                   id="${this._idPrefix}-selectedSearch" placeholder="搜索...">
                                            <ul class="list-group dual-list" id="${this._idPrefix}-selectedList"
                                                style="max-height: 270px; overflow-y: auto; margin-top: 8px;">
                                            </ul>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-default" data-dismiss="modal">取消</button>
                            <button type="button" class="btn btn-primary" id="${this._idPrefix}-confirmSelection">确定</button>
                        </div>
                    </div>
                </div>
            </div>
        `;

        if (existingModal.length === 0) {
            $('body').append(html);
        }

        this.$modal = $(`#${modalId}`);
        this.$availableList = this.$modal.find(`#${this._idPrefix}-availableList`);
        this.$selectedList = this.$modal.find(`#${this._idPrefix}-selectedList`);
        this.$availableSearch = this.$modal.find(`#${this._idPrefix}-availableSearch`);
        this.$selectedSearch = this.$modal.find(`#${this._idPrefix}-selectedSearch`);
        this.$availableMatchMode = this.$modal.find(`#${this._idPrefix}-availableMatchMode`);
    }

    bindEvents() {
        const self = this;

        this.$availableSearch.on('input', function() {
            self.filterList($(this).val(), self.$availableList, self.$availableMatchMode.val());
        });

        this.$availableMatchMode.on('change', () => {
            self.filterList(self.$availableSearch.val(), self.$availableList, self.$availableMatchMode.val());
        });

        this.$selectedSearch.on('input', function() {
            self.filterList($(this).val(), self.$selectedList);
        });

        this.$modal.find(`#${this._idPrefix}-moveRight`).click(() => this.moveRight());
        this.$modal.find(`#${this._idPrefix}-moveLeft`).click(() => this.moveLeft());
        this.$modal.find(`#${this._idPrefix}-selectAllAvailable`).click(() => this.selectAllAvailable());
        this.$modal.find(`#${this._idPrefix}-removeAllSelected`).click(() => this.removeAllSelected());

        this.$modal.find(`#${this._idPrefix}-confirmSelection`).click(() => {
            if (this.options.onConfirm) {
                this.options.onConfirm(this.selectedItems);
            }
            this.$modal.modal('hide');
            this.clearSearchInputs();
        });

        // 弹窗关闭时清空搜索框
        this.$modal.on('hidden.bs.modal', () => {
            this.clearSearchInputs();
        });

        this.$availableList.on('dblclick', 'li', function() {
            const id = $(this).data('id');
            self.addToSelected(id);
            self.render();
        });

        this.$selectedList.on('dblclick', 'li', function() {
            const id = $(this).data('id');
            self.removeFromSelected(id);
            self.render();
        });

        this.$availableList.on('click', 'li', function(e) {
            if (!e.ctrlKey && !e.metaKey) {
                self.$availableList.find('li').removeClass('active');
            }
            $(this).toggleClass('active');
        });

        this.$selectedList.on('click', 'li', function(e) {
            if (!e.ctrlKey && !e.metaKey) {
                self.$selectedList.find('li').removeClass('active');
            }
            $(this).toggleClass('active');
        });
    }

    render() {
        const availableItems = this.options.data.filter(
            item => !this.selectedItems.includes(item.id)
        );
        const selectedItems = this.options.data.filter(
            item => this.selectedItems.includes(item.id)
        );

        this.renderList(this.$availableList, availableItems);
        this.renderList(this.$selectedList, selectedItems);

        this.$modal.find(`#${this._idPrefix}-availableCount`).text(availableItems.length);
        this.$modal.find(`#${this._idPrefix}-selectedCount`).text(selectedItems.length);
    }

    renderList($container, items) {
        const html = items.map(item => {
            const displayText = item[this.options.displayField];
            const typeText = item[this.options.typeField] ? ` (${item[this.options.typeField]})` : '';
            return `
                <li class="list-group-item" data-id="${item.id}" style="cursor: pointer; word-break: break-all;">
                    ${displayText}${typeText}
                </li>
            `;
        }).join('');
        $container.html(html);
    }

    addToSelected(id) {
        if (!this.selectedItems.includes(id)) {
            this.selectedItems.push(id);
        }
    }

    removeFromSelected(id) {
        const index = this.selectedItems.indexOf(id);
        if (index > -1) {
            this.selectedItems.splice(index, 1);
        }
    }

    moveRight() {
        this.$availableList.find('li.active').each((_, el) => {
            this.addToSelected($(el).data('id'));
        });
        this.render();
    }

    moveLeft() {
        this.$selectedList.find('li.active').each((_, el) => {
            this.removeFromSelected($(el).data('id'));
        });
        this.render();
    }

    selectAllAvailable() {
        const self = this;
        this.$availableList.find('li:visible').each((_, el) => {
            self.addToSelected($(el).data('id'));
        });
        this.render();
    }

    removeAllSelected() {
        const self = this;
        this.$selectedList.find('li:visible').each((_, el) => {
            self.removeFromSelected($(el).data('id'));
        });
        this.render();
    }

    filterList(keyword, $list, mode = 'keyword') {
        if (!keyword.trim()) {
            $list.find('li').show();
            return;
        }

        const keywords = keyword.toLowerCase().split(',').map(k => k.trim()).filter(k => k);
        $list.find('li').each(function() {
            const text = $(this).text().toLowerCase();
            const displayName = $(this).data('id').toString().toLowerCase();
            let match;
            if (mode === 'exact') {
                match = keywords.some(k => displayName === k);
            } else {
                match = keywords.some(k => text.includes(k));
            }
            $(this).toggle(match);
        });
    }

    clearSearchInputs() {
        this.$availableSearch.val('');
        this.$selectedSearch.val('');
        this.$availableList.find('li').show();
        this.$selectedList.find('li').show();
    }

    show() {
        this.$modal.modal('show');
    }

    hide() {
        this.$modal.modal('hide');
    }

    getSelected() {
        return this.selectedItems;
    }

    setSelected(selected) {
        this.selectedItems = [...selected];
        this.render();
    }
}

/**
 * 选择器管理器 - 统一初始化和管理所有选择器
 * 消除重复代码，提供统一的配置和初始化方式
 */
(function() {
    if (window.SelectorManager && window.SelectorManager.instances) {
        Object.keys(window.SelectorManager.instances).forEach(key => {
            const instance = window.SelectorManager.instances[key];
            if (instance && typeof instance.destroy === 'function') {
                instance.destroy();
            }
        });
    }

    window.SelectorManager = {
        instances: {},

    /**
     * 通用初始化选择器
     * @param {Object} config 配置对象
     * @param {string} config.type 选择器类型: 'table' | 'field'
     * @param {string} config.side 侧边: 'source' | 'target'
     * @param {string} config.btnId 按钮ID
     * @param {string} config.selectId 隐藏的select元素ID
     * @param {string} config.modalId 模态框ID
     * @param {string} config.title 模态框标题
     * @param {string} config.leftTitle 左侧列表标题
     * @param {string} config.rightTitle 右侧列表标题
     * @param {Function} config.onConfirm 确认回调
     * @param {string} config.syncTarget 自动同步目标选择器的key（可选）
     */
    init(config) {
        const key = `${config.side}${config.type === 'table' ? 'Table' : 'Field'}Selector`;

        if (this.instances[key]) {
            this.instances[key].destroy();
            delete this.instances[key];
        }

        $(`#${config.btnId}`).off('click');

        const data = this.extractDataFromSelect(config.selectId, config.type);

        this.instances[key] = new DualListSelector({
            modalId: config.modalId,
            title: config.title,
            leftTitle: config.leftTitle,
            rightTitle: config.rightTitle,
            data: data,
            selected: [],
            displayField: 'name',
            typeField: 'type',
            onConfirm: (selectedIds) => {
                this.updateButtonDisplay(config.side, config.type, selectedIds, data);
                $(`#${config.selectId}`).val(selectedIds).trigger('change');
                if (config.onConfirm) {
                    config.onConfirm(selectedIds, data);
                }
            }
        });

        $(`#${config.btnId}`).click(() => {
            const currentSelected = $(`#${config.selectId}`).val() || [];
            this.instances[key].setSelected(Array.isArray(currentSelected) ? currentSelected : [currentSelected]);
            this.instances[key].show();
        });

        return this.instances[key];
    },

    /**
     * 从select元素提取数据
     * @param {string} selectId select元素ID
     * @param {string} type 数据类型: 'table' | 'field'
     * @returns {Array} 数据数组
     */
    extractDataFromSelect(selectId, type) {
        const data = [];
        $(`#${selectId} option`).each(function() {
            const $option = $(this);
            const name = $option.val();
            const text = $option.text();

            if (!name) return;

            // 提取类型信息
            const typeMatch = text.match(/\((.*?)\)/);
            const itemType = typeMatch ? typeMatch[1] : (type === 'table' ? 'TABLE' : '');

            const item = {
                id: name,
                name: name,
                type: itemType
            };

            // 字段类型额外提取主键信息
            if (type === 'field') {
                item.pk = text.includes('[主键]');
            }

            data.push(item);
        });
        return data;
    },

    /**
     * 更新按钮显示
     * @param {string} side 侧边: 'source' | 'target'
     * @param {string} type 类型: 'table' | 'field'
     * @param {Array} selectedIds 已选ID数组
     * @param {Array} allData 所有数据
     */
    updateButtonDisplay(side, type, selectedIds, allData) {
        const prefix = `${side}${type === 'table' ? 'Table' : 'Field'}`;
        const $text = $(`#${prefix}Text`);
        const $count = $(`#${prefix}Count`);
        const itemName = type === 'table' ? '表' : '字段';

        if (selectedIds.length === 0) {
            $text.text(`选择${itemName}`);
            $count.hide();
        } else if (selectedIds.length === 1) {
            const item = allData.find(d => d.id === selectedIds[0]);
            $text.text(item ? item.name : `选择${itemName}`);
            $count.hide();
        } else {
            $text.text(`已选 ${selectedIds.length} 个${itemName}`);
            $count.text(selectedIds.length).show();
        }
    },

    /**
     * 获取选择器实例
     * @param {string} key 选择器key
     * @returns {DualListSelector|null}
     */
    get(key) {
        return this.instances[key] || null;
    },

    /**
     * 设置选择器的选中值
     * @param {string} key 选择器key
     * @param {Array} selected 选中值数组
     */
    setSelected(key, selected) {
        const selector = this.instances[key];
        if (selector) {
            selector.setSelected(selected);
        }
    }
};
})();
