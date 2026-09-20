/**
 * 表格排序模块（从 edit.js 拆出，Stage 2/5）
 *
 * 职责：任务编辑页「数据同步进度」表格（#tableGroupProgress）的点击表头排序，
 *      以及排序状态在 localStorage 中的持久化与恢复。
 *
 * 依赖（全部是全局，不是模块内定义）：
 *   - jQuery $
 *   - localStorage        排序状态，键名 tableGroupSorting_<mappingId>
 *   - #mappingId          取任务 id 拼存储键
 *   - #tableGroupProgress 被排序的表格；仅第 2/4 列（同步数量/速度）可排序
 *
 * 入向调用点（唯一）：edit.js 的 updateTableGroups() 在渲染完表格行后调用
 * addTableSorting()。它在函数体内、运行时解析，与脚本加载顺序无关。
 *
 * 本模块完全自洽：不依赖 edit.js 的任何函数或变量，也不创建任何定时器，
 * 因此不涉及 ADR-0032 的「定时器句柄局部持有」约束。
 *
 * 已知风险（拆分不改变，仅记录）：saveSortingState / loadSortingState 直接读写
 * localStorage 且未做 try/catch 包裹（仅 loadSortingState 里的 JSON.parse 有），
 * 隐私模式或配额耗尽抛 SecurityError 时排序会失效。所幸 addTableSorting() 是在
 * updateTableGroups 的 success 回调末尾、表格行与 #metaSpeed 都已渲染完之后才调用，
 * 因此异常不会吃掉本轮数据渲染，只是排序不可用 + 控制台报错。
 */

// 获取排序状态的存储键
function getSortingStorageKey() {
    var mappingId = $("#mappingId").val() || 'default';
    return 'tableGroupSorting_' + mappingId;
}

// 保存排序状态到 localStorage
function saveSortingState(columnIndex, direction) {
    var key = getSortingStorageKey();
    var state = {
        columnIndex: columnIndex,
        direction: direction,
        timestamp: new Date().getTime()
    };
    localStorage.setItem(key, JSON.stringify(state));
}

// 从 localStorage 读取排序状态
function loadSortingState() {
    var key = getSortingStorageKey();
    var stored = localStorage.getItem(key);
    if (stored) {
        try {
            return JSON.parse(stored);
        } catch (e) {
            return null;
        }
    }
    return null;
}

// 添加表格排序功能
function addTableSorting() {
    var table = $('#tableGroupProgress');
    var headers = table.find('thead th');
    
    // 为表头添加排序功能
    headers.each(function(index) {
        // 只为需要排序的列添加排序功能
        if (index === 2 || index === 4) { // 同步数量、同步速度
            var header = $(this);
            header.css('cursor', 'pointer');
            header.addClass('sortable');
            
            // 添加排序图标
            if (!header.find('.sort-icon').length) {
                header.append('<span class="sort-icon" style="margin-left: 5px; font-size: 12px;">↕</span>');
            }
            
            // 绑定点击事件
            header.off('click').on('click', function() {
                var currentSort = header.data('sort') || 'asc';
                var newSort = currentSort === 'asc' ? 'desc' : 'asc';
                
                // 更新排序状态
                headers.each(function() {
                    $(this).data('sort', '');
                    $(this).find('.sort-icon').text('↕');
                });
                header.data('sort', newSort);
                header.find('.sort-icon').text(newSort === 'asc' ? '↑' : '↓');
                
                // 保存排序状态
                saveSortingState(index, newSort);
                
                // 排序表格
                sortTable(table, index, newSort);
            });
        }
    });
    
    // 恢复之前的排序状态
    restoreSortingState(table);
}

// 恢复排序状态
function restoreSortingState(table) {
    var state = loadSortingState();
    if (state && state.columnIndex !== undefined && state.direction) {
        var headers = table.find('thead th');
        var header = headers.eq(state.columnIndex);
        
        // 检查是否是可排序的列
        if (header.length && (state.columnIndex === 2 || state.columnIndex === 4)) {
            // 更新表头状态
            headers.each(function() {
                $(this).data('sort', '');
                $(this).find('.sort-icon').text('↕');
            });
            header.data('sort', state.direction);
            header.find('.sort-icon').text(state.direction === 'asc' ? '↑' : '↓');
            
            // 应用排序
            sortTable(table, state.columnIndex, state.direction);
        }
    }
}

// 排序表格
function sortTable(table, columnIndex, direction) {
    var tbody = table.find('tbody');
    var rows = tbody.find('tr').toArray();
    
    // 排序行
    rows.sort(function(a, b) {
        var aSortAttr = $(a).find('td').eq(columnIndex).data('sort');
        var bSortAttr = $(b).find('td').eq(columnIndex).data('sort');
        var aVal = (aSortAttr !== undefined && aSortAttr !== null) ? aSortAttr : $(a).find('td').eq(columnIndex).text();
        var bVal = (bSortAttr !== undefined && bSortAttr !== null) ? bSortAttr : $(b).find('td').eq(columnIndex).text();
        
        // 转换为数字进行比较
        if (!isNaN(aVal) && !isNaN(bVal)) {
            aVal = parseFloat(aVal);
            bVal = parseFloat(bVal);
        }
        
        if (aVal < bVal) {
            return direction === 'asc' ? -1 : 1;
        }
        if (aVal > bVal) {
            return direction === 'asc' ? 1 : -1;
        }
        return 0;
    });
    
    // 重新添加行
    tbody.empty();
    $.each(rows, function(index, row) {
        tbody.append(row);
    });
}

//# sourceURL=/js/mapping/editTableSorting.js
