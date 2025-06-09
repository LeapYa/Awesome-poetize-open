# FastMCP v2.0 主题切换服务器
import asyncio
import json
import logging
import sys
from typing import Any
from fastmcp import FastMCP

# 设置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# 创建FastMCP实例
mcp = FastMCP("Poetize主题切换服务")

@mcp.tool()
def toggle_theme() -> str:
    """切换网站主题（深色/浅色模式）
    
    Returns:
        str: 主题切换操作的结果消息
    """
    logger.info("收到主题切换请求")
    
    # 返回JavaScript代码来执行主题切换
    js_code = """
(function() {
    try {
        // 查找Vue组件实例
        const app = document.querySelector('#app');
        if (app && app.__vue__) {
            // 直接调用Vue组件的changeColor方法
            if (typeof app.__vue__.changeColor === 'function') {
                app.__vue__.changeColor();
                return '✅ 主题切换成功！';
            }
        }
        
        // 备用方案：查找所有可能的Vue实例
        const vueElements = document.querySelectorAll('[data-v-*], .vue-component');
        for (let element of vueElements) {
            if (element.__vue__ && typeof element.__vue__.changeColor === 'function') {
                element.__vue__.changeColor();
                return '✅ 主题切换成功！';
            }
        }
        
        // 最后的备用方案：模拟点击主题按钮
        const themeButton = document.querySelector('.el-icon-sunny, .fa-moon-o');
        if (themeButton) {
            themeButton.click();
            return '✅ 通过按钮切换主题成功！';
        }
        
        return '❌ 未找到主题切换方法';
    } catch (error) {
        console.error('主题切换错误:', error);
        return '❌ 主题切换失败: ' + error.message;
    }
})();"""
    
    return f"请在浏览器控制台执行以下代码进行主题切换：\n{js_code}"

@mcp.tool()
def get_current_theme() -> str:
    """获取当前网站主题状态
    
    Returns:
        str: 当前主题信息的JavaScript代码
    """
    logger.info("获取当前主题状态")
    
    js_code = """
(function() {
    try {
        const root = document.querySelector(':root');
        const bg = getComputedStyle(root).getPropertyValue('--background').trim();
        const isDark = bg === '#272727' || bg === 'rgb(39, 39, 39)';
        
        // 尝试获取Vue组件状态
        const app = document.querySelector('#app');
        let vueIsDark = null;
        if (app && app.__vue__ && app.__vue__.isDark !== undefined) {
            vueIsDark = app.__vue__.isDark;
        }
        
        return {
            cssTheme: isDark ? '深色' : '浅色',
            vueState: vueIsDark !== null ? (vueIsDark ? '深色' : '浅色') : '未知',
            synchronized: vueIsDark === isDark ? '✅ 同步' : '❌ 不同步'
        };
    } catch (error) {
        return { error: error.message };
    }
})();"""
    
    return f"请在浏览器控制台执行以下代码查看主题状态：\n{js_code}"

@mcp.tool()
def inject_theme_controller() -> str:
    """注入主题控制器到页面中
    
    Returns:
        str: 注入结果消息
    """
    logger.info("注入主题控制器")
    
    js_code = """
(function() {
    if (window.poetizeThemeController) {
        return '主题控制器已存在';
    }
    
    window.poetizeThemeController = {
        toggle: function() {
            try {
                // 方法1: 直接调用Vue组件方法
                const app = document.querySelector('#app');
                if (app && app.__vue__ && typeof app.__vue__.changeColor === 'function') {
                    app.__vue__.changeColor();
                    console.log('✅ Vue方法调用成功');
                    return '主题切换成功！';
                }
                
                // 方法2: 查找并点击主题按钮
                const buttons = document.querySelectorAll('.el-icon-sunny, .fa-moon-o');
                for (let btn of buttons) {
                    if (btn.offsetParent !== null) { // 确保按钮可见
                        btn.click();
                        console.log('✅ 按钮点击成功');
                        return '主题切换成功！';
                    }
                }
                
                // 方法3: 手动切换CSS变量
                const root = document.querySelector(':root');
                const currentBg = getComputedStyle(root).getPropertyValue('--background').trim();
                const isDark = currentBg === '#272727' || currentBg === 'rgb(39, 39, 39)';
                
                if (isDark) {
                    // 切换到浅色
                    root.style.setProperty('--background', 'white');
                    root.style.setProperty('--fontColor', 'black');
                    root.style.setProperty('--borderColor', 'rgba(0, 0, 0, 0.5)');
                    root.style.setProperty('--borderHoverColor', 'rgba(110, 110, 110, 0.4)');
                    root.style.setProperty('--articleFontColor', '#1F1F1F');
                    root.style.setProperty('--articleGreyFontColor', '#616161');
                    root.style.setProperty('--commentContent', '#F7F9FE');
                    root.style.setProperty('--favoriteBg', '#f7f9fe');
                } else {
                    // 切换到深色
                    root.style.setProperty('--background', '#272727');
                    root.style.setProperty('--fontColor', 'white');
                    root.style.setProperty('--borderColor', '#4F4F4F');
                    root.style.setProperty('--borderHoverColor', 'black');
                    root.style.setProperty('--articleFontColor', '#E4E4E4');
                    root.style.setProperty('--articleGreyFontColor', '#D4D4D4');
                    root.style.setProperty('--commentContent', '#D4D4D4');
                    root.style.setProperty('--favoriteBg', '#1e1e1e');
                }
                
                console.log('✅ CSS变量切换成功');
                return '主题切换成功！';
                
            } catch (error) {
                console.error('主题切换失败:', error);
                return '主题切换失败: ' + error.message;
            }
        },
        
        getStatus: function() {
            const root = document.querySelector(':root');
            const bg = getComputedStyle(root).getPropertyValue('--background').trim();
            return bg === '#272727' || bg === 'rgb(39, 39, 39)' ? '深色' : '浅色';
        }
    };
    
    console.log('✅ 主题控制器注入成功');
    return '主题控制器注入成功！现在可以使用 window.poetizeThemeController.toggle() 切换主题';
})();"""
    
    return f"请在浏览器控制台执行以下代码注入主题控制器：\n{js_code}"

async def main():
    """主入口函数"""
    try:
        logger.info("正在启动FastMCP服务器...")
        
        # 确保使用STDIO传输
        await mcp.run(transport="stdio")
        
    except KeyboardInterrupt:
        logger.info("收到退出信号，正在关闭服务器...")
    except Exception as e:
        logger.error(f"服务器运行错误: {e}")
        sys.exit(1)

if __name__ == "__main__":
    import asyncio
    import sys
    
    try:
        # 检查是否已有事件循环在运行
        try:
            loop = asyncio.get_running_loop()
            print("检测到已运行的事件循环，使用协程方式启动", file=sys.stderr)
            # 如果已有循环，直接创建任务
            loop.create_task(main())
        except RuntimeError:
            # 没有运行的循环，正常启动
            asyncio.run(main())
    except Exception as e:
        print(f"启动失败: {e}", file=sys.stderr)
        sys.exit(1)


