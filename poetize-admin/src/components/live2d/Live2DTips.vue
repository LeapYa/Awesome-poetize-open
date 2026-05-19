<template>
  <transition name="fade">
    <div
      v-if="message && message.text"
      id="waifu-tips"
      class="waifu-tips"
      :class="{ 'waifu-tips-active': isActive }"
      v-html="formattedText"
    />
  </transition>
</template>

<script>
import { defineComponent, computed, ref, watch } from 'vue'

export default defineComponent({
  name: 'Live2DTips',
  
  props: {
    message: {
      type: Object,
      default: null
    }
  },
  
  setup(props) {
    const isActive = ref(false)
    
    // 格式化文本（支持HTML）
    const formattedText = computed(() => {
      if (!props.message) return ''
      return props.message.text
    })
    
    // 监听消息变化，触发动画
    watch(() => props.message, (newMsg) => {
      if (newMsg && newMsg.text) {
        isActive.value = true
      } else {
        isActive.value = false
      }
    }, { immediate: true })
    
    return {
      isActive,
      formattedText
    }
  }
})
</script>

<style scoped>
.waifu-tips {
  position: absolute;
  bottom: calc(var(--waifu-size, 280px) - 18px);
  left: 18px;
  width: min(250px, calc(100vw - 36px));
  max-width: calc(100vw - 36px);
  min-height: 60px;
  padding: 10px 15px;
  background: rgba(236, 217, 188, 0.5);
  border: 1px solid rgba(224, 186, 140, 0.62);
  border-radius: 12px;
  box-shadow: 0 3px 15px rgba(0, 0, 0, 0.2);
  font-size: 14px;
  line-height: 1.6;
  color: #5a3d2b;
  word-wrap: break-word;
  opacity: 0;
  transform: translateY(10px);
  transition: all 0.3s ease;
  pointer-events: auto;
  cursor: text;
  user-select: text;
  z-index: 10;
}

.waifu-tips-active {
  opacity: 1;
  transform: translateY(0);
}

.waifu-tips::before {
  content: "";
  position: absolute;
  bottom: -9px;
  left: 50%;
  transform: translateX(-50%);
  width: 0;
  height: 0;
  border-left: 9px solid transparent;
  border-right: 9px solid transparent;
  border-top: 9px solid rgba(224, 186, 140, 0.62);
}

.waifu-tips::after {
  content: "";
  position: absolute;
  bottom: -8px;
  left: 50%;
  transform: translateX(-50%);
  width: 0;
  height: 0;
  border-left: 8px solid transparent;
  border-right: 8px solid transparent;
  border-top: 8px solid rgba(236, 217, 188, 0.5);
}

.waifu-tips >>> a {
  color: #4facfe;
  text-decoration: underline;
}

.waifu-tips >>> a:hover {
  color: #0078d7;
}

/* 淡入淡出动画 */
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.fade-enter, .fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
</style>
