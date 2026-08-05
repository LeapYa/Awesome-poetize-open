import { clearEditorThemeCache, initEditorTheme } from '@/utils/useEditorTheme';

export function createPluginManagerListState() {
  return {
    loading: false,
    tableData: [],
    currentPluginType: 'mouse_click_effect',
    activePluginKey: ''
  };
}

export function loadPluginListData(vm) {
  vm.loading = true;
  vm.$http.get(vm.$constant.baseURL + '/sysPlugin/listPlugins', { pluginType: vm.currentPluginType }, true)
    .then((res) => {
      vm.tableData = res.data || [];
      vm.loading = false;
    })
    .catch((error) => {
      vm.loading = false;
      vm.$message.error(error.message);
    });

  if (vm.currentPluginType === 'ai_tool') {
    vm.activePluginKey = '';
    return;
  }

  vm.$http.get(vm.$constant.baseURL + '/sysPlugin/getActivePlugin', { pluginType: vm.currentPluginType }, true)
    .then((res) => {
      if (res.data && res.data.enabled) {
        vm.activePluginKey = res.data.pluginKey;
      } else {
        vm.activePluginKey = 'none';
      }
    })
    .catch((error) => {
      console.error(error);
    });
}

export function setActivePlugin(vm, row) {
  vm.$confirm(
    `切换到「${row.pluginName}」会重新生成全站预渲染页面，搜索引擎可能据此重新抓取文章页。确定继续吗？`,
    '操作确认',
    { type: 'warning' }
  ).then(() => {
    vm.$http.post(vm.$constant.baseURL + '/sysPlugin/setActivePlugin', {
      pluginType: vm.currentPluginType,
      pluginKey: row.pluginKey
    }, true).then((res) => {
      if (res.code === 200) {
        vm.$message.success(`已切换为: ${row.pluginName}`);
        vm.activePluginKey = row.pluginKey;
        if (vm.currentPluginType === 'article_theme') {
          clearEditorThemeCache();
          initEditorTheme();
        }
        if (vm.currentPluginType === 'editor') {
          try {
            window.localStorage.setItem('activeEditorPluginKey', row.pluginKey);
          } catch (error) {}
          if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
            window.dispatchEvent(new CustomEvent('editor-plugin-changed', {
              detail: {
                editorKey: row.pluginKey
              }
            }));
          }
        }
      } else {
        vm.$message.error(res.message || '设置失败');
      }
    }).catch(() => {
      vm.$message.error('网络错误');
    });
  }).catch(() => {});
}

export function togglePluginStatus(vm, row, enabled) {
  const action = enabled ? '启用' : '禁用';
  vm.$confirm(
    `${action}「${row.pluginName}」会重新生成全站预渲染页面，搜索引擎可能据此重新抓取文章页。确定继续吗？`,
    '操作确认',
    { type: 'warning' }
  ).then(() => {
    vm.$http.post(vm.$constant.baseURL + '/sysPlugin/togglePluginStatus', {
      id: row.id,
      enabled
    }, true)
      .then(() => {
        vm.$message.success(enabled ? '已启用' : '已禁用');
        row.enabled = enabled;
        if (enabled && vm.currentPluginType === 'particle_effect') {
          vm.activePluginKey = row.pluginKey;
        }
        if (!enabled && vm.activePluginKey === row.pluginKey) {
          vm.activePluginKey = 'none';
        }
      })
      .catch((error) => {
        vm.$message.error(error.message);
      });
  }).catch(() => {});
}

export function deletePlugin(vm, row) {
  vm.$confirm('确定要删除这个插件吗？', '提示', {
    type: 'warning'
  }).then(() => {
    vm.$http.post(vm.$constant.baseURL + '/sysPlugin/deletePlugin', { id: row.id }, true)
      .then(() => {
        vm.$message.success('删除成功');
        loadPluginListData(vm);
      })
      .catch((error) => {
        vm.$message.error(error.message);
      });
  }).catch(() => {});
}
