<template>
  <n-config-provider :theme="null" :locale="zhCN" :date-locale="dateZhCN">
    <n-dialog-provider>
      <router-view/>
    </n-dialog-provider>
  </n-config-provider>
</template>

<script>
  import {NConfigProvider, NDialogProvider, zhCN, dateZhCN, darkTheme} from 'naive-ui';

  export default {
    components: {
      NConfigProvider,
      NDialogProvider
    },
    setup() {
      return {
        darkTheme,
        zhCN,
        dateZhCN
      }
    }
  }
</script>

<style scoped>

</style>
