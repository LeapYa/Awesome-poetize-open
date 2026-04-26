<template>
  <div>
    <!-- Live2D看板娘模式 -->
    <Live2DWidgetAsync v-if="displayMode === 'live2d'" />
    
    <!-- 简单按钮模式 -->
    <AIChatButtonAsync v-else-if="displayMode === 'button'" />
    
    <!-- AI聊天面板（懒加载） -->
    <AIChatPanelAsync v-if="showChat" />
  </div>
</template>

<script>
import { computed, defineAsyncComponent } from 'vue'
import { useLive2DStore } from '@/stores/live2d'
import { useMainStore } from '@/stores/main'

export default {
  name: 'Live2DIndex',
  
  components: {
    // 所有组件都使用动态导入，打破静态依赖链，避免 mermaid 等大型依赖被预加载
    Live2DWidgetAsync: defineAsyncComponent(() => import('./Live2DWidget.vue')),
    AIChatButtonAsync: defineAsyncComponent(() => import('./AIChatButton.vue')),
    AIChatPanelAsync: defineAsyncComponent(() => import('./AIChat/index.vue'))
  },
  
  props: {
    // 显示模式：'live2d' | 'button' | 'auto'
    // 'auto' 会根据live2d.enabled自动选择
    mode: {
      type: String,
      default: 'auto',
      validator: (value) => ['live2d', 'button', 'auto'].includes(value)
    }
  },
  
  setup(props) {
    const store = useLive2DStore()
    const mainStore = useMainStore()
    
    // 是否显示聊天窗口
    const showChat = computed(() => store.showChat)
    
    // 实际显示模式
    const displayMode = computed(() => {
      // 检查看板娘总开关是否启用
      const waifuEnabled = mainStore.webInfo?.enableWaifu !== false
      
      if (props.mode === 'auto') {
        // 自动模式：如果看板娘总开关关闭，则不显示任何内容
        if (!waifuEnabled) {
          return 'disabled'
        }
        // 如果live2d启用则显示live2d，否则显示按钮
        return store.enabled ? 'live2d' : 'button'
      }
      
      // 对于非auto模式，也需要检查总开关
      if (!waifuEnabled) {
        return 'disabled'
      }
      
      return props.mode
    })
    
    return {
      showChat,
      displayMode
    }
  }
}
</script>

<style>
/* 全局样式导入 */
@import './styles/live2d.css';
</style>
