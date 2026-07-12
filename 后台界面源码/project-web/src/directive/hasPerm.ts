import { useUserStore } from "@/store/user";
export default function hasParm(value: any) {
  const store = useUserStore()
  //当前用户拥有的权限字段
  const permissions = store.getCodeList;
  if (value && value instanceof Array && value.length > 0) {
    //按钮所需的权限字段
    const permissionRoles = value;
    //判断是否存在按钮所需的权限
    const hasPermission = permissions.some((role) => {
      return permissionRoles.includes(role)
    })
    return hasPermission
  } else {
    throw new Error('v-if="global.$hasPerm("[\'add\',\'edit\']")')
  }
}
