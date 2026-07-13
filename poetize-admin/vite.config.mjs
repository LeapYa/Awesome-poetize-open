import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue2';
import Components from 'unplugin-vue-components/vite';
import { ElementUiResolver } from 'unplugin-vue-components/resolvers';
import path from 'path';
import viteCompression from 'vite-plugin-compression';
import envCompatible from 'vite-plugin-env-compatible';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// 开发环境自动从 git 获取版本号（生产环境由 deploy.sh 注入 VITE_APP_VERSION）
function getGitVersion() {
    try {
        const version = execSync('git describe --tags --abbrev=0', { cwd: path.resolve(__dirname, '..'), encoding: 'utf-8' }).trim();
        if (version) return version;
    } catch {
        // ignore
    }
    try {
        const tags = execSync('git tag --sort=-version:refname', { cwd: path.resolve(__dirname, '..'), encoding: 'utf-8' }).trim();
        if (tags) {
            const firstTag = tags.split('\n')[0].trim();
            if (firstTag) return firstTag;
        }
    } catch {
        // ignore
    }
    return 'dev';
}

const MERMAID_CHUNK_PACKAGES = [
    'mermaid',
    '@mermaid-js',
    '@braintree/sanitize-url',
    '@iconify/utils',
    'dagre-d3-es',
    'cytoscape',
    'cytoscape-cose-bilkent',
    'cytoscape-fcose',
    'dayjs',
    'd3',
    'd3-sankey',
    'dompurify',
    'langium',
    'khroma',
    'lodash-es',
    'marked',
    'roughjs',
    'stylis',
    'uuid',
    'chevrotain',
    'chevrotain-allstar',
    'vscode-jsonrpc',
    'vscode-languageserver',
    'vscode-languageserver-protocol',
    'vscode-languageserver-textdocument',
    'vscode-languageserver-types',
    'vscode-uri',
];

function matchesPackage(id, pkgName) {
    return id.includes(`/node_modules/${pkgName}/`) || id.includes(`\\node_modules\\${pkgName}\\`);
}

export default defineConfig({
    base: '/admin/',
    plugins: [
        vue(),
        Components({
            dirs: [],
            dts: false,
            resolvers: [ElementUiResolver()],
        }),
        viteCompression({
            algorithm: 'gzip',
            threshold: 8192,
        }),
        envCompatible(),
    ],
    resolve: {
        // 确保 element-ui 和 app 使用同一个 Vue 实例
        dedupe: ['vue'],
        alias: {
            // 使用 runtime.common.js (CJS) 确保与 Element-UI (CJS) 的兼容性
            // 同时也解决了 Vite 开发/生产环境的双实例问题
            'vue': 'vue/dist/vue.runtime.common.js',
            '@': path.resolve(__dirname, 'src'),
            'static': path.resolve(__dirname, 'public'),
            'element-ui': 'element-ui-ce',
            // 排除 axios Node.js 专用模块
            './lib/adapters/http.js': path.resolve(__dirname, 'src/utils/empty-module.js'),
        },
        extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.vue'],
        // 解决 element-ui-ce 入口解析问题
        mainFields: ['browser', 'module', 'jsnext:main', 'jsnext', 'main'],
    },
    server: {
        port: 5174,
        host: true,
        proxy: {
            '/api': {
                target: 'http://localhost:8081', // Java后端
                changeOrigin: true,
            },
            // 字体子集管理相关接口（下载切割包等）
            '/fontSubset': {
                target: 'http://localhost:8081',
                changeOrigin: true,
            },
            // 代理主站静态资源（开发环境）
            '/static': {
                target: 'http://localhost:5173', // 主站前端开发服务器
                changeOrigin: true,
            },
            '/assets': {
                target: 'http://localhost:5173', // 主站前端开发服务器
                changeOrigin: true,
            },
        },
    },
    define: {
        'process.env': {},
        '__APP_VERSION__': JSON.stringify(process.env.VITE_APP_VERSION || getGitVersion()),
    },
    build: {
        sourcemap: false, // 禁用 source map 以减少内存消耗
        minify: 'esbuild', // 使用 esbuild 替代 terser，内存效率更高
        assetsDir: 'static', // 静态资源输出目录，与原 Vue CLI 配置一致
        chunkSizeWarningLimit: 2000,
        modulePreload: {
            resolveDependencies(filename, deps, context) {
                if (context.hostType !== 'html') {
                    return deps;
                }

                return deps.filter(dep => {
                    if (dep === filename) {
                        return false;
                    }

                    if (dep.includes('/mermaid-') || dep.startsWith('static/mermaid-')) {
                        return false;
                    }

                    return true;
                });
            },
        },
        // 处理 CommonJS 模块兼容性
        commonjsOptions: {
            transformMixedEsModules: true,
            requireReturnsDefault: 'auto',
        },
        rollupOptions: {
            output: {
                manualChunks(id) {
                    if (id.includes('node_modules')) {
                        // 仅保留真正的框架运行时在首屏框架包里，Element UI 组件交给 Rollup 按实际引用拆分。
                        if ((id.includes('/vue/') || id.includes('\\vue\\')) || id.includes('vue-router') || id.includes('pinia')) {
                            return 'framework';
                        }
                        if (id.includes('element-ui')) {
                            return undefined;
                        }
                        if (id.includes('echarts') || id.includes('zrender')) {
                            return 'echarts';
                        }
                        if (id.includes('highlight.js')) {
                            return 'highlight';
                        }
                        if (MERMAID_CHUNK_PACKAGES.some(pkgName => matchesPackage(id, pkgName))) {
                            return 'mermaid';
                        }
                        if (matchesPackage(id, 'driver.js')) {
                            return 'driver';
                        }
                        if (matchesPackage(id, '@fingerprintjs/fingerprintjs')) {
                            return 'fingerprint';
                        }
                        if (id.includes('vditor')) {
                            return 'vditor';
                        }
                        if (id.includes('turndown')) {
                            return 'turndown';
                        }
                        if (id.includes('markdown-it') || id.includes('katex')) {
                            return 'markdown';
                        }
                        if (id.includes('axios') || id.includes('qs') || id.includes('qiniu') || id.includes('anime')) {
                            return 'libs';
                        }
                        if (id.includes('core-js') || id.includes('babel-runtime')) {
                            return 'polyfills';
                        }
                        return 'vendors';
                    }
                },
            },
        },
    }
});
