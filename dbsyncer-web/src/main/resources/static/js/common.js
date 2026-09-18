// 获取项目地址
var $location = (window.location + '').split('/');
var $path = document.location.pathname;
var $basePath = $location[0] + '//' + $location[2] + $path.substr(0, $path.substr(1).indexOf("/") + 1);
// ******************* 插件封装 ***************************
// 全局提示框
function bootGrowl(data, type) {
    $.bootstrapGrowl(data, { // data为提示信息
        type: type == undefined ? 'success' : type,// type指提示类型
        delay: 3000,// 提示框显示时间
        allow_dismiss: true // 显示取消提示框
    });
}

// 刷新登录用户信息
function refreshLoginUser() {
    // 获取登录用户信息
    doGetter("/user/getUserInfo.json", {}, function (data) {
        if (data.success == true) {
            $("#currentUser").html(data.resultValue.nickname + " (" + data.resultValue.roleName + ")");
            $("#edit_personal").attr("username", data.resultValue.username);
        }
    });
}

// 刷新授权信息
function refreshLicenseInfo() {
    $(".license-info-item").addClass("hidden");
    // 获取登录用户信息
    doGetter("/license/query.json", {}, function (data) {
        if (data.success == true) {
            // 社区版
            if (isBlank(data.resultValue.key)) {
                return;
            }
            // 专业版
            $("#licenseInfo").removeClass("hidden");
            const $content = $("#effectiveContent");
            const $effectiveTime = data.resultValue.effectiveTime;
            if ($effectiveTime <= 0) {
                $content.html("<span style=\"color:red\"><b>未激活</b></span>");
                $content.removeClass("hidden");
                return;
            }
            const $currentTime = data.resultValue.currentTime;
            const $10days = 864000000;
            // 有效期内
            if ($currentTime < $effectiveTime && $effectiveTime - $10days > $currentTime) {
                $("#licenseCheck").removeClass("hidden");
            }
            // 即将过期
            else if ($currentTime < $effectiveTime && $effectiveTime - $10days <= $currentTime) {
                $("#licenseRemind").removeClass("hidden");
            }
            // 已过期
            else if ($currentTime > $effectiveTime) {
                $("#licenseWarning").removeClass("hidden");
            }
            $content.html("&nbsp;" + data.resultValue.effectiveContent);
            $content.removeClass("hidden");
        }
    });
}

// 跳转主页或返回上一页
function backIndexPage(projectGroupId, forceToIndex) {
    // 如果强制返回主页，则跳转到主页
    if (forceToIndex === true) {
        // 如果没有传递projectGroupId参数，则从页面的隐藏字段中获取当前分组ID
        if (typeof projectGroupId !== 'string' || projectGroupId === '') {
            projectGroupId = $('#projectGroup').val() || '';
        }
        // 使用updateHash更新URL哈希，而不是直接调用doLoader
        updateHash("/index?projectGroupId=" + projectGroupId + "&refresh=" + new Date().getTime());

         // 设置导航栏活动状态为驱动
        var $menu = $('#menu > li');
        $menu.removeClass('active');
        $menu.eq(0).addClass('active');
        return;
    }
    
    // 尝试从 sessionStorage 获取来源页面
    const previousPage = sessionStorage.getItem('previousPage');
    
    if (previousPage) {
        // 清除来源页面记录
        sessionStorage.removeItem('previousPage');
        // 跳转到来源页面
        updateHash(previousPage);
    } else {
        // 如果没有来源页面，则返回主页
        backIndexPage(projectGroupId, true);
    }
}

// 美化SQL
function beautifySql() {
    var $sql = $(".sql");
    var $tmp = $sql.attr('tmp');
    if (null == $tmp) {
        $sql.attr('tmp', $sql.val());
        $sql.val(sqlFormatter.format($sql.val()));
        return;
    }
    $sql.val($tmp);
    $sql.removeAttr('tmp');
}

// 初始化select组件，默认选中
function initSelectIndex($select, $selectedIndex) {
    let select = initSelect($select);

    if ($selectedIndex < 0) {
        return select;
    }

    $.each($select, function () {
        var v = $(this).selectpicker('val');
        if (undefined == v || '' == v) {
            var $option = $(this).find("option")[$selectedIndex];
            if (undefined != $option) {
                $(this).selectpicker('val', $option.value);
            }
        }
    });
    return select;
}

function initSelect($select) {
    return $select.selectpicker({
        "title": "请选择",
        "actionsBox": true,
        "liveSearch": true,
        "selectAllText": "全选",
        "deselectAllText": "取消全选",
        "noneResultsText": "没有找到 {0}",
        "selectedTextFormat": "count > 10"
    });
}

function isValidEmail(email) {
    var emailRegex = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
    return emailRegex.test(email);
}

function isValidPhone(phone) {
    var phoneRegex = /^1[3-9]\d{9}$/;
    return phoneRegex.test(phone);
}

function initMultipleInputTags() {
    $("input[data-role=tagsinput]").tagsinput({
        maxChars: 32,
        maxTags: 5,
        tagClass: 'label label-success',
        trimValue: true
    });
}

function initEmailTagsInput() {
    var $emailInput = $("input[data-role=tagsinput][name='email']");
    if ($emailInput.length === 0) return;
    
    $emailInput.each(function() {
        var $this = $(this);
        $this.tagsinput({
            maxChars: 64,
            maxTags: 5,
            tagClass: function(item) {
                return isValidEmail(item) ? 'label label-success' : 'label label-danger';
            },
            trimValue: true
        });
        
        $this.on('beforeItemAdd', function(event) {
            var email = event.item;
            if (!isValidEmail(email)) {
                event.cancel = true;
                bootGrowl('邮箱格式不正确: ' + email, 'danger');
            }
        });
    });
}

function initPhoneValidation() {
    var $phoneInput = $("input[name='phone']");
    if ($phoneInput.length === 0) return;
    
    $phoneInput.each(function() {
        var $this = $(this);
        $this.on('blur', function() {
            var phone = $this.val().trim();
            if (phone && !isValidPhone(phone)) {
                $this.addClass('dbsyncer_valid_error')
                     .attr("data-original-title", "请输入正确的手机号格式")
                     .tooltip({trigger: 'manual'})
                     .tooltip('show');
            } else {
                $this.tooltip('hide').removeClass('dbsyncer_valid_error');
            }
        });
        
        $this.on('input', function() {
            var phone = $this.val().trim();
            if (phone && !isValidPhone(phone)) {
                $this.addClass('dbsyncer_valid_error')
                     .attr("data-original-title", "请输入正确的手机号格式")
                     .tooltip({trigger: 'manual'})
                     .tooltip('show');
            } else {
                $this.tooltip('hide').removeClass('dbsyncer_valid_error');
            }
        });
    });
}

// 初始化开关
function initSwitch() {
    $('.dbsyncer_switch').bootstrapSwitch({
        onText: "Yes",
        offText: "No",
        onColor: "success",
        offColor: "info",
        size: "normal"
    });
}

// ******************* 水印 ***************************
window.onresize = function () {
    watermark();
}
window.onscroll = function () {
    watermark();
}
//水印样式默认设置
const settings = {
    watermark_txt: "",
    watermark_x: 50,//水印起始位置x轴坐标
    watermark_y: 55,//水印起始位置Y轴坐标
    watermark_rows: 2000,//水印行数
    watermark_cols: 2000,//水印列数
    watermark_x_space: 70,//水印x轴间隔
    watermark_y_space: 30,//水印y轴间隔
    watermark_color: '#aaaaaa',//水印字体颜色
    watermark_alpha: 0.2,//水印透明度
    watermark_fontsize: '15px',//水印字体大小
    watermark_font: '微软雅黑',//水印字体
    watermark_width: 210,//水印宽度
    watermark_height: 80,//水印长度
    watermark_angle: 15//水印倾斜度数
};
let timestampWatermark;

function watermark() {
    const now = Date.now();
    if (timestampWatermark != null && now - timestampWatermark < 200) {
        return;
    }
    if (isBlank(settings.watermark_txt)) {
        return;
    }
    timestampWatermark = now;
    $(".dbsyncer_mask").remove();

    const water = document.body;
    //获取页面最大宽度
    const page_width = Math.max(water.scrollWidth, water.clientWidth);
    //获取页面最大高度
    const page_height = Math.max(water.scrollHeight, water.clientHeight);
    //水印数量自适应元素区域尺寸
    settings.watermark_cols = Math.ceil(page_width / (settings.watermark_x_space + settings.watermark_width));
    settings.watermark_rows = Math.ceil(page_height / (settings.watermark_y_space + settings.watermark_height));
    let x;
    let y;
    for (let i = 0; i < settings.watermark_rows; i++) {
        y = settings.watermark_y + (settings.watermark_y_space + settings.watermark_height) * i;
        for (let j = 0; j < settings.watermark_cols; j++) {
            x = settings.watermark_x + (settings.watermark_width + settings.watermark_x_space) * j;
            let mask_div = document.createElement('div');
            mask_div.className = 'dbsyncer_mask';
            mask_div.innerHTML = (settings.watermark_txt);
            mask_div.style.webkitTransform = "rotate(-" + settings.watermark_angle + "deg)";
            mask_div.style.MozTransform = "rotate(-" + settings.watermark_angle + "deg)";
            mask_div.style.msTransform = "rotate(-" + settings.watermark_angle + "deg)";
            mask_div.style.OTransform = "rotate(-" + settings.watermark_angle + "deg)";
            mask_div.style.transform = "rotate(-" + settings.watermark_angle + "deg)";
            mask_div.style.position = "absolute";
            mask_div.style.left = x + 'px';
            mask_div.style.top = y + 'px';
            mask_div.style.overflow = "hidden";
            mask_div.style.pointerEvents = 'none';
            mask_div.style.opacity = settings.watermark_alpha;
            mask_div.style.fontSize = settings.watermark_fontsize;
            mask_div.style.fontFamily = settings.watermark_font;
            mask_div.style.color = settings.watermark_color;
            mask_div.style.textAlign = "center";
            mask_div.style.width = settings.watermark_width + 'px';
            mask_div.style.height = settings.watermark_height + 'px';
            mask_div.style.display = "block";
            water.appendChild(mask_div);
        }
    }
}

// ******************* 扩展JS表单方法 ***************************
$.fn.serializeJson = function () {
    var o = {};
    var a = this.serializeArray();
    $.each(a, function () {
        if (o[this.name] !== undefined) {
            if (!o[this.name].push) {
                o[this.name] = [o[this.name]];
            }
            o[this.name].push(this.value || '');
        } else {
            o[this.name] = this.value || '';
        }
    });
    return o;
};

// ******************* 字符串工具（ADR-0008 大小写不敏感）*******************
//
// 业务代码禁止直接调用 .toLowerCase() / .toUpperCase() / 手写大小写无关比较：
// 这两个方法跟随宿主默认 locale，而土耳其语等 locale 下 ASCII 的 'I'/'i' 折叠结果不同
// （'I'.toLowerCase() === '\u0131'）。SQL 类型名、连接器类型、字段名都是 ASCII 标识符，
// 一旦折叠错就会静默查表失败或漏配主键，很难排查。
// 下面这几个函数是前端唯一的入口，语义与 Java 侧
// org.dbsyncer.common.util.StringUtil#equalsIgnoreCase 对齐（两侧皆空为 true，单侧为空为 false）。

/** ASCII 大小写位移：offset 为 +32（转小写）或 -32（转大写）。不使用任何 locale 相关 API。 */
function foldAsciiCase(text, offset) {
    var from = offset > 0 ? 65 : 97;   // 'A' 或 'a'
    var to = offset > 0 ? 90 : 122;    // 'Z' 或 'z'
    var out = '';
    for (var i = 0; i < text.length; i++) {
        var code = text.charCodeAt(i);
        out += (code >= from && code <= to) ? String.fromCharCode(code + offset) : text.charAt(i);
    }
    return out;
}

/**
 * 折叠为小写。null/undefined 返回空串。
 * ASCII 段先做确定性位移（不依赖 locale），非 ASCII 段再交给带显式 locale 的
 * toLocaleLowerCase；老浏览器忽略 locale 参数、退化为默认 locale 时，
 * 会出问题的 ASCII 'I'/'i' 也已经在上一步处理完了。
 */
function toLowerCaseInvariant(value) {
    if (value === null || value === undefined) {
        return '';
    }
    return foldAsciiCase(String(value), 32).toLocaleLowerCase('en-US');
}

/** 折叠为大写。规则同 toLowerCaseInvariant。 */
function toUpperCaseInvariant(value) {
    if (value === null || value === undefined) {
        return '';
    }
    return foldAsciiCase(String(value), -32).toLocaleUpperCase('en-US');
}

/** 大小写无关比较，null/undefined 安全。字段名、表名、主键名比较一律走这里。 */
function equalsIgnoreCase(a, b) {
    if (a === b) {
        return true;                   // 含两侧同为 null/undefined
    }
    if (a === null || a === undefined || b === null || b === undefined) {
        return false;
    }
    return toLowerCaseInvariant(a) === toLowerCaseInvariant(b);
}

/**
 * 列表中是否存在与 name 大小写无关相等的元素。
 * 对应后端 TableGroup#containsPrimaryKey（ADR-0008 把主键判重收敛到业务对象层）。
 */
function containsIgnoreCase(list, name) {
    if (!list || !list.length) {
        return false;
    }
    for (var i = 0; i < list.length; i++) {
        if (equalsIgnoreCase(list[i], name)) {
            return true;
        }
    }
    return false;
}

// ==================== SPA 页面加载（ADR-0032）====================
//
// 设计约束：页面之间不共享任何状态。每次导航都会
//   1) 递增全局代际号 window.__pageEpoch，使上一个页面尚未返回的异步回调自动失效；
//   2) 中断上一个页面尚未完成的容器请求；
//   3) 用新页面的完整 HTML 覆盖唯一内容容器 #initContainer。
//
// 因此不存在“旧页面 DOM 残留”或“旧回调写入新页面”的问题，
// 也无需遮罩引用计数、过期响应序号表或页面清理函数注册表。

// 页面代际号：每次加载新页面 +1
window.__pageEpoch = 0;

/**
 * 取当前页面代际号。页面脚本应在启动时调用一次并保存返回值，
 * 之后在每个异步回调里用 isPageAlive() 比对，即可判断自己是否已被新页面替换。
 */
function pageGuard() {
    return window.__pageEpoch;
}

/** 判断给定代际号是否仍然有效（即该页面仍是当前页面）。 */
function isPageAlive(guard) {
    return guard === window.__pageEpoch;
}

// 唯一内容容器
var $initContainer = $('#initContainer');
$initContainer.css('min-height', $(window).height() - 125);

// 容器加载请求句柄，用于在快速切换时中断上一个页面的加载
var containerXHR = null;

/**
 * 更新 URL 哈希值。
 * @param url 页面相对路径（可带查询参数）
 * @returns {boolean} 哈希是否发生变化。变化时由 hashchange 负责加载，调用方无需重复加载。
 */
function updateHash(url) {
    const hashValue = '#' + url;

    // 如果当前URL不是编辑页面，则记录为来源页面
    const currentHash = window.location.hash.substring(1);
    if (currentHash && !currentHash.includes('/page/edit') && !currentHash.includes('/pageAdd')) {
        sessionStorage.setItem('previousPage', currentHash);
    }

    // 检查是否需要更新哈希，避免不必要的hashchange事件
    const before = window.location.hash;
    if (before === hashValue) {
        return false;
    }
    window.location.hash = hashValue;
    // 浏览器可能对 hash 做编码归一化，以最终值为准判断是否真的变化
    return window.location.hash !== before;
}

/**
 * 从 URL 中提取 projectGroupId 并同步到隐藏字段。
 * 页面 HTML 写入 DOM 时其脚本已同步执行完毕，因此 Thymeleaf 服务端渲染的
 * #projectGroup 才是页面 init() 的取值来源；此处仅在 URL 显式携带 projectGroupId 时覆盖它。
 */
function syncProjectGroupId(url) {
    var reg = new RegExp('(?:\\?|&)projectGroupId=([^&]*)');
    var match = url.match(reg);
    if (match) {
        $('#projectGroup').val(decodeURIComponent(match[1].replace(/\+/g, ' ')));
    }
}

/**
 * 加载页面到唯一内容容器，不更新哈希。
 * 用于哈希已经变化（hashchange 回调中）或不需要改变地址栏的场景。
 * 响应中的 <script> 会在 HTML 写入 DOM 后同步执行，因此页面脚本可直接调用 init()。
 *
 * @param url 页面相对路径（可带查询参数）
 */
function doLoaderWithoutHashUpdate(url) {
    // 递增代际号：上一个页面所有尚未返回的回调都会因代际不符而自行放弃
    window.__pageEpoch++;
    // 中断上一个页面的容器加载，避免其响应覆盖当前页面
    if (containerXHR) {
        containerXHR.abort();
        containerXHR = null;
    }
    $.loadingT(true);

    var xhr = $.ajax({
        url: $basePath + url,
        type: 'GET',
        dataType: 'html'
    }).done(function (html) {
        // 内容尚未写入，先关闭导航遮罩。
        // 紧接着 .html() 会同步执行页面脚本，页面若需要自己的加载遮罩（如列表拉数据），
        // 会在那时重新打开并由其自身的 AJAX 回调关闭，不会被这里提前掩盖。
        // 这一步不可省：部分页面（如数据源）不自管理遮罩，否则遮罩会一直挡到 30s 超时。
        $.loadingT(false);
        // 用 .html() 写入：与 jQuery .load() 等价，同样会执行响应中的 <script>
        $initContainer.html(html);
        syncProjectGroupId(url);
    }).fail(function (resp, status) {
        // 主动中断的请求属于正常流程：遮罩已由新一次导航接管，不能在这里关闭
        if (status === 'abort') {
            return;
        }
        $.loadingT(false);
        bootGrowl((resp.responseText || '页面加载失败'), 'danger');
    }).always(function () {
        // 只有当句柄仍属于本次请求时才清除；
        // 否则旧请求的迟到回调会把新请求的句柄抹掉，导致新请求再也无法被中断
        if (containerXHR === xhr) {
            containerXHR = null;
        }
        try {
            watermark();
        } catch (e) {
            bootGrowl('页面初始化异常：' + (e.message || e), 'danger');
        }
    });
    containerXHR = xhr;
}

/**
 * 加载页面并更新哈希。
 * 若哈希确实变化，浏览器会异步触发 hashchange 并由 handleHashChange 完成加载，
 * 此处直接返回，避免同一页面被重复加载（脚本重复执行会导致定时器与请求翻倍）。
 */
function doLoader(url) {
    if (updateHash(url)) {
        return;
    }
    doLoaderWithoutHashUpdate(url);
}

// 异常请求
function doRequest(action, data) {
    $.loadingT(false);
    // 异常请求：302
    if (!(data instanceof Object)) {
        bootGrowl("会话过期, 3秒后将访问登录主页...", "danger");
        setTimeout(function () {
            location.replace($basePath); // 修改为replace
        }, 3000);
    } else {
        action(data);
    }
}

// 异常响应
function doErrorResponse(xhr, status, info) {
    $.loadingT(false);
    bootGrowl("访问异常，请刷新或重试.", "danger");
}

// 全局Ajax post
function doPoster(url, params, action) {
    $.loadingT(true);
    $.post($basePath + url, params, function (data) {
        doRequest(action, data);
    }).error(function (xhr, status, info) {
        doErrorResponse(xhr, status, info);
    });
}

// 全局Ajax get
function doGetter(url, params, action, loading) {
    if (loading == undefined || loading == true) {
        $.loadingT(true);
    }
    $.get($basePath + url, params, function (data) {
        doRequest(action, data);
    }).error(function (xhr, status, info) {
        doErrorResponse(xhr, status, info);
    });
}

// 全局Ajax get, 不显示加载动画
function doGetWithoutLoading(url, params, action) {
    doGetter(url, params, action, false);
}

/**
 * 判断字符串是否为空串
 * @eg undefined true
 * @eg null true
 * @eg '' true
 * @eg ' ' true
 * @eg '1' false
 * @return Boolean
 */
function isBlank(str) {
    return str === undefined || str === null || str === false || str.length === 0;
}

/**
 * 按照指定分隔符切分字符串
 *
 * @param str 带切分字符
 * @param delimiter 分隔符
 * @return Array
 */
function splitStrByDelimiter(str, delimiter) {
    return isBlank(str) ? [] : str.split(delimiter);
}