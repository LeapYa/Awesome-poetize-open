<template>
  <el-dialog
    title="微言"
    :modal="true"
    :model-value="modelValue"
    width="40%"
    :append-to-body="true"
    :close-on-click-modal="false"
    destroy-on-close
    center
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div>
      <div style="margin-bottom: 5px">
        Date：
        <el-date-picker
          :model-value="newsTime"
          type="datetime"
          placeholder="Select date and time"
          @update:model-value="emit('update:newsTime', $event)"
        >
        </el-date-picker>
      </div>
      <commentBox :disableGraffiti="true" :disable-ai-mention="true" @submitComment="emit('submit-comment', $event)">
      </commentBox>
    </div>
  </el-dialog>
</template>

<script setup>
import { defineAsyncComponent } from 'vue'

defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
  newsTime: {
    type: [String, Date],
    default: '',
  },
})

const emit = defineEmits(['update:modelValue', 'update:newsTime', 'submit-comment'])

const commentBox = defineAsyncComponent(() => import('../comment/commentBox'))
</script>
