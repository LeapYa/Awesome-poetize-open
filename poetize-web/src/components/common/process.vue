<template>
  <div v-if="!$common.isEmpty(treeHoleList)">
    <div class="process-line">
      <div
        class="process-item"
        v-for="(treeHole, index) in treeHoleList"
        :key="index"
      >
        <div class="timeline-item-time">
          <span>
            {{ $common.getDateDiff(treeHole.createTime) }}
          </span>
          <span
            @click="deleteTreeHole(treeHole.id)"
            class="process-delete"
            v-if="
              !$common.isEmpty(mainStore.currentUser) &&
              mainStore.currentUser.id === treeHole.userId
            "
          >
            <svg
              viewBox="0 0 1024 1024"
              width="16"
              height="16"
              style="vertical-align: -3px"
            >
              <path
                d="M921.1392 155.392h-270.592v-48.2816c0-22.7328-18.432-41.1648-41.1648-41.1648H426.3424a41.1648 41.1648 0 0 0-41.1648 41.1648v48.2816H110.6432c-14.1312 0-25.6 11.4688-25.6 25.6s11.4688 25.6 25.6 25.6h810.496c14.1312 0 25.6-11.4688 25.6-25.6s-11.4688-25.6-25.6-25.6zM170.8032 260.0448v592.8448c0 50.8928 41.2672 92.16 92.16 92.16h500.6848c50.8928 0 92.16-41.2672 92.16-92.16V260.0448H170.8032z m249.1392 462.7968c0 14.1312-11.4688 25.6-25.6 25.6s-25.6-11.4688-25.6-25.6V443.0848c0-14.1312 11.4688-25.6 25.6-25.6s25.6 11.4688 25.6 25.6v279.7568z m243.1488 0c0 14.1312-11.4688 25.6-25.6 25.6s-25.6-11.4688-25.6-25.6V443.0848c0-14.1312 11.4688-25.6 25.6-25.6s25.6 11.4688 25.6 25.6v279.7568z"
                fill="#FF623E"
              ></path>
            </svg>
          </span>
        </div>
        <CommentMarkdownRenderer
          class="timeline-item-content"
          :content="treeHole.content"
          @rendered="bindContentImages"
        />
      </div>
    </div>
  </div>
</template>

<script>
import { $on, $off, $once, $emit } from '../../utils/gogocodeTransfer'
import { useMainStore } from '@/stores/main'
import CommentMarkdownRenderer from '../comment/CommentMarkdownRenderer.vue'

export default {
  components: {
    CommentMarkdownRenderer,
  },
  props: {
    treeHoleList: {
      type: Array,
    },
  },
  data() {
    return {}
  },
  computed: {
    mainStore() {
      return useMainStore()
    },
  },
  watch: {},
  created() {},
  mounted() {
    this.$common.imgShow('.process-line .pictureReg')
  },
  methods: {
    deleteTreeHole(id) {
      $emit(this, 'deleteTreeHole', id)
    },
    // 内容渲染完成后绑定图片预览（渲染器异步出图，比 mounted 时机更可靠）
    bindContentImages() {
      this.$common.imgShow('.process-line .pictureReg')
    },
  },
  emits: ['deleteTreeHole'],
}
</script>

<style scoped>
.process-line {
  border-left: 2px solid var(--lightGreen);
  padding: 50px 20px 10px;
  margin-left: 20px;
  position: relative;
}
.process-line:before {
  content: '';
  width: 8px;
  height: 8px;
  border: 4px solid var(--maxLightRed);
  border-radius: 50%;
  position: absolute;
  top: 15px;
  left: -1px;
  transform: translateX(-50%);
  background-color: var(--white);
  animation: weiYanShadowFlashing 1.5s linear infinite;
}
.process-item {
  position: relative;
  margin: 10px;
  color: var(--fontColor);
}
.timeline-item-time::before {
  position: absolute;
  top: 0.35em;
  left: -37px;
  width: 6px;
  height: 6px;
  border: 3px solid var(--blue);
  border-radius: 50%;
  background: var(--white);
  content: '';
}
.timeline-item-content {
  padding: 12px 15px;
  margin: 10px 0 15px;
  border-radius: 10px;
  background: rgba(66, 139, 202, 0.2);
}
.process-delete {
  margin-left: 10px;
  cursor: pointer;
}
</style>
