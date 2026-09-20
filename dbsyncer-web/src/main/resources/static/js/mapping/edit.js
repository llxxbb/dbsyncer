//*********************************** 驱动保存 开始位置***********************************//
function submit(data) {
    doPoster("/mapping/edit", data, function (data) {
        if (data.success == true) {
            bootGrowl("修改驱动成功!", "success");
            // 保存成功后刷新当前页面，保留在驱动编辑页面
            var id = $("#mappingId").val();
            doLoader('/mapping/page/edit?id=' + id + "&classOn=1&refresh=" + new Date().getTime());
        } else {
            // 检查是否是目标表不存在的异常（通过错误码识别）
            if (data.status == 400 && data.resultValue &&
                typeof data.resultValue === 'object' &&
                data.resultValue.errorCode === 'TARGET_TABLE_NOT_EXISTS') {
                // 显示确认对话框
                showCreateTableConfirmDialogForMapping(data.resultValue);
            } else {
                // 其他错误，直接显示错误信息
                let errorMsg = typeof data.resultValue === 'string'
                    ? data.resultValue
                    : (data.resultValue && data.resultValue.message ? data.resultValue.message : '操作失败');
                bootGrowl(errorMsg, "danger");
            }
        }
    });
}
//*********************************** 驱动保存 结束位置***********************************//
// 根据同步模型（全量/增量/混合）显示/隐藏配置面板
function toggleModelConfigPanels() {
    var $modelValue = $('input[name="model"]').val();
    var $full = $("#mappingFullConfig");
    var $increment = $("#mappingIncrementConfig");
    if ('full' == $modelValue) {
        $increment.addClass("hidden");
        $full.removeClass("hidden");
    } else if ('increment' == $modelValue) {
        $full.addClass("hidden");
        $increment.removeClass("hidden");
    } else {
        $full.removeClass("hidden");
        $increment.removeClass("hidden");
    }
}

// 刷新页面
function refresh(id,classOn) {
    updateHash('/mapping/page/edit?id=' + id+"&classOn="+classOn+"&t="+new Date().getTime());
}

// 修改驱动名称
function mappingModifyName() {
    var $name = $("#mappingModifyName");
    var tmp = $name.text();
    $name.text("");
    $name.append("<input type='text'/>");
    var $input = $name.find("input");
    $input.focus().val(tmp);
    $input.blur(function () {
        $name.text($(this).val());
        $("#mappingModifyForm input[name='name']").val($(this).val());
        $input.unbind();
    });
}

function doMappingSubmit() {
    // 保存
    let $form = $("#mappingModifyForm");
    if ($form.formValidate() == true) {
        let data = $form.serializeJson();
        submit(data);
        //提交时停止轮询
        mappingPoller.stop();
    }
}

// updateProgressChart 改进版
// 切换到指定的映射关系标签页
function nextToMapping(str) {
    const $baseConfigLink = $('a[href="#' + str + '"]');
    if ($baseConfigLink.length) {
        $baseConfigLink.tab('show');
    }
}

$(function () {

    var classOnVal =  $("#mappingShow").val();
    if(classOnVal){
        // 根据classOn值切换到对应的标签页
        // 1: 映射关系, 2: 高级配置, 3: 错误队列, 其他: 概括
        if(classOnVal == 1){
            nextToMapping('mappingBaseConfig');
        } else if(classOnVal == 2){
            nextToMapping('mappingSuperConfig');
        } else if(classOnVal == 3){
            nextToMapping('mappingErrorQueue');
            initErrorQueue();
        }
        // classOnVal为0或其他值时，默认显示概括标签页（不需要切换）
    }

    var mappingId =  $("#mappingId").val();
    if(mappingId){
        getMappping(mappingId);
    }

    // 根据同步模型显示/隐藏配置面板
    toggleModelConfigPanels();

    // 绑定删除表映射事件
    bindMappingTableGroupCheckBoxClick();

    // 绑定表关系点击事件（内部会自动触发字段差异检查）
    bindMappingTableGroupListClick();
    // 绑定下拉选择事件自动匹配相似表事件
    bindTableSelect();
    // 绑定多值输入框事件
    initMultipleInputTags();
    // 绑定删除表关系点击事件
    bindMappingTableGroupDelClick();
    // 绑定重新同步按钮点击事件
    bindMappingTableGroupResyncClick();
    //binding刷新数据表按钮点击事件
    bindRefreshTablesClick();

    // 绑定下拉过滤按钮点击事件
    bindMultipleSelectFilterBtnClick();

    // 绑定编辑页面操作按钮事件
    bindEditPageOperationButtons();

    // 绑定数据源详情按钮点击事件
    bindConnectorDetailBtnClick();

    // 初始化批量转换配置功能
    initBatchConvertConfig();

    // 页面加载完成后自动触发字段差异检查（延迟执行确保DOM已渲染）
    // 传入true表示先刷新字段元数据，再检测差异，确保结果准确
    setTimeout(function() {
        checkFieldDifferencesForTableGroups(true);
    }, 500);

    // 返回
    $("#mappingBackBtn").click(function () {
         //停止轮询
         mappingPoller.stop();
         backIndexPage();
    });

    // 不再监听 window 的 beforeunload/pagehide 来清定时器：
    // window 是常驻外壳，每次进入编辑页都会多叠一层处理器（既有泄漏），
    // 而轮询已由页面代际守卫自我销毁（ADR-0032），真实卸载时 JS 状态本身也会全部销毁。

})

// 监听标签页切换事件，当切换到错误队列标签时初始化数据
$(function() {
    $('a[data-toggle="tab"]').on('shown.bs.tab', function (e) {
        var target = $(e.target).attr('href');
        if (target === '#mappingErrorQueue') {
            initErrorQueue();
        }
        if (target === '#mappingLog' && currentMappingId) {
            doQueryLog(1);
        }
    });
});

//# sourceURL=/js/mapping/edit.js
