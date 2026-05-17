<template>
  <div class="video-player-container" :class="{ 'is-playing': isPlaying }">
    <video
      ref="videoPlayer"
      class="video-player-box"
      :src="url?.src"
      :poster="cover"
      controls
      :loop="false"
      :muted="false"
      :autoplay="false"
      preload="metadata"
      playsinline
      @play="onPlayerPlay"
      @pause="onPlayerPause"
      @ended="onPlayerEnded"
      @waiting="onPlayerWaiting"
      @playing="onPlayerPlaying"
      @loadeddata="onPlayerLoadeddata"
      @timeupdate="onPlayerTimeupdate"
      @canplay="onPlayerCanplay"
      @canplaythrough="onPlayerCanplaythrough"
    >
      <source v-if="url?.src" :src="url.src" :type="url?.type || 'video/mp4'" />
      <p class="fallback-message">
        您的浏览器不支持 HTML5 视频播放，请升级浏览器。
      </p>
    </video>
    <span class="video-player-overlay" aria-hidden="true"></span>
  </div>
</template>

<script>
export default {
  name: 'VideoPlayer',
  props: {
    url: {
      type: Object,
      default: () => ({ src: '', type: '' }),
    },
    cover: {
      type: String,
      default: '',
    },
  },
  emits: ['play', 'pause', 'ended', 'ready'],
  data() {
    return {
      isReady: false,
      isPlaying: false,
    }
  },
  computed: {
    player() {
      return this.$refs.videoPlayer
    },
  },
  mounted() {
    // 视频播放器就绪
    this.$nextTick(() => {
      if (this.$refs.videoPlayer) {
        this.isReady = true
        this.$emit('ready', this.$refs.videoPlayer)
      }
    })
  },
  methods: {
    // 播放回调
    onPlayerPlay(event) {
      this.isPlaying = true
      this.$emit('play', event)
    },
    // 暂停回调
    onPlayerPause(event) {
      this.isPlaying = false
      this.$emit('pause', event)
    },
    // 视频播完回调
    onPlayerEnded(event) {
      this.isPlaying = false
      this.$emit('ended', event)
    },
    // 当播放由于暂时缺少数据而停止时触发
    onPlayerWaiting(event) {},
    // 播放恢复时触发
    onPlayerPlaying(event) {},
    // 视频帧加载完成后触发
    onPlayerLoadeddata(event) {},
    // currentTime更新时触发
    onPlayerTimeupdate(event) {},
    // 可以播放时触发
    onPlayerCanplay(event) {},
    // 可以完整播放时触发
    onPlayerCanplaythrough(event) {},
  },
}
</script>

<style scoped>
.video-player-container {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
}

.video-player-box {
  width: 100%;
  height: 100%;
  border-radius: 8px;
  overflow: hidden;
  background-color: #000;
}

.video-player-overlay {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 58px;
  height: 58px;
  pointer-events: none;
  background: rgba(0, 0, 0, 0.48);
  border-radius: 50%;
  transform: translate(-50%, -50%);
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.video-player-overlay::before {
  position: absolute;
  top: 50%;
  left: 52%;
  width: 0;
  height: 0;
  content: '';
  border-top: 14px solid transparent;
  border-bottom: 14px solid transparent;
  border-left: 22px solid #fff;
  transform: translate(-50%, -50%);
}

.video-player-container.is-playing .video-player-overlay {
  opacity: 0;
  transform: translate(-50%, -50%) scale(0.92);
}

.fallback-message {
  color: #fff;
  text-align: center;
  padding: 20px;
}
</style>
