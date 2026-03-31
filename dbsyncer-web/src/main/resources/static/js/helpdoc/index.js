let currentDocName = null;
let isAdmin = false;

$(function () {
    isAdmin = $('#isAdmin').val() === 'true';
    loadDocList();
    if (isAdmin) {
        initEditorToolbar();
    }
    bindEvents();
});

function loadDocList() {
    doGetter('/helpDoc/list.json', {}, function (data) {
        if (data.success) {
            renderDocList(data.resultValue);
        } else {
            bootGrowl(data.resultValue, 'danger');
        }
    });
}

function renderDocList(docs) {
    const $docList = $('#docList');
    $docList.empty();
    
    if (docs.length === 0) {
        $docList.append('<li class="list-group-item text-muted text-center">暂无文档</li>');
        return;
    }
    
    docs.forEach(function (doc) {
        const size = formatFileSize(doc.size);
        const date = formatDate(doc.lastModified);
        const item = '<li class="list-group-item doc-item" data-filename="' + doc.fileName + '">' +
            '<i class="fa fa-file-text-o"></i> ' + doc.fileName +
            '<br><small class="text-muted">' + size + ' | ' + date + '</small>' +
            '</li>';
        $docList.append(item);
    });
    
    $('.doc-item').click(function () {
        const fileName = $(this).data('filename');
        loadDoc(fileName);
        $('.doc-item').removeClass('active');
        $(this).addClass('active');
    });
}

function loadDoc(fileName) {
    doGetter('/helpDoc/get.json', {fileName: fileName}, function (data) {
        if (data.success) {
            currentDocName = data.resultValue.fileName;
            $('#editorContent').html(data.resultValue.content);
            $('#currentDocTitle').text(currentDocName);
            $('#editorContainer').removeClass('hidden');
            $('#noDocSelected').addClass('hidden');
            if (isAdmin) {
                $('#docActionBtns').removeClass('hidden');
            }
        } else {
            bootGrowl(data.resultValue, 'danger');
        }
    });
}

function initEditorToolbar() {
    $('#editorToolbar button[data-command]').click(function () {
        const command = $(this).data('command');
        const value = $(this).data('value') || null;
        document.execCommand(command, false, value);
        $('#editorContent').focus();
    });
    
    $('#insertImageBtn').click(function () {
        $('#imageUploadInput').click();
    });
    
    $('#imageUploadInput').change(function () {
        const file = this.files[0];
        if (file) {
            uploadImage(file);
        }
        this.value = '';
    });
}

function uploadImage(file) {
    const formData = new FormData();
    formData.append('file', file);
    
    $.ajax({
        url: $basePath + '/helpDoc/uploadImage.json',
        type: 'POST',
        data: formData,
        processData: false,
        contentType: false,
        success: function (data) {
            if (data.success) {
                const imgHtml = '<img src="/helpdocs/' + data.resultValue.url + '" style="max-width: 100%;">';
                document.execCommand('insertHTML', false, imgHtml);
            } else {
                bootGrowl(data.resultValue, 'danger');
            }
        },
        error: function () {
            bootGrowl('上传图片失败', 'danger');
        }
    });
}

function bindEvents() {
    $('#createDocBtn').click(function () {
        BootstrapDialog.show({
            title: '新建文档',
            type: BootstrapDialog.TYPE_INFO,
            message: '<div class="form-group">' +
                '<label>文档名称</label>' +
                '<input type="text" class="form-control" id="newDocName" placeholder="请输入文档名称">' +
                '</div>',
            size: BootstrapDialog.SIZE_NORMAL,
            buttons: [{
                label: '确定',
                action: function (dialog) {
                    const fileName = $('#newDocName').val().trim();
                    if (!fileName) {
                        bootGrowl('请输入文档名称', 'danger');
                        return;
                    }
                    doPoster('/helpDoc/create.json', {fileName: fileName}, function (data) {
                        if (data.success) {
                            bootGrowl('创建成功', 'success');
                            loadDocList();
                            loadDoc(data.resultValue);
                        } else {
                            bootGrowl(data.resultValue, 'danger');
                        }
                    });
                    dialog.close();
                }
            }, {
                label: '取消',
                action: function (dialog) {
                    dialog.close();
                }
            }]
        });
    });
    
    $('#saveDocBtn').click(function () {
        if (!currentDocName) {
            bootGrowl('请先选择文档', 'danger');
            return;
        }
        const content = $('#editorContent').html();
        doPoster('/helpDoc/save.json', {fileName: currentDocName, content: content}, function (data) {
            if (data.success) {
                bootGrowl('保存成功', 'success');
                loadDocList();
            } else {
                bootGrowl(data.resultValue, 'danger');
            }
        });
    });
    
    $('#renameDocBtn').click(function () {
        if (!currentDocName) {
            bootGrowl('请先选择文档', 'danger');
            return;
        }
        const oldName = currentDocName.replace(/\.(html|md)$/, '');
        BootstrapDialog.show({
            title: '重命名文档',
            type: BootstrapDialog.TYPE_INFO,
            message: '<div class="form-group">' +
                '<label>新文档名称</label>' +
                '<input type="text" class="form-control" id="renameDocName" value="' + oldName + '">' +
                '</div>',
            size: BootstrapDialog.SIZE_NORMAL,
            buttons: [{
                label: '确定',
                action: function (dialog) {
                    const newName = $('#renameDocName').val().trim();
                    if (!newName) {
                        bootGrowl('请输入文档名称', 'danger');
                        return;
                    }
                    doPoster('/helpDoc/rename.json', {oldName: currentDocName, newName: newName}, function (data) {
                        if (data.success) {
                            bootGrowl('重命名成功', 'success');
                            loadDocList();
                            if (data.resultValue) {
                                currentDocName = data.resultValue;
                                $('#currentDocTitle').text(currentDocName);
                            }
                        } else {
                            bootGrowl(data.resultValue, 'danger');
                        }
                    });
                    dialog.close();
                }
            }, {
                label: '取消',
                action: function (dialog) {
                    dialog.close();
                }
            }]
        });
    });
    
    $('#deleteDocBtn').click(function () {
        if (!currentDocName) {
            bootGrowl('请先选择文档', 'danger');
            return;
        }
        BootstrapDialog.show({
            title: '删除文档',
            type: BootstrapDialog.TYPE_DANGER,
            message: '确定要删除文档 "' + currentDocName + '" 吗？',
            size: BootstrapDialog.SIZE_NORMAL,
            buttons: [{
                label: '确定',
                action: function (dialog) {
                    doPoster('/helpDoc/delete.json', {fileName: currentDocName}, function (data) {
                        if (data.success) {
                            bootGrowl('删除成功', 'success');
                            currentDocName = null;
                            $('#currentDocTitle').text('请选择文档');
                            $('#editorContainer').addClass('hidden');
                            $('#noDocSelected').removeClass('hidden');
                            $('#docActionBtns').addClass('hidden');
                            loadDocList();
                        } else {
                            bootGrowl(data.resultValue, 'danger');
                        }
                    });
                    dialog.close();
                }
            }, {
                label: '取消',
                action: function (dialog) {
                    dialog.close();
                }
            }]
        });
    });
}

function formatFileSize(bytes) {
    if (bytes < 1024) {
        return bytes + ' B';
    } else if (bytes < 1024 * 1024) {
        return (bytes / 1024).toFixed(2) + ' KB';
    } else {
        return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
    }
}

function formatDate(timestamp) {
    const date = new Date(timestamp);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return year + '-' + month + '-' + day + ' ' + hours + ':' + minutes;
}
