import {createStore} from 'vuex'


export default createStore({
  state: {
    currentUser: JSON.parse(localStorage.getItem("currentUser") || '{}'),
    sysConfig: JSON.parse(localStorage.getItem("sysConfig") || '{}')
  },
  getters: {},
  mutations: {
    loadCurrentUser(state, user) {
      state.currentUser = user;
      localStorage.setItem("currentUser", JSON.stringify(user));
    },
    loadSysConfig(state, sysConfig) {
      state.sysConfig = sysConfig;
      localStorage.setItem("sysConfig", JSON.stringify(sysConfig));
    }
  },
  actions: {},
  modules: {},
  plugins: []
})
