<template>
  <div class="myButton">
    <div :style="{'background': before}">{{info}}</div>
    <div :style="{'background': after}">{{info}}</div>
    <div :style="{'background': after}">{{info}}</div>
  </div>
</template>

<script>
  export default {
    props: {
      info: {
        type: String,
        default: "确定"
      },
      before: {
        type: String,
        default: "black"
      },
      after: {
        type: String,
        default: "linear-gradient(45deg, #f43f3b, #ec008c)"
      }
    }
  }
</script>

<style scoped>
  .myButton {
    cursor: pointer;
    user-select: none;
    position: relative;
    width: 66px;
    height: 33px;
    border-radius: 4px;
    color: var(--white);
    font-size: 14px;
    overflow: hidden;
  }

  .myButton div {
    width: 66px;
    height: 33px;
    line-height: 33px;
    border-radius: 4px;
    text-align: center;
    position: absolute;
  }

  .myButton div:nth-child(2) {
    width: 100px;
    transition: all 0.3s ease;
    transform: translateX(-120px) skewX(-30deg);
  }

  .myButton div:nth-child(3) {
    transition: all 0.3s ease;
    transform: translateX(-120px);
  }

  .myButton:hover div:nth-child(2) {
    transform: translateX(20px) skewX(-30deg);
  }

  .myButton:hover div:nth-child(3) {
    transform: translateX(0px);
  }
</style>
