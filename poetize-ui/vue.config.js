const CompressionPlugin = require('compression-webpack-plugin')
const path = require('path')
const TerserPlugin = require('terser-webpack-plugin')
const axios = require('axios')
const fs = require('fs')

// 设置网站默认标题（实际运行时会从数据库获取）
const siteTitle = 'POETIZE'; 

module.exports = {
  devServer: {
    port: 80,
    https: false,
    open: false
  },
  lintOnSave: false,
  productionSourceMap: false,
  chainWebpack: config => {
    config
      .plugin('html')
      .tap(args => {
        args[0].title = siteTitle
        return args
      })
  },
  configureWebpack: {
    plugins: [
      new CompressionPlugin({
        algorithm: 'gzip',
        test: /\.js$|\.html$|\.css$/,
        filename: '[path].gz[query]',
        minRatio: 0.8,
        threshold: 8192,
        deleteOriginalAssets: false
      })
    ],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src'),
        'static': path.resolve(__dirname, 'public')
      }
    },
    optimization: {
      minimize: true,
      minimizer: [
        new TerserPlugin({
          terserOptions: {
            compress: {
              drop_console: true,
              drop_debugger: true
            }
          }
        })
      ],
      splitChunks: {
        chunks: 'all',
        maxInitialRequests: Infinity,
        minSize: 20000,
        cacheGroups: {
          vendor: {
            test: /[\\/]node_modules[\\/]/,
            name(module) {
              const packageName = module.context.match(/[\\/]node_modules[\\/](.*?)([\\/]|$)/)[1];
              return `npm.${packageName.replace('@', '')}`;
            }
          }
        }
      }
    }
  },
  publicPath: '/'
}
