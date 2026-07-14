//定义用户数据类型
export type SysUser = {
  userId: string,
  username: string,
  password: string,
  phone: string,
  email: string,
  sex: string,
  nickName: string,
  roleId: string,
}
//列表查询参数
export type SysUserListParm = {
  phone:string,
  nickname:string,
  currentPage:number,
  pageSize:number,
  total:number
}

//登录
export type Login = {
  username:string;
  password:string;
  captchaId:string;
  code:string;
}
//菜单树参数
export type AssignTreeParm = {
  userId:string;
  roleId:string;
}
//修改密码参数
export type UpdatePasswordParm = {
  userId:string;
  oldPassword:string;
  password:string;
}
