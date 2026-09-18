var TempPKManager = {
    STORAGE_PREFIX: 'dbsyncer_temp_pk_',

    getStorageKey: function(tableGroupId) {
        return this.STORAGE_PREFIX + tableGroupId;
    },

    save: function(tableGroupId, tempPKs) {
        var data = {
            tempPKs: tempPKs,
            updatedAt: Date.now()
        };
        localStorage.setItem(this.getStorageKey(tableGroupId), JSON.stringify(data));
    },

    load: function(tableGroupId) {
        var raw = localStorage.getItem(this.getStorageKey(tableGroupId));
        if (!raw) return null;
        try {
            return JSON.parse(raw);
        } catch (e) {
            return null;
        }
    },

    addTempPK: function(tableGroupId, fieldName, backendPKs) {
        var tempData = this.load(tableGroupId);
        var tempPKs;
        if (tempData) {
            tempPKs = tempData.tempPKs.slice();
        } else if (backendPKs && backendPKs.length > 0) {
            tempPKs = backendPKs.slice();
        } else {
            tempPKs = [];
        }
        // equalsIgnoreCase 是 null 安全的，故不再需要 fieldNameLower 中间变量
        var exists = tempPKs.some(function(pk) { return equalsIgnoreCase(pk, fieldName); });
        if (!exists) {
            tempPKs.push(fieldName);
            this.save(tableGroupId, tempPKs);
        }
    },

    removeTempPK: function(tableGroupId, fieldName) {
        var tempData = this.load(tableGroupId);
        if (!tempData) return;
        var tempPKs = tempData.tempPKs.slice();
        var idx = -1;
        for (var i = 0; i < tempPKs.length; i++) {
            if (equalsIgnoreCase(tempPKs[i], fieldName)) { idx = i; break; }
        }
        if (idx >= 0) {
            tempPKs.splice(idx, 1);
            this.save(tableGroupId, tempPKs);
        }
    },

    clear: function(tableGroupId) {
        localStorage.removeItem(this.getStorageKey(tableGroupId));
    },

    /**
     * 主键集合是否等价（忽略大小写、忽略顺序）。
     * 调用方 editTableGroup.js 用它算 hasDiff，判错就会弹出多余的「确认修改主键」。
     */
    arraysEqual: function(a, b) {
        // 归一化：调用方可能传入 localStorage 里的残缺数据，避免 a.length 直接抛异常
        var arrA = a || [], arrB = b || [];
        if (arrA.length !== arrB.length) return false;
        // 不能用默认 sort()：它按 UTF-16 码元比较，大写(65-90)排在小写(97-122)之前，
        // 于是 ['ID','name'] 与 ['id','NAME'] 排序结果不同，会被误判为主键有变更。
        // 排序键必须与下面的比较口径一致，同样走 ASCII 折叠。
        var byFoldedCase = function(x, y) {
            var fx = toLowerCaseInvariant(x), fy = toLowerCaseInvariant(y);
            return fx < fy ? -1 : (fx > fy ? 1 : 0);
        };
        var sortedA = arrA.slice().sort(byFoldedCase);
        var sortedB = arrB.slice().sort(byFoldedCase);
        return sortedA.every(function(v, i) { return equalsIgnoreCase(v, sortedB[i]); });
    }
};

//# sourceURL=/js/mapping/tempPKManager.js
