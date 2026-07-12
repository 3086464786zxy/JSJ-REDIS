import { useUserStore } from "@/store/user";
import type { Directive } from "vue";

//自定义按钮权限指令
export const permission:Directive = {
  //el: 当前按钮上面的DOM元素, bind: 绑定的按钮权限的值
  mounted(el,binding) {
    const userStore = useUserStore();
    //按钮上的权限字段
    const {value} = binding;
    //当前用户的所有权限字段
    const permissions = userStore.getCodeList;
    if (value && value instanceof Array && value.length > 0) {
      const permissionRoles = value;
      const hasPermission = permissions.some((role)=>{
        return permissionRoles.includes(role);
      })
      //如果没有按钮权限, 隐藏按钮
      if (!hasPermission) {
        el.style.display = 'none'
      }
    } else {
      throw new Error("按钮权限的传递方式v-permission=['sys:role:add']");
    }
  }
}
