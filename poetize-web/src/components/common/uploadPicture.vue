<template>
  <div>
    <el-upload
      class="upload-demo"
      ref="upload"
      multiple
      drag
      :action="mainStore.sysConfig.qiniuUrl"
      :on-change="handleChange"
      :before-upload="beforeUpload"
      :on-success="handleSuccess"
      :on-error="handleError"
      :on-remove="handleRemove"
      :http-request="customUpload"
      :list-type="listType"
      :accept="accept"
      :limit="maxNumber"
      :auto-upload="false"
    >
      <div class="el-upload__text">
        <svg viewBox="0 0 1024 1024" width="40" height="40">
          <path
            d="M666.2656 629.4528l-113.7664-112.4864c-20.7872-20.5824-54.3232-20.5312-75.1104 0.1024l-113.3056 112.4864c-20.8896 20.736-21.0432 54.528-0.256 75.4688 20.736 20.8896 54.528 21.0432 75.4688 0.256l22.6304-22.4256v189.5936c0 29.44 23.9104 53.3504 53.3504 53.3504s53.3504-23.9104 53.3504-53.3504v-189.5424l22.6816 22.4256a53.1456 53.1456 0 0 0 37.5296 15.4112c13.7728 0 27.4944-5.2736 37.9392-15.8208 20.6336-20.9408 20.4288-54.7328-0.512-75.4688z"
            fill="#FFE37B"
          ></path>
          <path
            d="M820.992 469.504h-5.376c-3.072-163.328-136.3456-294.8096-300.4416-294.8096S217.856 306.1248 214.784 469.504H209.408c-100.7104 0-182.3744 81.664-182.3744 182.3744s81.664 182.3744 182.3744 182.3744h209.7664V761.856c-30.208 5.5808-62.464-3.3792-85.6576-26.7264-37.3248-37.5808-37.0688-98.5088 0.512-135.7824l113.3056-112.4864c37.2224-36.9664 97.8432-37.0176 135.168-0.1536l113.7664 112.4864c18.2272 18.0224 28.3648 42.0864 28.5184 67.7376 0.1536 25.6512-9.728 49.8176-27.7504 68.0448a95.40096 95.40096 0 0 1-68.3008 28.5184c-5.9392 0-11.776-0.512-17.5104-1.5872v72.3456h209.7664c100.7104 0 182.3744-81.664 182.3744-182.3744S921.7024 469.504 820.992 469.504z"
            fill="#8C7BFD"
          ></path>
        </svg>
        <div>拖拽上传 / 点击上传</div>
      </div>
      <template #tip>
        <div class="el-upload__tip" v-if="listType === 'picture'">
          一次最多上传{{ maxNumber }}张图片，且每张图片不超过{{ effectiveMaxSize }}M！
        </div>
        <div class="el-upload__tip" v-else>
          一次最多上传{{ maxNumber }}个文件，且每个文件不超过{{ effectiveMaxSize }}M！
        </div>
      </template>
    </el-upload>

    <div style="text-align: center; margin-top: 20px">
      <el-button type="success" style="font-size: 12px" @click="submitUpload">
        上传
      </el-button>
    </div>
  </div>
</template>

<script>
import { $on, $off, $once, $emit } from '../../utils/gogocodeTransfer'
import { useMainStore } from '@/stores/main'

import upload from '../../utils/ajaxUpload'

// 站长/管理员适用的系统硬上限（MB）：资源 size 以 int 存储（Integer.MAX_VALUE），实际约 2GB
const STAFF_MAX_UPLOAD_MB = 2048

export default {
  props: {
    isAdmin: {
      type: Boolean,
      default: false,
    },
    prefix: {
      type: String,
      default: '',
    },
    listType: {
      type: String,
      default: 'picture',
    },
    storeType: {
      type: String,
      default: null, // 改为null，使用计算属性获取
    },
    accept: {
      type: String,
      default: 'image/*,.ico,.tiff,.tif,.bmp,.psd,.svg',
    },
    maxSize: {
      type: Number,
      default: 5,
    },
    maxNumber: {
      type: Number,
      default: 5,
    },
  },
  data() {
    return {
      // 本地存储类型，当props未提供时使用
      localStoreType: null,
    }
  },
  computed: {
    mainStore() {
      return useMainStore()
    },
    isBossUser() {
      const userType = this.mainStore.currentUser && this.mainStore.currentUser.userType
      return userType === 0 || userType === 1
    },
    // 站长/管理员适用系统硬上限 2048MB，普通用户按 maxSize
    effectiveMaxSize() {
      return this.isBossUser ? STAFF_MAX_UPLOAD_MB : this.maxSize
    },
    // 计算属性：获取当前存储类型
    currentStoreType() {
      // 优先使用props传入的storeType
      if (this.storeType) {
        return this.storeType
      }

      // 其次使用本地存储的localStoreType（可能通过事件更新）
      if (this.localStoreType) {
        return this.localStoreType
      }

      // 最后使用Vuex中的配置
      return this.mainStore.sysConfig && this.mainStore.sysConfig['store.type']
        ? this.mainStore.sysConfig['store.type']
        : 'local'
    },
  },
  watch: {},
  created() {
    // 监听系统配置更新事件
    $on(this.$bus, 'sysConfigUpdated', this.handleSysConfigUpdate)
  },
  mounted() {},
  beforeUnmount() {
    // 移除事件监听，避免内存泄漏
    $off(this.$bus, 'sysConfigUpdated', this.handleSysConfigUpdate)
  },
  methods: {
    // 处理系统配置更新事件
    handleSysConfigUpdate(config) {
      if (config && config['store.type']) {
        this.localStoreType = config['store.type']
      }
    },

    submitUpload() {
      this.$refs.upload.submit()
    },

    customUpload(options) {
      let suffix = ''
      if (options.file.name.lastIndexOf('.') !== -1) {
        suffix = options.file.name.substring(options.file.name.lastIndexOf('.'))
      }

      let key =
        this.prefix +
        '/' +
        (!this.$common.isEmpty(this.mainStore.currentUser.username)
          ? this.mainStore.currentUser.username.replace(/[^a-zA-Z]/g, '') +
            this.mainStore.currentUser.id
          : this.mainStore.currentAdmin.username.replace(/[^a-zA-Z]/g, '') +
            this.mainStore.currentAdmin.id) +
        new Date().getTime() +
        Math.floor(Math.random() * 1000) +
        suffix

      if (this.currentStoreType === 'local') {
        let fd = new FormData()
        fd.append('file', options.file)
        fd.append('originalName', options.file.name)
        fd.append('key', key)
        fd.append('relativePath', key)
        fd.append('type', this.prefix)
        fd.append('storeType', this.currentStoreType)

        if (this.shouldUseChunkUpload(options.file)) {
          return this.uploadLocalFileInChunks(options.file, fd, options)
        }
        return this.$http.upload(
          this.$constant.baseURL + '/resource/upload',
          fd,
          this.isAdmin,
          options
        )
      } else if (this.currentStoreType === 'qiniu') {
        const xhr = new XMLHttpRequest()
        xhr.open(
          'get',
          this.$constant.baseURL + '/qiniu/getUpToken?key=' + key,
          false
        )
        xhr.withCredentials = true

        try {
          xhr.send()
          const res = JSON.parse(xhr.responseText)
          if (res !== null && res.hasOwnProperty('code') && res.code === 200) {
            options.data = {
              token: res.data,
              key: key,
            }
            return upload(options)
          } else if (
            res !== null &&
            res.hasOwnProperty('code') &&
            res.code !== 200
          ) {
            return Promise.reject(res.message)
          } else {
            return Promise.reject('服务异常！')
          }
        } catch (e) {
          return Promise.reject(e.message)
        }
      } else if (
        this.currentStoreType === 'lsky' ||
        this.currentStoreType === 'easyimage'
      ) {
        let fd = new FormData()
        fd.append('file', options.file)
        fd.append('originalName', options.file.name)
        fd.append('key', key)
        fd.append('relativePath', key)
        fd.append('type', this.prefix)
        fd.append('storeType', this.currentStoreType)

        return this.$http.upload(
          this.$constant.baseURL + '/resource/upload',
          fd,
          this.isAdmin,
          options
        )
      }
    },

    async uploadLocalFileInChunks(file, fd, options) {
      const chunkSize = this.getChunkUploadSize(file.size || 0)
      const totalChunks = Math.max(1, Math.ceil((file.size || 0) / chunkSize))
      const uploadId = this.createChunkUploadId()

      for (let chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
        const start = chunkIndex * chunkSize
        const end = Math.min(start + chunkSize, file.size)
        const chunkFd = new FormData()
        chunkFd.append('chunk', file.slice(start, end), file.name + '.part' + chunkIndex)
        chunkFd.append('uploadId', uploadId)
        chunkFd.append('chunkIndex', String(chunkIndex))
        chunkFd.append('totalChunks', String(totalChunks))

        const chunkResult = await this.$http.upload(
          this.$constant.baseURL + '/resource/uploadChunk',
          chunkFd,
          this.isAdmin,
          {
            timeout: 60000,
            onProgress: () => {},
          }
        )
        this.ensureUploadSuccess(chunkResult, '文件分片上传失败')

        if (options && typeof options.onProgress === 'function') {
          options.onProgress({
            percent: Math.round(((chunkIndex + 1) / totalChunks) * 95),
          })
        }
      }

      const mergeFd = new FormData()
      mergeFd.append('uploadId', uploadId)
      mergeFd.append('totalChunks', String(totalChunks))
      mergeFd.append('originalName', fd.get('originalName') || file.name)
      mergeFd.append('relativePath', fd.get('relativePath'))
      mergeFd.append('type', fd.get('type'))
      mergeFd.append('contentType', file.type || 'application/octet-stream')

      const mergeResult = await this.$http.upload(
        this.$constant.baseURL + '/resource/mergeChunks',
        mergeFd,
        this.isAdmin,
        {
          timeout: 300000,
          onProgress: () => {},
        }
      )
      this.ensureUploadSuccess(mergeResult, '文件合并失败')

      if (options && typeof options.onProgress === 'function') {
        options.onProgress({ percent: 100 })
      }
      return mergeResult
    },

    shouldUseChunkUpload(file) {
      const fileSize = file && file.size ? file.size : 0
      return this.currentStoreType === 'local' && fileSize > 1024 * 1024
    },

    getChunkUploadSize(fileSize) {
      if (fileSize <= 50 * 1024 * 1024) {
        return 1 * 1024 * 1024
      }
      if (fileSize <= 500 * 1024 * 1024) {
        return 5 * 1024 * 1024
      }
      return 10 * 1024 * 1024
    },

    createChunkUploadId() {
      return (
        'w_' +
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

    // 文件上传成功时的钩子
    handleSuccess(response, file, fileList) {
      let url
      if (this.currentStoreType === 'local') {
        url = response.data
      } else if (this.currentStoreType === 'qiniu') {
        url = this.mainStore.sysConfig['qiniu.downloadUrl'] + response.key
        this.$common.saveResource(
          this,
          this.prefix,
          url,
          file.size,
          file.raw.type,
          file.name,
          'qiniu',
          this.isAdmin
        )
      } else if (
        this.currentStoreType === 'lsky' ||
        this.currentStoreType === 'easyimage'
      ) {
        url = response.data
        this.$common.saveResource(
          this,
          this.prefix,
          url,
          file.size,
          file.raw.type,
          file.name,
          this.currentStoreType,
          this.isAdmin
        )
      }
      $emit(this, 'addPicture', url)
    },
    handleError(err, file, fileList) {
      this.$message({
        message: err,
        type: 'error',
      })
    },
    // 上传文件之前的钩子，参数为上传的文件，若返回 false 或者返回 Promise 且被 reject，则停止上传
    beforeUpload(file) {
      // 支持的格式：JPEG, PNG, GIF, BMP, WEBP, TIFF, PSD, SVG, ICO
      const isImage = file.type.startsWith('image/')
      const isValidType = [
        'image/jpeg',
        'image/jpg',
        'image/png',
        'image/gif',
        'image/bmp',
        'image/webp',
        'image/tiff',
        'image/x-photoshop',
        'image/svg+xml',
        'image/x-icon',
        'image/vnd.microsoft.icon',
        'image/ico',
        'application/octet-stream',
      ].includes(file.type)
      const isValidExt =
        /\.(jpg|jpeg|png|gif|bmp|webp|tiff|tif|psd|svg|ico)$/i.test(file.name)

      if (!isImage && !isValidExt) {
        this.$message({
          message: '只能上传图片文件！',
          type: 'error',
        })
        return false
      }

      if (!isValidType && !isValidExt) {
        this.$message({
          message:
            '文件类型不被支持！支持：JPEG, PNG, GIF, BMP, WEBP, TIFF, PSD, SVG, ICO',
          type: 'error',
        })
        return false
      }

      if (!isValidExt) {
        this.$message({
          message: '文件扩展名不匹配！',
          type: 'error',
        })
        return false
      }

      // 防止文件名欺骗：检查文件名是否可疑
      const suspiciousPatterns =
        /(\.php|\.jsp|\.asp|\.aspx|\.sh|\.py|\.pl|\.exe|\.dll|\.bat|\.cmd|\.jar|\.class)$/i
      if (suspiciousPatterns.test(file.name)) {
        this.$message({
          message: '检测到危险文件类型，禁止上传！',
          type: 'error',
        })
        return false
      }

      // TIFF/PSD/SVG文件可能较大
      const ext = file.name.substring(file.name.lastIndexOf('.')).toLowerCase()
      if (['.tiff', '.tif', '.psd', '.svg'].includes(ext)) {
        this.$message({
          message: '注意：专业格式文件可能较大，上传时间可能较长',
          type: 'warning',
        })
      }

      return true
    },
    // 文件列表移除文件时的钩子
    handleRemove(file, fileList) {},
    // 添加文件、上传成功和上传失败时都会被调用
    handleChange(file, fileList) {
      // 站长/管理员上限为系统硬上限 2048MB，普通用户按 maxSize
      if (file.size > this.effectiveMaxSize * 1024 * 1024) {
        this.$message({
          message: '文件最大为' + this.effectiveMaxSize + 'M！',
          type: 'warning',
        })
        fileList.splice(fileList.size - 1, 1)
      }
    },
  },
  emits: ['addPicture'],
}
</script>
