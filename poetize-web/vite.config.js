import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import viteCompression from 'vite-plugin-compression'
import JavaScriptObfuscator from 'javascript-obfuscator'
import path from 'path'
import { fileURLToPath } from 'url'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import fs from 'fs'
import { minify } from 'terser'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

const ELEMENT_PLUS_SHARED_COMPONENT_ROOTS = {
  ElAside: 'container',
  ElButtonGroup: 'button',
  ElCheckboxButton: 'checkbox',
  ElCheckboxGroup: 'checkbox',
  ElCollapseItem: 'collapse',
  ElContainer: 'container',
  ElDropdownItem: 'dropdown',
  ElDropdownMenu: 'dropdown',
  ElFooter: 'container',
  ElFormItem: 'form',
  ElHeader: 'container',
  ElMain: 'container',
  ElMenuItem: 'menu',
  ElMenuItemGroup: 'menu',
  ElOption: 'select',
  ElOptionGroup: 'select',
  ElRadioButton: 'radio',
  ElRadioGroup: 'radio',
  ElSubMenu: 'menu',
  ElTabPane: 'tabs',
  ElTableColumn: 'table',
  ElTimelineItem: 'timeline',
  ElTreeSelect: 'tree-select',
}

function toKebabCase(value) {
  return value
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1-$2')
    .toLowerCase()
}

function getElementPlusDirectImport(componentName) {
  if (!/^El[A-Z]/.test(componentName)) {
    return null
  }

  if (/^ElIcon(?!$)/.test(componentName)) {
    return null
  }

  const importName = componentName
  const rootName =
    ELEMENT_PLUS_SHARED_COMPONENT_ROOTS[componentName] ||
    toKebabCase(componentName.slice(2))

  return {
    name: importName,
    from: `element-plus/es/components/${rootName}/index`,
    sideEffects: [`element-plus/es/components/${rootName}/style/css`],
  }
}

function createDirectElementPlusResolver() {
  return {
    type: 'component',
    resolve: getElementPlusDirectImport,
  }
}

function getElementPlusChunkName(normalizedId) {
  if (normalizedId.includes('/node_modules/dayjs/')) {
    return 'dayjs'
  }

  if (
    normalizedId.includes('/node_modules/@floating-ui/') ||
    normalizedId.includes('/node_modules/@popperjs/core/')
  ) {
    return 'ep-overlay'
  }

  if (normalizedId.includes('/node_modules/async-validator/')) {
    return 'ep-form-input'
  }

  if (
    !normalizedId.includes('/node_modules/element-plus/') &&
    !normalizedId.includes('/node_modules/@element-plus/')
  ) {
    return null
  }

  if (normalizedId.includes('/node_modules/@element-plus/icons-vue/')) {
    return 'ep-icons'
  }

  if (
    normalizedId.includes('/components/message') ||
    normalizedId.includes('/components/message-box') ||
    normalizedId.includes('/components/notification') ||
    normalizedId.includes('/components/loading')
  ) {
    return 'ep-feedback'
  }

  if (
    normalizedId.includes('/components/dialog') ||
    normalizedId.includes('/components/drawer') ||
    normalizedId.includes('/components/popover') ||
    normalizedId.includes('/components/popconfirm') ||
    normalizedId.includes('/components/overlay') ||
    normalizedId.includes('/components/popper') ||
    normalizedId.includes('/components/popper-content') ||
    normalizedId.includes('/components/focus-trap') ||
    normalizedId.includes('/components/tooltip') ||
    normalizedId.includes('/components/tour')
  ) {
    return 'ep-overlay'
  }

  if (
    normalizedId.includes('/components/form') ||
    normalizedId.includes('/components/input') ||
    normalizedId.includes('/components/input-number')
  ) {
    return 'ep-form-input'
  }

  if (
    normalizedId.includes('/components/select') ||
    normalizedId.includes('/components/option') ||
    normalizedId.includes('/components/option-group') ||
    normalizedId.includes('/components/checkbox') ||
    normalizedId.includes('/components/radio') ||
    normalizedId.includes('/components/switch') ||
    normalizedId.includes('/components/slider') ||
    normalizedId.includes('/components/cascader') ||
    normalizedId.includes('/components/color-picker')
  ) {
    return 'ep-form-choice'
  }

  if (
    normalizedId.includes('/components/date-picker') ||
    normalizedId.includes('/components/time-picker') ||
    normalizedId.includes('/components/time-select') ||
    normalizedId.includes('/components/calendar')
  ) {
    return 'ep-form-picker'
  }

  if (normalizedId.includes('/components/upload')) {
    return 'ep-upload'
  }

  if (
    normalizedId.includes('/components/image') ||
    normalizedId.includes('/components/avatar') ||
    normalizedId.includes('/components/card') ||
    normalizedId.includes('/components/descriptions') ||
    normalizedId.includes('/components/empty') ||
    normalizedId.includes('/components/result') ||
    normalizedId.includes('/components/skeleton') ||
    normalizedId.includes('/components/tag') ||
    normalizedId.includes('/components/text')
  ) {
    return 'ep-display'
  }

  if (
    normalizedId.includes('/components/collapse') ||
    normalizedId.includes('/components/tabs') ||
    normalizedId.includes('/components/dropdown') ||
    normalizedId.includes('/components/badge') ||
    normalizedId.includes('/components/divider') ||
    normalizedId.includes('/components/scrollbar')
  ) {
    return 'ep-nav'
  }

  if (
    normalizedId.includes('/components/button') ||
    normalizedId.includes('/components/link') ||
    normalizedId.includes('/components/progress') ||
    normalizedId.includes('/components/steps')
  ) {
    return 'ep-actions'
  }

  return 'ep-shared'
}

function captchaObfuscator() {
  return {
    name: 'captcha-obfuscator',
    apply: 'build',
    enforce: 'post',
    generateBundle(_options, bundle) {
      for (const fileName of Object.keys(bundle)) {
        const chunk = bundle[fileName]
        if (chunk.type !== 'chunk') continue
        if (!/^(static\/)?captcha-core-[\w-]+\.js$/.test(fileName)) continue

        const result = JavaScriptObfuscator.obfuscate(chunk.code, {
          compact: true,
          disableConsoleOutput: true,
          identifierNamesGenerator: 'hexadecimal',
          log: false,
          renameGlobals: false,
          rotateStringArray: true,
          stringArray: true,
          stringArrayEncoding: ['base64'],
          stringArrayThreshold: 0.35,
          unicodeEscapeSequence: false,
        })

        chunk.code = result.getObfuscatedCode()
      }
    },
  }
}

function swMinifier() {
  return {
    name: 'sw-minifier',
    apply: 'build',
    enforce: 'post',
    async closeBundle() {
      const swPath = path.resolve(__dirname, 'dist/sw.js')
      if (fs.existsSync(swPath)) {
        try {
          const code = fs.readFileSync(swPath, 'utf8')
          const result = await minify(code, {
            compress: true,
            mangle: true,
            format: {
              comments: false
            }
          })
          if (result.code) {
            fs.writeFileSync(swPath, result.code, 'utf8')
            console.log('✓ sw.js minified successfully')
          }
        } catch (e) {
          console.error('Failed to minify sw.js:', e)
        }
      }
    }
  }
}

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    swMinifier(),
    // 仅自动导入 Vue / Vue Router API，避免把 Element Plus JS 标识符重新指回聚合入口
    AutoImport({
      imports: ['vue', 'vue-router'],
      dts: false, // 不生成 d.ts 文件
    }),
    Components({
      globs: [
        'src/components/**/*.vue',
        '!src/components/im/common/commentBox.vue',
        '!src/components/im/common/emoji.vue',
        '!src/components/im/common/proButton.vue',
        '!src/components/im/common/uploadPicture.vue',
      ],
      resolvers: [
        // 直接指向组件子入口，避免统一经过 element-plus/es 聚合入口
        createDirectElementPlusResolver(),
      ],
      excludeNames: [
        /^(AsyncNotification|CaptchaContainer)$/,
        /^(Loader|Zombie|Printer|SortArticle|MyAside|Danmaku|Card|Process|VideoPlayer|Emoji|ProButton|UploadPicture|Live2DTips|Live2DCanvas|Live2DToolbar|Live2DToggle)$/,
      ],
      dts: false, // 不生成 d.ts 文件
    }),
    // Gzip compression
    viteCompression({
      algorithm: 'gzip',
      ext: '.gz',
      threshold: 8192,
      deleteOriginFile: false
    }),
    captchaObfuscator(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      'static': path.resolve(__dirname, 'public')
    },
    extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.vue']
  },
  server: {
    port: 5173,
    host: true,
    open: false,
    watch: {
      usePolling: true,
      interval: 300,
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      },
      '/ws/im': {
        target: 'ws://localhost:8081',
        ws: true,
        changeOrigin: true
      }
    }
  },
  build: {
    target: 'es2015',
    outDir: 'dist',
    assetsDir: 'static',
    sourcemap: false,
    chunkSizeWarningLimit: 2000, // 提高 chunk 大小警告阈值
    modulePreload: {
      polyfill: false, // 禁用 modulePreload polyfill
    },
    cssCodeSplit: true, // 保持 CSS 代码分割
    rollupOptions: {
      output: {
        manualChunks(id) {
          const normalizedId = id.replace(/\\/g, '/')
          if (
            normalizedId.includes('/src/utils/captchaUtil.js') ||
            normalizedId.includes('/src/utils/fingerprintUtil.js')
          ) {
            return 'captcha-core'
          }
          if (id.includes('node_modules')) {
            // 核心框架库 (仅 Vue 相关)
            if (id.match(/[\\/]node_modules[\\/](vue|@vue|pinia|vue-router)/)) {
              return 'framework';
            }
            // Element Plus 的“按需导入”只保证源码层面不整包引入，
            // 但在大量异步页面共同依赖时，Rolldown 仍可能把它们重新折叠
            // 成一个超大共享 chunk。这里按能力域继续拆分，避免再次聚合。
            const elementPlusChunk = getElementPlusChunkName(normalizedId)
            if (elementPlusChunk) {
              return elementPlusChunk
            }
            // ECharts 通过运行时按图表类型动态导入，交给 Vite 自动拆分
            // 避免这里强制合并成单个大 chunk
            // Vditor 编辑器
            if (id.includes('vditor')) {
              return 'vditor';
            }
            // 数学公式库
            if (id.includes('katex')) {
              return 'katex';
            }
            // 代码高亮库通过动态 import() 按需加载，交由 Vite/Rolldown 自动分块
            // 避免手动分块后在 Vite 8 / Rolldown 下出现导出绑定异常
            if (id.includes('highlight.js') || id.includes('highlightjs-line-numbers')) {
              return;
            }
            // Mermaid 及其依赖 - 不指定 chunk，让 Vite 自动处理动态导入
            // 这些库只通过动态 import() 加载，不需要预先打包
            if (id.includes('mermaid') || id.includes('cytoscape') || id.includes('elkjs')) {
              return; // 返回 undefined，不分配到任何 manual chunk
            }
            // 其他 node_modules 库也不分配到 vendors，让它们合并到使用它们的页面 chunk 中
          }
        }
      }
    }
  },
  optimizeDeps: {
    include: [
      'vue',
      'vue-router',
      'pinia',
      'element-plus',
      'axios'
    ]
  }
})
