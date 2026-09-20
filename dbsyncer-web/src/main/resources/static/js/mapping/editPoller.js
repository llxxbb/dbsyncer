/**
 * 轮询/状态模块（从 edit.js 拆出，Stage 8）
 *
 * 职责：任务编辑页的状态轮询、耗时显示更新——
 *       定期从后端获取任务状态并更新页面显示（状态/进度/耗时/速度/表组进度），
 *       每秒刷新耗时显示。
 *
 * 依赖（全部是全局，不是模块内定义）：
 *   - jQuery $
 *   - pageGuard / isPageAlive   common.js（ADR-0032 页面代际守卫）
 *   - doGetWithoutLoading       common.js
 *   - bootGrowl                 common.js
 *   - getCheckedBoxSize         editTable.js（运行时调用，在轮询回调内）
 *   - updateTableGroups         editTable.js（运行时调用，在轮询回调内）
 *
 * 导出（全局变量，供其他模块使用）：
 *   - currentMappingId    当前任务ID，editLog.js 读取
 *   - mappingPoller       轮询控制器 { start, stop }，editOperationButtons.js 和
 *                         edit.js 的返回按钮调用 stop()
 *
 * 入向调用点：
 *   - edit.js 的 ready 块中调用 getMappping(mappingId)
 *   因为是运行时回调（非求值期），加载顺序无严格要求。
 *
 * 注意：本模块含有顶层 IIFE（每秒刷新耗时显示），会在脚本加载时立即执行。
 *   但由于它只依赖 pageGuard/isPageAlive/renderDuration（本模块内定义），
 *   不依赖 edit.js 的任何内容，所以前置加载安全。
 */

// 全局变量，用于保存当前任务ID
var currentMappingId = null;

/**
 * 任务轮询控制器（ADR-0032）。
 *
 * 定时器句柄刻意不放在顶层 var 里：edit.js 每次导航都会被重新 eval，顶层赋值
 * 会先抹掉上一个实例仍持有的句柄（使其永远清不掉），而且新旧实例会竞争写入
 * 同一个全局槽位。那正是「旧定时器 clearInterval(timer3) 误杀新定时器、轮询
 * 永久停止、界面只能靠 F5 刷新」的成因。
 *
 * 改为闭包持有后，句柄对外不可见也不可写，每个页面实例只能清除自己的定时器。
 * start() 内部先停掉上一个，保证同一实例内最多只有一个轮询在跑。
 */
var mappingPoller = (function () {
    // 当前页面实例私有的定时器句柄
    var handle = null;

    // 仅回收句柄。start() 复用它，避免顺带清掉调用方刚设置好的 currentMappingId。
    function clearHandle() {
        if (handle !== null) {
            clearInterval(handle);
            handle = null;
        }
    }

    // 停止轮询。保留原 clearTimer() 的语义：同时清空任务标识，
    // 使依赖 currentMappingId 的日志查询等逻辑一并失效。
    function stop() {
        clearHandle();
        currentMappingId = null;
    }

    function start(guard, intervalMs, tick) {
        clearHandle();
        var self = setInterval(function () {
            // 页面已被替换：停掉轮询并自我销毁。
            // 有了代际守卫，就不需要再往常驻的外壳导航按钮上层层绑定清理逻辑。
            // 注意这里只能清「自己」的句柄 self，不能清 handle ——
            // handle 可能已被新实例覆盖，清它会误杀健康的定时器。
            if (!isPageAlive(guard)) {
                clearInterval(self);
                if (handle === self) {
                    handle = null;
                }
                return;
            }
            tick();
        }, intervalMs);
        handle = self;
    }

    return { start: start, stop: stop };
})();

function getMappping(id) {
    // 先停掉旧轮询，确保每次调用都使用最新的id
    mappingPoller.stop();
    // 更新当前任务ID
    currentMappingId = id;
    // 页面代际守卫（ADR-0032）：页面被替换后轮询自行停止
    var guard = pageGuard();

    // 立即加载一次数据，确保页面初始加载时就能显示表数据
    $.ajax({
        url: '/mapping/get?id='+ id + "&refresh=" + new Date().getTime(),
        type: 'GET',
        success: function(dataJson) {
            // 重新绑定事件处理器（如果需要）
            var success = dataJson.success;
            var m = dataJson.resultValue;
            if(success && m ){
                // 安全访问对象属性
                var mid = m && m.id ? m.id : '';
                var meta = m && m.meta ? m.meta : {};
                var metaId = meta.id || '';
                var total = meta.total || 0;
                var successCount = meta.success || 0;
                var failCount = meta.fail || 0;
                
                // 更新 metaId 隐藏字段
                $("#metaId").val(metaId);

                updateMetaDuration(meta.beginTime || 0, meta.endTime || 0, meta.state || 0);

                // 更新表映射关系数据
                updateTableGroups(mid, total, successCount, failCount);
            }
        },
        error: function() {
            // alert('刷新失败');
        }
    });

    doGetWithoutLoading("/monitor/getRefreshIntervalSeconds", {}, function (data) {
        if (data.success == true) {
            // 保存当前闭包中的任务ID
            var localId = id;
            // 响应到达时页面可能已被 SPA 重载（如启动/停止后 refresh() 触发 hashchange，
            // __pageEpoch 递增）。此时本次响应属于旧页面实例，必须直接丢弃，
            // 否则会创建出守卫已失效的"僵尸定时器"。
            if (!isPageAlive(guard)) {
                return;
            }
            // 检查当前任务ID是否与闭包中的任务ID相同，如果不同则不创建定时器
            if (currentMappingId === localId) {
                // 句柄由 mappingPoller 闭包私有持有，start() 内部会先停掉上一个；
                // 代际守卫与「只清自己句柄」的自我销毁逻辑都在控制器里，
                // 这里只负责描述「每个周期做什么」。
                mappingPoller.start(guard, data.resultValue * 1000, function () {
                    // 再次检查当前任务ID是否与闭包中的任务ID相同
                    if (currentMappingId === localId) {
                        $.ajax({
                            url: '/mapping/get?id='+ localId + "&refresh=" + new Date().getTime(),
                            type: 'GET',
                            success: function(dataJson) {
                                // 重新绑定事件处理器（如果需要）
                                var success = dataJson.success;
                                var m = dataJson.resultValue;
                                if(success && m ){
                                    // 安全访问对象属性
                                    var mid = m && m.id ? m.id : '';
                                    var model = m && m.model ? m.model : '';
                                    var modelname =  '';
                                    if(model == 'full'){
                                        modelname= '全量';
                                    }else if(model == 'increment'){
                                        modelname= '增量';
                                    }else if(model == 'fullIncrement'){
                                        modelname= '混合';
                                    }
                                    var meta = m && m.meta ? m.meta : {};
                                    var total = meta.total || 0;
                                    var successCount = meta.success || 0;
                                    var failCount = meta.fail || 0;
                                    var beginTime = meta.beginTime || 0;
                                    var updateTime = meta.updateTime || 0;
                                    var syncPhase = meta.syncPhase || {};
                                    var syncPhaseCode = syncPhase.code || 0;
                                    var counting = meta.counting || false;
                                    var errorMessage = meta.errorMessage || '';
                                    var metaId = meta.id || '';
                                    var state = meta.state || '';
                                    
                                    // 更新 metaId 隐藏字段
                                    $("#metaId").val(metaId);

                                    var stateHtmlContent = '<p>任务状态：</p>';
                                    if(state == 0){
                                        stateHtmlContent += '<span class="highlight-number total-color">未运行</span>';
                                    }else if(state == 1){
                                        stateHtmlContent += '<span class="highlight-number success-color">运行中</span>';
                                    }else if(state == 2){
                                        stateHtmlContent += '<span class="highlight-number error-color">停止中</span>';
                                    }else if(state == 3){
                                        stateHtmlContent += '<span class="highlight-number error-color">异常</span>';
                                    }
                                    $("#mappingState").html(stateHtmlContent);

                                    var endTime = meta.endTime || 0;
                                    updateMetaDuration(beginTime, endTime, state);

                                    // 更新重新同步按钮状态
                                    var $resyncBtn = $("#tableGroupResyncBtn");
                                    if ($resyncBtn.length > 0) {
                                        var hasChecked = getCheckedBoxSize($(".tableGroupCheckbox")).length > 0;
                                        var isStopped = state == 0 || state == 3;
                                        $resyncBtn.prop('disabled', !hasChecked || !isStopped);
                                    }

                                    $("#metaPercent").find("p").text("数据同步进度:");
                                    if (counting) {
                                        $("#metaPercent").find(".highlight-number").text("(正在统计中)");
                                    } else {
                                        if (total > 0 && (successCount + failCount) > 0) {
                                            var progress = ((successCount + failCount) / total * 100).toFixed(2);
                                            $("#metaPercent").find(".highlight-number").text(progress + "%");
                                            // 同步更新文本标签
                                            $("#progressText").text(progress + "%");
                                        } else {
                                            $("#metaPercent").find(".highlight-number").text("0%");
                                            $("#progressText").text("0%");
                                        }
                                    }
                                    $("#metaModel").html('<p>总数:</p>' + '<span class="highlight-number total-color">'+total+'</span>');

                                    if (successCount > 0) {
                                        $("#metaSuccess").html('<p>成功:</p>' +'<span class="highlight-number success-color">'+ successCount+'</span>');
                                    }
                                    if (failCount > 0) {
                                        $("#metaError").html('<p>失败:</p>' +'<span class="highlight-number error-color">'+ failCount+'</span>');
                                    }

                                    // 更新表映射关系数据
                                    updateTableGroups(mid, total, successCount, failCount);
                                }
                            },
                            error: function() {
                                // alert('刷新失败');
                            }
                        });
                    }
                });
            }
        } else {
            bootGrowl(data.resultValue, "danger");
        }
    });
}

function updateMetaDuration(beginTime, endTime, state) {
    var $duration = $("#metaDuration");
    if (!$duration.length) return;
    $duration.attr('data-begin-time', beginTime);
    $duration.attr('data-end-time', endTime);
    $duration.attr('data-state', state);
    renderDuration();
}

function renderDuration() {
    var $duration = $("#metaDuration");
    var beginTime = parseInt($duration.attr('data-begin-time')) || 0;
    var endTime = parseInt($duration.attr('data-end-time')) || 0;
    var state = parseInt($duration.attr('data-state')) || 0;
    var $span = $duration.find('.highlight-number');
    if (!$span.length) return;
    if (beginTime <= 0) {
        $span.text('-');
        return;
    }
    var endMs = (state === 1) ? Date.now() : endTime;
    if (endMs <= 0) {
        $span.text('-');
        return;
    }
    var totalSeconds = Math.floor((endMs - beginTime) / 1000);
    if (totalSeconds < 0) totalSeconds = 0;
    if (totalSeconds < 60) {
        $span.text(totalSeconds + '秒');
    } else if (totalSeconds < 3600) {
        var minutes = Math.floor(totalSeconds / 60);
        $span.text(minutes + '分' + (totalSeconds - minutes * 60) + '秒');
    } else {
        var hours = Math.floor(totalSeconds / 3600);
        var minutes = Math.floor((totalSeconds - hours * 3600) / 60);
        $span.text(hours + '时' + minutes + '分' + (totalSeconds - hours * 3600 - minutes * 60) + '秒');
    }
}

// 每秒刷新耗时显示。
// 用页面代际守卫自我销毁（ADR-0032）：离开编辑页后下一次触发即停止。
// 定时器句柄必须局部持有，不能用顶层 var（那是全局的，
// 重复进入页面会被新实例覆盖，导致旧定时器句柄丢失、永远无法清除）。
(function () {
    var guard = pageGuard();
    var timer = setInterval(function () {
        if (!isPageAlive(guard)) {
            clearInterval(timer);
            return;
        }
        renderDuration();
    }, 1000);
})();
