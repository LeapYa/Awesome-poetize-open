export default {
  // 测试环境 - 已注释，使用生产环境配置
  /*
  baseURL: "http://poetize-java:8081",
  webBaseURL: "http://poetize-frontend",
  imURL: "http://poetize-frontend/im",
  imBaseURL: "poetize-frontend",
  wsProtocol: "ws",
  wsPort: "9324",
  */

  // 生产环境
  baseURL: location.protocol + "//" + location.hostname + (location.port ? ':' + location.port : '') + "/api",
  webBaseURL: location.protocol + "//" + location.hostname + (location.port ? ':' + location.port : ''),
  imURL: location.protocol + "//" + location.hostname + (location.port ? ':' + location.port : '') + "/im",
  imBaseURL: location.hostname + (location.port ? ':' + location.port : ''),
  wsProtocol: location.protocol === "http:" ? "ws" : "wss",
  wsPort: "",

  webHistory: "/im/",
  hitokoto: "https://v1.hitokoto.cn",
  jinrishici: "https://v1.jinrishici.com/all.json",
  jitang: "https://api.oick.cn/dutang/api.php",
  shehui: "https://api.oick.cn/yulu/api.php",
  yiyan: "https://api.oick.cn/yiyan/api.php",
  dog: "https://api.oick.cn/dog/api.php",

  //前后端定义的密钥，AES使用16位
  cryptojs_key: "sarasarasarasara",

  before_color_1: "black",
  after_color_1: "linear-gradient(45deg, #f43f3b, #ec008c)",

  before_color_2: "rgb(131, 123, 199)",
  after_color_2: "linear-gradient(45deg, #f43f3b, #ec008c)",

  tree_hole_color: ["rgb(180, 224, 255)", "rgb(180, 203, 255)", "rgb(246, 223, 255)", "rgb(255, 214, 198)", "rgb(255, 205, 143)", "rgb(238, 255, 143)", "rgb(220, 255, 165)", "rgb(164, 234, 192)", "rgb(202, 241, 233)", "rgb(230, 230, 250)"],

  emojiList: ['衰', '鄙视', '再见', '捂嘴', '摸鱼', '奋斗', '白眼', '可怜', '皱眉', '鼓掌', '烦恼', '吐舌', '挖鼻', '委屈', '滑稽', '啊这', '生气', '害羞', '晕', '好色', '流泪', '吐血', '微笑', '酷', '坏笑', '吓', '大兵', '哭笑', '困', '呲牙']
}
