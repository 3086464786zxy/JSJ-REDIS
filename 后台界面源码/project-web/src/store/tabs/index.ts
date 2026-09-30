import {defineStore} from 'pinia'
//定义选项卡数据
export type Tab = {
  title:string;
  path:string;
}
//定义state的数据类型
export type TabState = {
  tabList:Tab[]
}

//定义store
export const useTabStore = defineStore('useTabStore',{
  state: ():TabState=>{
    return {
      tabList:[]
    }
  },
  getters:{
    getTab(state) {
      return state.tabList
    }
  },
  actions:{
    //添加选项卡数据
    addTab(tab:Tab) {
      //判断数据是否存在选项卡里面
      if (this.tabList.some(item=>item.path === tab.path)) return;
      if(tab.path == '/dashboard') {
        //如果是首页，加到第一个
        this.tabList.unshift(tab)
      } else {
        this.tabList.push(tab)
      }
    }
  },
  persist: {
    key: 'useTabStore',
    //storage: localStorage,
    storage: sessionStorage,
    pick: ['tabList'],  // 只持久化指定字段
    serializer: {  // 自定义序列化
      serialize: JSON.stringify,
      deserialize: JSON.parse
    },
  }
})
