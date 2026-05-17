<template>
  <div>
    <el-upload
      class="upload-demo"
      :action="qiniuUrl"
      multiple
      drag
      :on-remove="handleRemove"
      :limit="maxNumber"
      ref="upload"
      :auto-upload="false"
      :http-request="customUpload"
      :on-change="handleChange"
      :before-upload="beforeUpload"
      :on-success="handleSuccess"
      :on-error="handleError"
      list-type="picture"
      accept="image/*">
      <div class="el-upload__text">
        <svg viewBox="0 0 1024 1024" width="40" height="40">
          <path
            d="M666.2656 629.4528l-113.7664-112.4864c-20.7872-20.5824-54.3232-20.5312-75.1104 0.1024l-113.3056 112.4864c-20.8896 20.736-21.0432 54.528-0.256 75.4688 20.736 20.8896 54.528 21.0432 75.4688 0.256l22.6304-22.4256v189.5936c0 29.44 23.9104 53.3504 53.3504 53.3504s53.3504-23.9104 53.3504-53.3504v-189.5424l22.6816 22.4256a53.1456 53.1456 0 0 0 37.5296 15.4112c13.7728 0 27.4944-5.2736 37.9392-15.8208 20.6336-20.9408 20.4288-54.7328-0.512-75.4688z"
            fill="#FFE37B"></path>
          <path
            d="M820.992 469.504h-5.376c-3.072-163.328-136.3456-294.8096-300.4416-294.8096S217.856 306.1248 214.784 469.504H209.408c-100.7104 0-182.3744 81.664-182.3744 182.3744s81.664 182.3744 182.3744 182.3744h209.7664V761.856c-30.208 5.5808-62.464-3.3792-85.6576-26.7264-37.3248-37.5808-37.0688-98.5088 0.512-135.7824l113.3056-112.4864c37.2224-36.9664 97.8432-37.0176 135.168-0.1536l113.7664 112.4864c18.2272 18.0224 28.3648 42.0864 28.5184 67.7376 0.1536 25.6512-9.728 49.8176-27.7504 68.0448a95.40096 95.40096 0 0 1-68.3008 28.5184c-5.9392 0-11.776-0.512-17.5104-1.5872v72.3456h209.7664c100.7104 0 182.3744-81.664 182.3744-182.3744S921.7024 469.504 820.992 469.504z"
            fill="#8C7BFD"></path>
        </svg>
        <div>拖拽上传 / 点击上传</div>
      </div>
      <template #tip>
        <div class="el-upload__tip">
          一次最多上传{{maxNumber}}张图片，且每张图片不超过{{maxSize}}M！
        </div>
      </template>
    </el-upload>
    <div style="text-align: center;margin-top: 20px">
      <el-button type="success" style="font-size: 12px" @click="submitUpload">
        上传
      </el-button>
    </div>
  </div>
</template>
<script>
  import { message as ElMessage } from '@/utils/element-command-services';
  import upload from '../../../utils/imUtils/ajaxUpload';
  import { ref, computed, getCurrentInstance } from 'vue';
  import { useMainStore } from '../../../stores/main';
  
  export default {
    props: {
      prefix: {
        type: String,
        default: ""
      },
      maxSize: {
        type: Number,
        default: 5
      },
      maxNumber: {
        type: Number,
        default: 5
      }
    },
    emits: ['addPicture'],
    setup(props, { emit }) {
      const globalProperties = getCurrentInstance().appContext.config.globalProperties;
      const $http = globalProperties.$http;
      const $constant = globalProperties.$constant;
      const $common = globalProperties.$common;
      
      const mainStore = useMainStore();
      const uploadRef = ref(null);
      
      const storeType = computed(() => {
        return mainStore.sysConfig && mainStore.sysConfig['store.type'] 
          ? mainStore.sysConfig['store.type'] 
          : "local";
      });
      
      const qiniuUrl = computed(() => mainStore.sysConfig?.qiniuUrl || '');
      const currentUser = computed(() => mainStore.currentUser);

      const ensureUploadSuccess = (result, fallbackMessage) => {
        if (!result || (result.code && result.code !== 200) || result.success === false) {
          throw new Error((result && result.message) || fallbackMessage || "上传失败");
        }
      };

      const getChunkUploadSize = (fileSize) => {
        if (fileSize <= 2 * 1024 * 1024) {
          return 8 * 1024;
        }
        if (fileSize <= 30 * 1024 * 1024) {
          return 64 * 1024;
        }
        return 256 * 1024;
      };

      const createChunkUploadId = () => {
        return "im_" + Date.now().toString(36) + "_" + Math.random().toString(36).slice(2, 12);
      };

      const uploadLocalFileInChunks = async (file, data, options) => {
        const chunkSize = getChunkUploadSize(file.size || 0);
        const totalChunks = Math.max(1, Math.ceil((file.size || 0) / chunkSize));
        const uploadId = createChunkUploadId();

        for (let chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
          const start = chunkIndex * chunkSize;
          const end = Math.min(start + chunkSize, file.size);
          const chunkFd = new FormData();
          chunkFd.append("chunk", file.slice(start, end), file.name + ".part" + chunkIndex);
          chunkFd.append("uploadId", uploadId);
          chunkFd.append("chunkIndex", String(chunkIndex));
          chunkFd.append("totalChunks", String(totalChunks));

          const chunkResult = await $http.upload($constant.baseURL + "/resource/uploadChunk", chunkFd, false, {
            timeout: 60000,
            onProgress: () => {}
          });
          ensureUploadSuccess(chunkResult, "文件分片上传失败");

          if (options && typeof options.onProgress === "function") {
            options.onProgress({
              percent: Math.round(((chunkIndex + 1) / totalChunks) * 95)
            });
          }
        }

        const mergeFd = new FormData();
        mergeFd.append("uploadId", uploadId);
        mergeFd.append("totalChunks", String(totalChunks));
        mergeFd.append("originalName", data.originalName || file.name);
        mergeFd.append("relativePath", data.relativePath);
        mergeFd.append("type", data.type);
        mergeFd.append("contentType", file.type || "application/octet-stream");

        const mergeResult = await $http.upload($constant.baseURL + "/resource/mergeChunks", mergeFd, false, {
          timeout: 300000,
          onProgress: () => {}
        });
        ensureUploadSuccess(mergeResult, "文件合并失败");

        if (options && typeof options.onProgress === "function") {
          options.onProgress({ percent: 100 });
        }
        return mergeResult;
      };
      
      const submitUpload = () => {
        uploadRef.value.submit();
      };
      
      const handleSuccess = (response, file, fileList) => {
        let url;
        if (storeType.value === "local") {
          url = response.data;
        } else if (storeType.value === "qiniu") {
          url = mainStore.sysConfig['qiniu.downloadUrl'] + response.key;
          $common.saveResource(null, props.prefix, url, file.size, file.raw.type, file.name, "qiniu");
        } else if (storeType.value === "lsky" || storeType.value === "easyimage") {
          url = response.data;
          $common.saveResource(null, props.prefix, url, file.size, file.raw.type, file.name, storeType.value);
        }
        emit("addPicture", url);
      };
      
      const customUpload = (options) => {
        let suffix = "";
        if (options.file.name.lastIndexOf('.') !== -1) {
          suffix = options.file.name.substring(options.file.name.lastIndexOf('.'));
        }
        let key = props.prefix + "/" + currentUser.value.username.replace(/[^a-zA-Z]/g, '') + currentUser.value.id + new Date().getTime() + Math.floor(Math.random() * 1000) + suffix;
        let data = {};
        data.key = key;
        options.data = data;
        if (storeType.value === "local") {
          data.relativePath = key;
          data.type = props.prefix;
          data.storeType = storeType.value;
          data.originalName = options.file.name;
          data.file = options.file;
          if ((options.file.size || 0) > 1024 * 1024) {
            return uploadLocalFileInChunks(options.file, data, options);
          }
          return $http.upload($constant.baseURL + "/resource/upload", data, false, options);
        } else if (storeType.value === "qiniu") {
          const xhr = new XMLHttpRequest();
          xhr.open('get', $constant.baseURL + "/qiniu/getUpToken?key=" + key, false);
          xhr.withCredentials = true;
          try {
            xhr.send();
            const res = JSON.parse(xhr.responseText);
            if (res !== null && res.hasOwnProperty("code") && res.code === 200) {
              data.token = res.data;
              return upload(options);
            } else if (res !== null && res.hasOwnProperty("code") && res.code !== 200) {
              return Promise.reject(res.message);
            } else {
              return Promise.reject("服务异常！");
            }
          } catch (e) {
            return Promise.reject(e.message);
          }
        } else if (storeType.value === "lsky" || storeType.value === "easyimage") {
          data.relativePath = key;
          data.type = props.prefix;
          data.storeType = storeType.value;
          data.originalName = options.file.name;
          data.file = options.file;
          return $http.upload($constant.baseURL + "/resource/upload", data, options);
        }
      };
      
      const handleError = (err, file, fileList) => {
        ElMessage({
          message: err,
          type: 'error'
        });
      };
      
      const handleRemove = (file, fileList) => {
      };
      
      const beforeUpload = (file) => {
      };
      
      const handleChange = (file, fileList) => {
        let flag = false;
        if (file.size > props.maxSize * 1024 * 1024) {
          ElMessage({
            message: "图片最大为" + props.maxSize + "M！",
            type: 'warning'
          });
          flag = true;
        }
        if (fileList.length > props.maxNumber) {
          flag = true;
        }
        if (flag) {
          fileList.splice(fileList.length - 1, 1);
        }
      };
      
      return {
        upload: uploadRef,
        qiniuUrl,
        submitUpload,
        handleSuccess,
        customUpload,
        handleError,
        handleRemove,
        beforeUpload,
        handleChange
      };
    }
  }
</script>
<style scoped>
  :deep(.el-upload) {
    width: 100%;
  }
  :deep(.el-upload-dragger) {
    width: 100%;
  }
</style>
