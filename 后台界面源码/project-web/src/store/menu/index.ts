import { defineStore } from 'pinia';
import { getMenuListApi } from '@/api/menu';
import Layout from '@/layout/Index.vue'
import Center from '@/layout/center/center.vue'
import type { RouteRecord, RouteRecordRaw } from 'vue-router';
//获取views下面的所有.vue文件
const modules = import.meta.glob('../../views/**/*.vue')
//定义store
export const useMenuStore = defineStore('menu', {
  state: () => {
    return {
      initialized: false,
      collapse: true,
      //菜单数据
      menuList: [
        {
          path: '/dashboard',
          component: 'Layout',
          name: 'dashboard',
          meta: {
            title: '首页',
            icon: 'HomeFilled',
            roles: ['sys:dashboard']
          }
        },
      ]
    }
  },
  getters: {
    getCollapse(state) {
      return state.collapse;
    },
    getMenu(state) {
      return state.menuList;
    }
  },
  actions: {
    setCollapse(collapse:boolean) {
      this.collapse = collapse;
    },
    //获取菜单数据
    getMenuList(router: any, userId: string) {
      return new Promise((resolve, reject) => {
        //调用api里面的menu中的getMenuListApi方法
        getMenuListApi(userId).then((res) => {
          let accessRoute;
          if (res && res.code == 200) {
            //生成路由
            accessRoute = generateRoute(res.data, router) as any;
            this.menuList = [this.menuList[0], ...accessRoute];
            this.initialized = true;
          }
          resolve(this.menuList)
        }).catch((error) => {
          reject(error)
        })
      })
    }
  }
})
//动态生成路由
export function generateRoute(routes: RouteRecordRaw[], router: any, register = true) {
  //路由数据
  const res: Array<RouteRecordRaw> = [];
  routes.forEach((route: any) => {
    const tmp = { ...route }
    const component = tmp.component;
    //生成路由的component
    if (route.component) {
      if (component == 'Layout') {
        tmp.component = Layout;
      } else {
        tmp.component = modules[`../../${component}`]
      }
    }
    //有下级:
    if (tmp.children && tmp.children.length > 0) {
      if (route.component != 'Layout') {
        tmp.component = Center;
      }
      //递归：生成下级
      tmp.children = generateRoute(tmp.children, router, false);
    }
    res.push(tmp)
    //加入路由
    if (register) router.addRoute(tmp)
  })
  return res;
}
