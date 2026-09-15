/**
 * 数据源页面
 * 使用 TaskPanel 组件渲染关联任务列表
 */
(function ($) {
    'use strict';

    var relatedTaskPanel = null;
    var currentConnectorId = null;

    function init() {
        bindAddConnector();
        bindConnectorSearch();
        loadConnectorList();
    }

    function bindConnectorSearch() {
        $('#searchBtn').click(function() { loadConnectorList(); });
        $('#searchInput').keypress(function(e) {
            if (e.which === 13) loadConnectorList();
        });
        $('#searchType').change(function() { loadConnectorList(); });
    }

    function loadConnectorList() {
        var searchType = $('#searchType').val();
        var keyword = $('#searchInput').val().trim();

        $.ajax({
            url: '/datasource/list',
            type: 'POST',
            data: { searchType: searchType, keyword: keyword },
            dataType: 'json',
            success: function(data) {
                if (data.success === true) {
                    renderConnectorList(data.resultValue);
                } else {
                    showError('加载数据源列表失败：' + data.resultValue);
                }
            },
            error: function() { showError('加载数据源列表失败'); }
        });
    }

    function renderConnectorList(connectors) {
        var $connectorList = $('#connector-list');
        $connectorList.empty();

        if (!connectors || connectors.length === 0) {
            $connectorList.append('<div class="empty-message">暂无数据源</div>');
            return;
        }

        connectors.forEach(function(connector) {
            var dataSourceType = connector.config && connector.config.connectorType ? connector.config.connectorType : 'default';
            var imgPath = '/img/datasource/' + dataSourceType + '.png';
            var html = '<div class="connector-item" data-id="' + connector.id + '">' +
                '    <div class="connector-icon">' +
                '        <img src="' + imgPath + '" alt="' + dataSourceType + '" onerror="this.onerror=null;this.outerHTML=\'<i class=\\\"fa fa-database\\\"></i>\';" />' +
                '    </div>' +
                '    <div class="connector-info">' +
                '        <div class="connector-name">' + escapeHtml(connector.name) + '</div>' +
                '    </div>' +
                '    <div class="connector-actions">' +
                '        <div class="dropdown">' +
                '            <button class="btn btn-default dropdown-toggle" type="button" data-toggle="dropdown">' +
                '                <i class="fa fa-ellipsis-v"></i>' +
                '            </button>' +
                '            <ul class="dropdown-menu">' +
                '                <li><a href="javascript:;" class="edit-connector">编辑</a></li>' +
                '                <li><a href="javascript:;" class="copy-connector">复制</a></li>' +
                '                <li><a href="javascript:;" class="delete-connector">删除</a></li>' +
                '            </ul>' +
                '        </div>' +
                '    </div>' +
                '</div>';

            var $item = $(html);
            $connectorList.append($item);

            $item.click(function() {
                selectConnector($(this).data('id'));
            });

            $item.find('.connector-actions').click(function(e) { e.stopPropagation(); });

            $item.find('.dropdown-toggle').click(function(e) {
                e.stopPropagation();
                $('.dropdown-menu.show').removeClass('show');
                $(this).next('.dropdown-menu').toggleClass('show');
            });

            $item.find('.edit-connector').click(function() {
                bindEditConnector($item.data('id'));
                $item.find('.dropdown-menu').removeClass('show');
            });

            $item.find('.copy-connector').click(function() {
                copyConnector($item.data('id'));
                $item.find('.dropdown-menu').removeClass('show');
            });

            $item.find('.delete-connector').click(function() {
                deleteConnector($item.data('id'));
                $item.find('.dropdown-menu').removeClass('show');
            });
        });
    }

    function selectConnector(connectorId) {
        $('.connector-item').removeClass('selected');
        $('.connector-item[data-id="' + connectorId + '"]').addClass('selected');
        loadConnectorDetail(connectorId);
    }

    function loadConnectorDetail(connectorId) {
        $.ajax({
            url: '/datasource/connector/get?id=' + connectorId,
            type: 'GET',
            dataType: 'json',
            success: function(data) {
                if (data.success === true) {
                    renderConnectorDetail(data.resultValue);
                } else {
                    showError('加载数据源详情失败：' + data.resultValue);
                }
            },
            error: function() { showError('加载数据源详情失败'); }
        });
    }

    function renderConnectorDetail(connector) {
        var $detail = $('#connector-detail');
        $detail.empty();

        var html = '<div class="connector-detail-header"><h3>数据源详情</h3></div>' +
            '<div class="connector-detail-content">' +
            '<div class="connector-details-info">' +
            '<input type="hidden" id="connector-id" value="' + connector.id + '">' +
            '<div class="detail-row"><span class="detail-label">数据源名称</span><span class="detail-value">' + escapeHtml(connector.name) + '</span></div>' +
            '<div class="detail-row"><span class="detail-label">数据源类型</span><span class="detail-value">' + escapeHtml(connector.type) + '</span></div>';

        if (connector.config && typeof connector.config === 'object') {
            for (var key in connector.config) {
                if (connector.config.hasOwnProperty(key) && key !== 'password') {
                    html += '<div class="detail-row"><span class="detail-label">' + getConfigLabel(key) + '</span><span class="detail-value">' + escapeHtml(connector.config[key]) + '</span></div>';
                }
            }
        }

        html += '</div></div>';
        $detail.html(html);

        loadRelatedDrivers(connector.id);
    }

    function loadRelatedDrivers(connectorId) {
        currentConnectorId = connectorId;

        if (relatedTaskPanel) {
            relatedTaskPanel.destroy();
        }

        relatedTaskPanel = new TaskPanel();
        relatedTaskPanel.init('#drivers-list', {
            enableCreate: false,
            enableCheckbox: true,
            enableBatchStart: true,
            enableBatchStop: true,
            emptyMessage: '暂无关联任务',
            onRefresh: function() {
                loadRelatedDrivers(currentConnectorId);
            }
        });

        $.loadingT(true);
        $.ajax({
            url: '/mapping/getRelatedMappings?connectorId=' + connectorId,
            type: 'GET',
            dataType: 'json',
            success: function(data) {
                $.loadingT(false);
                if (data.success === true) {
                    var drivers = data.resultValue || [];
                    $('.drivers-count').text('共 ' + drivers.length + ' 个任务');
                    relatedTaskPanel.setData(drivers);

                    var hasRunning = drivers.some(function(d) { return d.meta && d.meta.state === 1; });
                    updateEditButtonState(hasRunning);
                } else {
                    showError('加载关联任务失败：' + data.resultValue);
                    relatedTaskPanel.setData([]);
                    updateEditButtonState(false);
                }
            },
            error: function() {
                $.loadingT(false);
                showError('加载关联任务失败');
                relatedTaskPanel.setData([]);
                updateEditButtonState(false);
            }
        });
    }

    function updateEditButtonState(hasRunningTask) {
        var $connectorId = $('#connector-id');
        if (!$connectorId.length) return;

        var currentId = $connectorId.val();
        var $connectorItem = $('.connector-item[data-id="' + currentId + '"]');
        var $editBtn = $connectorItem.find('.edit-connector');

        if (hasRunningTask) {
            $editBtn.addClass('disabled').css({
                'color': '#999',
                'cursor': 'not-allowed',
                'pointer-events': 'none'
            }).attr('title', '存在运行中的任务，无法编辑');
        } else {
            $editBtn.removeClass('disabled').css({
                'color': '',
                'cursor': '',
                'pointer-events': ''
            }).removeAttr('title');
        }
    }

    function getConfigLabel(key) {
        var labels = {
            'username': '用户名',
            'password': '密码',
            'url': '数据源URL',
            'host': '主机',
            'port': '端口',
            'database': '数据库名',
            'driverClassName': '驱动类名'
        };
        return labels[key] || key;
    }

    function bindAddConnector() {
        $('#add-connector').click(function() {
            doLoader('/connector/page/add');
        });
    }

    function bindEditConnector(connectorId) {
        doLoader('/connector/page/edit?id=' + connectorId);
    }

    function copyConnector(connectorId) {
        if (confirm('确定要复制该数据源吗？')) {
            $.ajax({
                url: '/connector/copy',
                type: 'POST',
                data: { id: connectorId },
                success: function(result) {
                    if (result.success === true) {
                        showSuccess('复制成功');
                        loadConnectorList();
                    } else {
                        showError('复制失败：' + result.resultValue);
                    }
                },
                error: function() { showError('复制失败'); }
            });
        }
    }

    function deleteConnector(connectorId) {
        if (confirm('确定要删除该数据源吗？删除后将无法恢复。')) {
            $.ajax({
                url: '/connector/remove',
                type: 'POST',
                data: { id: connectorId },
                success: function(result) {
                    if (result.success === true) {
                        showSuccess('删除成功');
                        loadConnectorList();
                        $('#connector-detail').html('<div class="no-connector-selected"><p>请从左侧选择一个数据源或点击"添加数据源"按钮</p></div>');
                    } else {
                        showError('删除失败：' + result.resultValue);
                    }
                },
                error: function() { showError('删除失败'); }
            });
        }
    }

    function escapeHtml(str) {
        if (!str) return '';
        var div = document.createElement('div');
        div.appendChild(document.createTextNode(str));
        return div.innerHTML;
    }

    function showSuccess(message) {
        alert(message || '操作成功');
    }

    function showError(message) {
        alert(message || '操作失败');
    }

    $(function() { init(); });

})(jQuery);
