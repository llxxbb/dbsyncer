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
        if (tempPKs.indexOf(fieldName) < 0) {
            tempPKs.push(fieldName);
            this.save(tableGroupId, tempPKs);
        }
    },

    removeTempPK: function(tableGroupId, fieldName) {
        var tempData = this.load(tableGroupId);
        if (!tempData) return;
        var tempPKs = tempData.tempPKs.slice();
        var idx = tempPKs.indexOf(fieldName);
        if (idx >= 0) {
            tempPKs.splice(idx, 1);
            this.save(tableGroupId, tempPKs);
        }
    },

    clear: function(tableGroupId) {
        localStorage.removeItem(this.getStorageKey(tableGroupId));
    },

    arraysEqual: function(a, b) {
        if (a.length !== b.length) return false;
        var sortedA = a.slice().sort();
        var sortedB = b.slice().sort();
        return sortedA.every(function(v, i) { return v === sortedB[i]; });
    }
};
