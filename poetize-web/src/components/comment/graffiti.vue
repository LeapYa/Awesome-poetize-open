<template>
  <div>
    <!-- 画布 -->
    <div
      style="padding: 5px"
      @mousemove="canvasOutMove($event)"
      @touchmove="canvasOutMove($event)"
    >
      <div class="graffiti-container">
        <canvas
          id="canvas"
          width="766"
          height="400"
          @mousedown="canvasDown($event)"
          @mouseup="canvasUp($event)"
          @mousemove="canvasMove($event)"
          @touchstart="canvasDown($event)"
          @touchend="canvasUp($event)"
          @touchmove="canvasMove($event)"
        >
        </canvas>
      </div>
    </div>

    <div style="padding: 10px 0 0 5px">
      <!-- 颜色 -->
      <div class="graffiti-tools">
        <span class="graffiti-title">画笔颜色</span>
        <div class="myCenter" style="margin-left: 2rem">
          <div
            v-for="(color, index) in colors"
            :class="{ activeColor: config.lineColor === color }"
            :style="{ background: color }"
            @click="setColor(color)"
            class="graffiti-color"
            :key="index"
          ></div>
        </div>
      </div>

      <!-- 大小 -->
      <div class="graffiti-tools">
        <span class="graffiti-title">画笔大小</span>
        <div class="myCenter" style="margin-left: 2rem">
          <i
            v-for="(pen, index) in brushSize"
            :key="index"
            class="graffiti-size"
            :class="[
              pen.className,
              { activeSize: config.lineWidth === pen.lineWidth },
            ]"
            @click="setBrush(pen.lineWidth)"
          >
          </i>
        </div>
      </div>

      <!-- 操作 -->
      <div class="graffiti-tools">
        <span class="graffiti-title">操作</span>
        <div class="myCenter" style="margin-left: 3.7rem">
          <i
            v-for="(control, index) in controls"
            :title="control.title"
            :class="control.className"
            class="graffiti-operate"
            @click="controlCanvas(control.action)"
            :key="index"
          >
          </i>
        </div>
      </div>

      <!-- 按钮 -->
      <div class="graffiti-tools" style="justify-content: center">
        <proButton
          :info="'文字'"
          @click="showComment()"
          :before="$constant.before_color_1"
          :after="$constant.after_color_1"
          style="margin-right: 6px"
        >
        </proButton>
        <proButton
          :info="'提交'"
          @click="getImage()"
          :before="$constant.before_color_2"
          :after="$constant.after_color_2"
        >
        </proButton>
      </div>
    </div>
  </div>
</template>

<script>
import { defineAsyncComponent } from 'vue'
import { $on, $off, $once, $emit } from '../../utils/gogocodeTransfer'
import { useMainStore } from '@/stores/main'


export default {
  components: {
    proButton: defineAsyncComponent(() => import('../common/proButton')),
  },
  data() {
    return {
      context: {},
      canvasMoveUse: false,
      // 存储当前表面状态数组-上一步
      preDrawAry: [],
      // 存储当前表面状态数组-下一步
      nextDrawAry: [],
      // 中间数组
      middleAry: [],
      // 配置参数
      config: {
        lineWidth: 5,
        lineColor: '#8154A3',
        shadowBlur: 2,
      },
      colors: [
        '#8154A3',
        '#fef4ac',
        '#0018ba',
        '#ffc200',
        '#f32f15',
        '#cccccc',
        '#5ab639',
      ],
      brushSize: [
        {
          className: 'small el-icon-edit',
          lineWidth: 5,
        },
        {
          className: 'middle el-icon-edit',
          lineWidth: 10,
        },
        {
          className: 'big el-icon-edit',
          lineWidth: 15,
        },
      ],
      currentStoreType: null, // 添加currentStoreType属性
    }
  },
  computed: {
    mainStore() {
      return useMainStore()
    },
    controls() {
      return [
        {
          title: '上一步',
          action: 'prev',
          className: this.preDrawAry.length
            ? 'active el-icon-arrow-left'
            : 'ban el-icon-arrow-left',
        },
        {
          title: '下一步',
          action: 'next',
          className: this.nextDrawAry.length
            ? 'active el-icon-arrow-right'
            : 'ban el-icon-arrow-right',
        },
        {
          title: '清除',
          action: 'clear',
          className:
            this.preDrawAry.length || this.nextDrawAry.length
              ? 'active el-icon-refresh'
              : 'ban el-icon-refresh',
        },
      ]
    },
  },
  mounted() {
    const canvas = document.querySelector('#canvas')
    this.context = canvas.getContext('2d', { willReadFrequently: true })
    this.initDraw()
    this.setCanvasStyle()
  },
  created() {
    // 监听系统配置更新事件
    $on(this.$bus, 'sysConfigUpdated', this.handleSysConfigUpdate)
  },
  beforeUnmount() {
    // 移除事件监听，避免内存泄漏
    $off(this.$bus, 'sysConfigUpdated', this.handleSysConfigUpdate)
  },
  methods: {
    canvasOutMove(e) {
      const canvas = document.querySelector('#canvas')
      if (e.target !== canvas) {
        this.canvasMoveUse = false
      }
    },
    initDraw() {
      const preData = this.context.getImageData(0, 0, 1200, 600)
      // 空绘图表面进栈
      this.middleAry.push(preData)
    },
    canvasUp(e) {
      const preData = this.context.getImageData(0, 0, 1200, 600)
      if (!this.nextDrawAry.length) {
        // 当前绘图表面进栈
        this.middleAry.push(preData)
      } else {
        this.middleAry = []
        this.middleAry = this.middleAry.concat(this.preDrawAry)
        this.middleAry.push(preData)
        this.nextDrawAry = []
      }
      this.canvasMoveUse = false
      this.context.beginPath()
    },
    canvasDown(e) {
      this.canvasMoveUse = true
      // client是基于整个页面的坐标
      // offset是canvas距离顶部以及左边的距离
      this.setCanvasStyle()
      // 清除子路径
      this.context.beginPath()
      this.context.moveTo(e.layerX, e.layerY)
      // 当前绘图表面状态
      const preData = this.context.getImageData(0, 0, 1200, 600)
      // 当前绘图表面进栈
      this.preDrawAry.push(preData)
    },
    canvasMove(e) {
      if (this.canvasMoveUse) {
        this.context.lineTo(e.layerX, e.layerY)
        this.context.stroke()
      }
    },
    // 设置绘画配置
    setCanvasStyle() {
      this.context.lineWidth = this.config.lineWidth
      this.context.shadowBlur = this.config.shadowBlur
      this.context.shadowColor = this.config.lineColor
      this.context.strokeStyle = this.config.lineColor
    },
    // 设置颜色
    setColor(color) {
      this.config.lineColor = color
    },
    // 设置笔刷大小
    setBrush(size) {
      this.config.lineWidth = size
    },
    controlCanvas(action) {
      switch (action) {
        case 'prev':
          if (this.preDrawAry.length) {
            const popData = this.preDrawAry.pop()
            const midData = this.middleAry[this.preDrawAry.length + 1]
            this.nextDrawAry.push(midData)
            this.context.putImageData(popData, 0, 0)
          }
          break
        case 'next':
          if (this.nextDrawAry.length) {
            const popData = this.nextDrawAry.pop()
            const midData =
              this.middleAry[
                this.middleAry.length - this.nextDrawAry.length - 2
              ]
            this.preDrawAry.push(midData)
            this.context.putImageData(popData, 0, 0)
          }
          break
        case 'clear':
          this.clearContext()
          this.middleAry = [this.middleAry[0]]
          break
      }
    },
    clearContext() {
      this.context.clearRect(
        0,
        0,
        this.context.canvas.width,
        this.context.canvas.height
      )
      this.preDrawAry = []
      this.nextDrawAry = []
    },
    showComment() {
      this.clearContext()
      $emit(this, 'showComment')
    },
    getImage() {
      if (this.$common.isEmpty(this.mainStore.currentUser)) {
        this.$message({
          message: '请先登录！',
          type: 'error',
        })
        return
      }

      if (this.preDrawAry.length < 1) {
        this.$message({
          message: '你还没画呢~',
          type: 'warning',
        })
        return
      }

      const canvas = document.querySelector('#canvas')
      const dataURL = canvas.toDataURL('image/png')
      let arr = dataURL.split(','),
        mine = arr[0].match(/:(.*?);/)[1],
        str = atob(arr[1]),
        n = str.length,
        u8arr = new Uint8Array(n)
      while (n--) {
        u8arr[n] = str.charCodeAt(n)
      }
      let obj = new Blob([u8arr], { type: mine })
      let key =
        'graffiti' +
        '/' +
        this.mainStore.currentUser.username.replace(/[^a-zA-Z]/g, '') +
        this.mainStore.currentUser.id +
        new Date().getTime() +
        Math.floor(Math.random() * 1000) +
        '.png'

      // 获取当前存储类型，优先使用更新后的配置
      let storeType =
        this.currentStoreType ||
        this.mainStore.sysConfig['store.type'] ||
        'local'

      let fd = new FormData()
      fd.append('file', obj)
      fd.append('originalName', 'graffiti.png')
      fd.append('key', key)
      fd.append('relativePath', key)
      fd.append('type', 'graffiti')
      fd.append('storeType', storeType)

      if (storeType === 'local') {
        this.saveLocal(fd)
      } else if (storeType === 'qiniu') {
        this.saveQiniu(fd)
      } else if (storeType === 'lsky') {
        this.saveLsky(fd)
      } else if (storeType === 'easyimage') {
        this.saveLsky(fd)
      }
    },
    saveLocal(fd) {
      const file = fd.get('file')
      if (file && file.size > 1024 * 1024) {
        this.saveLocalInChunks(fd)
        return
      }
      this.$http
        .upload(this.$constant.baseURL + '/resource/upload', fd)
        .then((res) => {
          if (!this.$common.isEmpty(res.data)) {
            this.clearContext()
            let url = res.data
            let img = '[你画我猜,' + url + ']'
            $emit(this, 'addGraffitiComment', img)
          }
        })
        .catch((error) => {
          this.$message({
            message: error.message,
            type: 'error',
          })
        })
    },
    async saveLocalInChunks(fd) {
      const file = fd.get('file')
      const chunkSize = this.getChunkUploadSize(file.size || 0)
      const totalChunks = Math.max(1, Math.ceil((file.size || 0) / chunkSize))
      const uploadId = this.createChunkUploadId()

      try {
        for (let chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
          const start = chunkIndex * chunkSize
          const end = Math.min(start + chunkSize, file.size)
          const chunkFd = new FormData()
          chunkFd.append('chunk', file.slice(start, end), 'graffiti.png.part' + chunkIndex)
          chunkFd.append('uploadId', uploadId)
          chunkFd.append('chunkIndex', String(chunkIndex))
          chunkFd.append('totalChunks', String(totalChunks))

          const chunkResult = await this.$http.upload(
            this.$constant.baseURL + '/resource/uploadChunk',
            chunkFd,
            false,
            {
              timeout: 60000,
              onProgress: () => {},
            }
          )
          this.ensureUploadSuccess(chunkResult, '涂鸦分片上传失败')
        }

        const mergeFd = new FormData()
        mergeFd.append('uploadId', uploadId)
        mergeFd.append('totalChunks', String(totalChunks))
        mergeFd.append('originalName', fd.get('originalName') || 'graffiti.png')
        mergeFd.append('relativePath', fd.get('relativePath'))
        mergeFd.append('type', fd.get('type'))
        mergeFd.append('contentType', file.type || 'image/png')

        const mergeResult = await this.$http.upload(
          this.$constant.baseURL + '/resource/mergeChunks',
          mergeFd,
          false,
          {
            timeout: 300000,
            onProgress: () => {},
          }
        )
        this.ensureUploadSuccess(mergeResult, '涂鸦合并失败')

        if (!this.$common.isEmpty(mergeResult.data)) {
          this.clearContext()
          let url = mergeResult.data
          let img = '[你画我猜,' + url + ']'
          $emit(this, 'addGraffitiComment', img)
        }
      } catch (error) {
        this.$message({
          message: error.message,
          type: 'error',
        })
      }
    },
    getChunkUploadSize(fileSize) {
      if (fileSize <= 2 * 1024 * 1024) {
        return 8 * 1024
      }
      if (fileSize <= 30 * 1024 * 1024) {
        return 64 * 1024
      }
      return 256 * 1024
    },
    createChunkUploadId() {
      return (
        'g_' +
        Date.now().toString(36) +
        '_' +
        Math.random().toString(36).slice(2, 12)
      )
    },
    ensureUploadSuccess(result, fallbackMessage) {
      if (!result || (result.code && result.code !== 200) || result.success === false) {
        throw new Error((result && result.message) || fallbackMessage || '上传失败')
      }
    },
    saveQiniu(fd) {
      this.$http
        .get(this.$constant.baseURL + '/qiniu/getUpToken', {
          key: fd.get('key'),
        })
        .then((res) => {
          if (!this.$common.isEmpty(res.data)) {
            fd.append('token', res.data)

            this.$http
              .uploadQiniu(this.mainStore.sysConfig.qiniuUrl, fd)
              .then((res) => {
                if (!this.$common.isEmpty(res.key)) {
                  this.clearContext()
                  let url =
                    this.mainStore.sysConfig['qiniu.downloadUrl'] + res.key
                  let file = fd.get('file')
                  this.$common.saveResource(
                    this,
                    'graffiti',
                    url,
                    file.size,
                    file.type,
                    null,
                    'qiniu'
                  )
                  let img = '[你画我猜,' + url + ']'
                  $emit(this, 'addGraffitiComment', img)
                }
              })
              .catch((error) => {
                this.$message({
                  message: error.message,
                  type: 'error',
                })
              })
          }
        })
        .catch((error) => {
          this.$message({
            message: error.message,
            type: 'error',
          })
        })
    },
    saveLsky(fd) {
      this.$http
        .post(this.$constant.baseURL + '/resource/upload', fd)
        .then((res) => {
          if (!this.$common.isEmpty(res.data)) {
            this.clearContext()
            let url = res.data
            let file = fd.get('file')
            let storeType = fd.get('storeType') || 'lsky'
            this.$common.saveResource(
              this,
              'graffiti',
              url,
              file.size,
              file.type,
              null,
              storeType
            )
            let img = '[你画我猜,' + url + ']'
            $emit(this, 'addGraffitiComment', img)
          } else {
            this.$message({
              message: '图床上传失败：服务器未返回有效的图片URL',
              type: 'error',
            })
          }
        })
        .catch((error) => {
          this.$message({
            message: error.message,
            type: 'error',
          })
        })
    },
    handleSysConfigUpdate(config) {
      if (config && config['store.type']) {
        this.currentStoreType = config['store.type']
      }
    },
  },
  emits: ['addGraffitiComment', 'showComment'],
}
</script>

<style scoped>
.graffiti-container {
  overflow: hidden;
  border: 2px var(--lightGray) solid;
  border-radius: 4px;
}
.graffiti-tools {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}
.graffiti-title {
  font-size: 14px;
}
.graffiti-color {
  width: 1rem;
  height: 1rem;
  border-radius: 50%;
  float: left;
  margin-right: 20px;
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease;
}
.graffiti-color:hover {
  width: 1.3rem;
  height: 1.3rem;
}
.activeColor {
  width: 1.3rem;
  height: 1.3rem;
}
.graffiti-size {
  cursor: pointer;
  margin-right: 20px;
}
.graffiti-size.small {
  font-size: 14px;
}
.graffiti-size.middle {
  font-size: 16px;
}
.graffiti-size.big {
  font-size: 18px;
}
.activeSize {
  background-color: var(--themeBackground);
  color: var(--white);
  border-radius: 50%;
  padding: 2px;
}
.graffiti-operate {
  margin-right: 20px;
  cursor: pointer;
  font-size: 18px;
}
.graffiti-operate.ban {
  cursor: not-allowed;
}
</style>
