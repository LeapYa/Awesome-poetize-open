export default {
  // 应用版本号（生产环境由 deploy.sh 注入，开发环境从 git tag 自动获取）
  // eslint-disable-next-line no-undef
  APP_VERSION: typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : 'dev',

  // GitHub 仓库地址（用于检查更新）
  GITHUB_REPO: 'LeapYa/awesome-poetize-open',

  // Gitee 仓库地址（国内服务器 GitHub 不可达时降级）
  GITEE_REPO: 'leapya/poetize',

  // 自动根据环境切换 URL
  baseURL: import.meta.env.DEV
    ? "http://localhost:8081"
    : location.protocol + "//" + location.host + "/api",


  webURL: import.meta.env.DEV
    ? "http://localhost:5174"
    : location.protocol + "//" + location.host,

  imBaseURL: import.meta.env.DEV
    ? "http://localhost:81/im/"
    : location.protocol + "//" + location.host + "/im",

  // 前台URL（用于跨应用跳转，如登录页、文章详情页）
  // 开发环境通过环境变量配置，生产环境与后台同域
  frontendURL: import.meta.env.DEV
    ? `http://localhost:5173`
    : location.protocol + "//" + location.host,

  // 后台登录URL（用于Token过期跳转）
  adminLoginURL: import.meta.env.DEV
    ? `http://localhost:5174/admin`
    : location.protocol + "//" + location.host,

  host: location.host,

  // 本地看板娘资源路径（使用主站的静态资源）
  live2d_path: "/static/live2d-widget/dist/",
  // Live2D 模型默认走 CDN，本地模型包按需下载后会自动切回 /static/live2d_api/
  localLive2dApiPath: "/static/live2d_api/",
  remoteLive2dApiPath: "https://cdn.jsdelivr.net/gh/fghrsh/live2d_api/",
  cdnPath: "https://cdn.jsdelivr.net/gh/fghrsh/live2d_api/",
  waifuPath: "/webInfo/getWaifuJson",
  // 一言API保留外部链接，但添加本地备用数据
  hitokoto: "https://v1.hitokoto.cn",
  // 社会语录API
  shehui: "https://api.oick.cn/yulu/api.php",
  tocbot: "/static/js/tocbot.min.js",
  // 今日诗词API
  jinrishici: "https://v1.jinrishici.com/all.json",
  //前后端定义的密钥，AES使用16位
  cryptojs_key: process.env.VUE_APP_POETIZE_AES_KEY || "sarasarasarasara",

  friendWebName: "POETIZE",
  friendUrl: location.protocol + "//" + location.host,
  friendIntroduction: "这是一个 Vue2 Vue3 与 SpringBoot 结合的产物～",
  friendCover: "https://s1.ax1x.com/2022/11/10/z9VlHs.png",

  before_color_list: ["black", "rgb(131, 123, 199)", "#ee7752", "#e73c7e", "#23a6d5", "#23d5ab"],

  tree_hole_color: ["rgb(180, 224, 255)", "rgb(180, 203, 255)", "rgb(246, 223, 255)", "rgb(255, 214, 198)", "rgb(255, 205, 143)", "rgb(238, 255, 143)", "rgb(220, 255, 165)", "rgb(164, 234, 192)", "rgb(202, 241, 233)", "rgb(230, 230, 250)"],

  before_color_1: "black",
  after_color_1: "linear-gradient(45deg, #f43f3b, #ec008c)",

  before_color_2: "rgb(131, 123, 199)",
  after_color_2: "linear-gradient(45deg, #f43f3b, #ec008c)",

  sortColor: ["linear-gradient(to right, #358bff, #15c6ff)",
    "linear-gradient(to right, #18e7ae, #1eebeb)",
    "linear-gradient(to right, #ff6655, #ffbf37)",
    "linear-gradient(120deg, rgba(255, 39, 232, 1) 0%, rgba(255, 128, 0, 1) 100%)",
    "linear-gradient(120deg, rgba(91, 39, 255, 1) 0%, rgba(0, 212, 255, 1) 100%)"
  ],

  pageColor: "#ee7752",
  commentPageColor: "#23d5ab",
  userId: 1,
  source: 0,

  emojiList: ['衰', '鄙视', '再见', '捂嘴', '摸鱼', '奋斗', '白眼', '可怜', '皱眉', '鼓掌', '烦恼', '吐舌', '挖鼻', '委屈', '滑稽', '啊这', '生气', '害羞', '晕', '好色', '流泪', '吐血', '微笑', '酷', '坏笑', '吓', '大兵', '哭笑', '困', '呲牙']
}
