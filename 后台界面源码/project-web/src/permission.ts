import router from './router'
import { useUserStore } from './store/user'
import { useMenuStore } from './store/menu'
//定义白名单: 不需要验证
const whiteList = ['/login']
router.beforeEach(async (to,from,next)=>{
  const userStore = useUserStore();
  const menuStore = useMenuStore();
  //获取token
  const token = userStore.getToken;
  //判断token是否存在
  if (token) {
    //判断是否是登录或首页来的: 是放行， 不是: 从服务器获取菜单数据
    if (to.path === '/login') {
      next({path:'/'});
    } else {
      //判断权限数据是否存在
      const hasRoles = userStore.initialized;
      const hasMenus = menuStore.initialized;
      if (hasRoles && hasMenus) { //存在: 放行
        next()
      } else { //不存在: 从服务器获取
        try {
          //获取用户信息
          await userStore.getUserInfo();
          //获取菜单数据
          await menuStore.getMenuList(router, userStore.getUserId);
          //等待路由完全挂载
          next({ ...to, replace: true });
        } catch {
          // 认证失效由 HTTP 层统一退出；网络异常不清空仍有效的会话。
          if (userStore.getToken) next(false);
          else next({path:'/login'});
        }
      }
    }
  } else {
    if (whiteList.indexOf(to.path) !== -1) {
      //说明要去的路由在白名单, 放行
      next();
    } else { //不在白名单，跳转登录
      next({path:'/login'});
    }
  }
})
