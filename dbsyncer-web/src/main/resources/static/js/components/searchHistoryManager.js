/**
 * 搜索历史管理器 - 可复用组件
 * 提供搜索历史的存储、展示、删除功能
 * 
 * 使用示例:
 * const history = new SearchHistoryManager({
 *     storageKey: 'mySearchHistory',
 *     maxItems: 10,
 *     onSelect: (keyword) => console.log('选中:', keyword)
 * });
 * history.attachTo($('#searchInput'));
 */
class SearchHistoryManager {
    constructor(options) {
        this.options = $.extend({
            storageKey: 'searchHistory',  // localStorage 存储键名
            maxItems: 10,                 // 最大保存条数
            onSelect: null,               // 选中历史记录的回调
            onDelete: null                // 删除历史记录的回调
        }, options);

        this.history = this.loadHistory();
        this.$dropdown = null;
        this.$input = null;
    }

    // 从 localStorage 加载历史记录
    loadHistory() {
        try {
            const stored = localStorage.getItem(this.options.storageKey);
            return stored ? JSON.parse(stored) : [];
        } catch (e) {
            return [];
        }
    }

    // 保存历史记录到 localStorage
    saveHistory() {
        try {
            localStorage.setItem(this.options.storageKey, JSON.stringify(this.history));
        } catch (e) {
            console.warn('保存搜索历史失败:', e);
        }
    }

    // 添加搜索记录
    add(keyword) {
        if (!keyword || !keyword.trim()) return;
        
        keyword = keyword.trim();
        
        // 移除重复项
        this.history = this.history.filter(item => item !== keyword);
        
        // 添加到开头
        this.history.unshift(keyword);
        
        // 限制数量
        if (this.history.length > this.options.maxItems) {
            this.history = this.history.slice(0, this.options.maxItems);
        }
        
        this.saveHistory();
    }

    // 删除单条记录
    remove(keyword) {
        this.history = this.history.filter(item => item !== keyword);
        this.saveHistory();
        this.renderDropdown();
        if (this.options.onDelete) {
            this.options.onDelete(keyword);
        }
    }

    // 清空所有记录
    clear() {
        this.history = [];
        this.saveHistory();
        this.renderDropdown();
    }

    // 获取所有历史记录
    getAll() {
        return [...this.history];
    }

    // 绑定到输入框
    attachTo($input) {
        this.$input = $input;
        this.createDropdown();
        this.bindEvents();
        return this;
    }

    // 创建下拉列表 DOM
    createDropdown() {
        const dropdownId = `search-history-${this.options.storageKey}`;
        
        // 移除已存在的下拉列表
        $(`#${dropdownId}`).remove();
        
        this.$dropdown = $(`
            <div id="${dropdownId}" class="search-history-dropdown"
                 style="display: none; position: absolute; top: 100%; left: 0; right: 0; 
                        background: #fff; border: 1px solid #ddd; border-radius: 4px; 
                        box-shadow: 0 2px 8px rgba(0,0,0,0.15); z-index: 1050; max-height: 200px; 
                        overflow-y: auto;">
                <div class="search-history-list"></div>
            </div>
        `);
        
        // 将下拉列表添加到输入框的父元素
        this.$input.parent().css('position', 'relative');
        this.$input.after(this.$dropdown);
    }

    // 渲染下拉列表内容
    renderDropdown() {
        const $list = this.$dropdown.find('.search-history-list');
        
        if (this.history.length === 0) {
            $list.html('<div class="text-muted text-center" style="padding: 10px;">暂无搜索记录</div>');
            return;
        }
        
        let html = '';
        this.history.forEach((item, index) => {
            html += `
                <div class="search-history-item" data-keyword="${this.escapeHtml(item)}"
                     style="display: flex; justify-content: space-between; align-items: center; 
                            padding: 8px 12px; cursor: pointer; border-bottom: 1px solid #f0f0f0;">
                    <span style="flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                        <i class="fa fa-history text-muted" style="margin-right: 8px;"></i>
                        ${this.escapeHtml(item)}
                    </span>
                    <button type="button" class="btn btn-xs btn-link delete-history-item" 
                            data-keyword="${this.escapeHtml(item)}" title="删除"
                            style="color: #999; padding: 2px 6px;">
                        <i class="fa fa-times"></i>
                    </button>
                </div>
            `;
        });
        
        // 添加清空全部按钮
        html += `
            <div class="search-history-clear text-center" 
                 style="padding: 8px; cursor: pointer; color: #666; border-top: 1px solid #e0e0e0;">
                <i class="fa fa-trash-o" style="margin-right: 5px;"></i>清空全部记录
            </div>
        `;
        
        $list.html(html);
    }

    // 绑定事件
    bindEvents() {
        const self = this;
        
        // 输入框聚焦时显示下拉列表
        this.$input.on('focus', () => {
            this.renderDropdown();
            this.$dropdown.show();
        });
        
        // 点击外部隐藏下拉列表
        $(document).on('click.searchHistory', (e) => {
            if (!$(e.target).closest(this.$input.parent()).length) {
                this.$dropdown.hide();
            }
        });
        
        // 点击历史记录项
        this.$dropdown.on('click', '.search-history-item', function(e) {
            if ($(e.target).closest('.delete-history-item').length) return;
            
            const keyword = $(this).data('keyword');
            self.$input.val(keyword);
            self.$dropdown.hide();
            
            // 触发输入事件，让搜索生效
            self.$input.trigger('input');
            
            if (self.options.onSelect) {
                self.options.onSelect(keyword);
            }
        });
        
        // 点击删除按钮
        this.$dropdown.on('click', '.delete-history-item', function(e) {
            e.stopPropagation();
            const keyword = $(this).data('keyword');
            self.remove(keyword);
        });
        
        // 点击清空全部
        this.$dropdown.on('click', '.search-history-clear', () => {
            this.clear();
        });
        
        // 输入框回车时保存搜索记录
        this.$input.on('keydown', (e) => {
            if (e.key === 'Enter') {
                this.add(this.$input.val());
            }
        });
        
        // 输入框失去焦点时保存搜索记录（如果有内容）
        this.$input.on('blur', () => {
            setTimeout(() => {
                this.add(this.$input.val());
            }, 200);
        });
    }

    // HTML 转义，防止 XSS
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // 销毁实例
    destroy() {
        $(document).off('click.searchHistory');
        if (this.$dropdown) {
            this.$dropdown.remove();
        }
    }
}

// 导出到全局
window.SearchHistoryManager = SearchHistoryManager;
