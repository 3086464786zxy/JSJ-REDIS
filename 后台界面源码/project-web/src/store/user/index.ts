import { defineStore } from 'pinia'
import { getUserInfoApi } from '@/api/user';
//定义store
export const useUserStore = defineStore('user', {
  state: () => {
    return {
      userId:'',
      nickName:'',
      token:'',
      codeList:[] as string[]
    }
  },
  getters: {
    getUserId(state) {
      return state.userId;
    },
    getNickName(state) {
      return state.nickName;
    },
    getToken(state) {
      return state.token;
    },
    getCodeList(state) {
      return state.codeList;
    }
  },
  actions: {
    setUserId(userId:string) {
      this.userId = userId;
    },
    setNickName(nickName:string) {
      this.nickName = nickName;
    },
    setToken(token:string) {
      this.token = token;
    },
    getUserInfo() {
      return new Promise((resolve,reject) => {
        getUserInfoApi(this.userId).then((res)=>{
          if (res && res.code == 200) {
            this.codeList = res.data.permissions;
          }
          resolve(this.codeList);
        }).catch((error) => {
          reject(error);
        })
      })
    }
  },
  persist: {
    key: 'useUserStore',
    storage: sessionStorage,
    pick: ['userId','nickName','token'],  // 只持久化指定字段
    serializer: {  // 自定义序列化
    serialize: JSON.stringify,
    deserialize: JSON.parse
  },
  }
})
