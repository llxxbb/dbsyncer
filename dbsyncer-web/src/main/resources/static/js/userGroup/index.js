$(function () {
    // 绑定添加用户组按钮事件
    $("#addUserGroupBtn").click(function () {
        doLoader("/userGroup/page/add");
    });

    // 绑定修改用户组按钮事件
    $(".editUserGroupBtn").click(function () {
        var id = $(this).attr("id");
        doLoader("/userGroup/page/edit?id=" + id);
    });

    // 绑定删除用户组按钮事件
    $(".removeUserGroupBtn").click(function () {
        const $id = $(this).attr("id");
        // 确认框确认是否删除用户组
        BootstrapDialog.show({
            title: "提示",
            type: BootstrapDialog.TYPE_INFO,
            message: "确认删除该用户组？",
            size: BootstrapDialog.SIZE_NORMAL,
            buttons: [{
                label: "确定",
                action: function (dialog) {
                    doPoster('/userGroup/remove', {id: $id}, function (data) {
                        if (data.success == true) {
                            bootGrowl("删除用户组成功！", "success");
                        } else {
                            bootGrowl(data.resultValue, "danger");
                        }
                        doLoader("/userGroup?refresh=" + new Date().getTime());
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

});
