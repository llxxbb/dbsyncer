// ******************* 初始化 *****************************
// 默认绑定菜单事件
$(function () {
    // 刷新登录用户
    refreshLoginUser();
    // 刷新授权信息
    refreshLicenseInfo();

    // 初始化版权信息
    doGetter("/index/version.json", {}, function (data) {
        if (data.success == true) {
            // 获取底部版权信息
            $("#appCopyRight").html(data.resultValue.appCopyRight);
            settings.watermark_txt = data.resultValue.watermark;
            watermark();
        }
    });

    // 修改登录用户
    $("#edit_personal").click(function () {
        updateHash("/user/page/edit?username=" + $(this).attr("username"));
    });

    // 注销
    $("#nav_logout").click(function () {
        // 确认框确认是否注销
        BootstrapDialog.show({
            title: "提示",
            type: BootstrapDialog.TYPE_INFO,
            message: "确认注销帐号？",
            size: BootstrapDialog.SIZE_NORMAL,
            buttons: [{
                label: "确定",
                action: function (dialog) {
                    doPoster("/logout", null, function (data) {
                        location.replace($basePath);
                    });
                    dialog.close();
                }
            }, {
                label: "取消",
                action: function (dialog) {
                    dialog.close();
                }
            }]
        });
    });

    // 绑定所有的菜单链接点击事件，根据不同的URL加载页面
    $("#menu li a[url]").click(function () {
        // 使用updateHash更新URL哈希而不是直接调用doLoader
        updateHash($(this).attr("url"));
        
        // 设置活动菜单项
        const $menu = $("#menu > li");
        $menu.removeClass('active');
        
        // 如果是下拉菜单中的菜单项，给父菜单项添加active类
        const $parentLi = $(this).parent();
        if ($parentLi.parents('.dropdown').length > 0) {
            $parentLi.parents('.dropdown').addClass('active');
        } else {
            $parentLi.addClass('active');
        }
    });

    // 初始化哈希路由
    initHashRouter();
    
    // 如果没有哈希，则显示主页
    if (!window.location.hash || window.location.hash === '#') {
        backIndexPage();
    }
});

// 处理哈希变化事件
function handleHashChange() {
    // 获取当前哈希值
    const hashValue = window.location.hash;
    
    // 如果哈希为空，则返回主页
    if (!hashValue || hashValue === '#') {
        backIndexPage();
        return;
    }
    
    // 去掉 '#' 得到页面 URL（含查询参数）。
    // 所有页面共用唯一内容容器，URL 即页面标识，无需再解析路由编号（ADR-0032）。
    const hashPath = hashValue.substring(1);
    if (!hashPath) {
        backIndexPage();
        return;
    }
    
    // 根据URL路径设置导航栏活动状态
    const $menu = $("#menu > li");
    $menu.removeClass('active');
    
    // 使用data-path精确匹配菜单项
    const urlPath = hashPath.split('?')[0];
    const $matchingLink = $("#menu li a[data-path='" + urlPath + "']");
    
    if ($matchingLink.length > 0) {
        const $matchingLi = $matchingLink.parent();
        // 如果是下拉菜单中的菜单项，也需要给父菜单项添加active类
        if ($matchingLi.parents('.dropdown').length > 0) {
            $matchingLi.parents('.dropdown').addClass('active');
        } else {
            $matchingLi.addClass('active');
        }
    }
    
    // 使用不更新哈希的加载函数，避免循环触发hashchange事件
    doLoaderWithoutHashUpdate(hashPath);
}

// 初始化哈希路由
function initHashRouter() {
    // 添加哈希变化事件监听器
    window.addEventListener('hashchange', handleHashChange, false);
    
    // 初始化时检查当前哈希
    handleHashChange();
}