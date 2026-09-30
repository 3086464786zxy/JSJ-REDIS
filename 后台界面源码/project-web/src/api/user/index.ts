import http from '@/http'
import type { SysUser, SysUserListParm,Login,AssignTreeParm,UpdatePasswordParm } from './UserModel'
//新增
export const addApi = (parm:SysUser)=>{
  const {username,password,phone,email,sex,nickName,roleId} = parm;
  return http.post("/api/sysUser",{username,password,phone,email,sex,nickName,roleId});
}
//列表查询
export const getListApi = (parm:SysUserListParm)=>{
  return http.get("/api/sysUser/list",parm);
}
//根据用户id查询角色
export const getRoleListApi = (userId:string)=> {
  return http.get("/api/sysUser/getRoleList",{userId:userId});
}
//编辑
export const editApi = (parm:SysUser)=>{
  const {userId,username,phone,email,sex,nickName,roleId} = parm;
  return http.put("/api/sysUser",{userId,username,phone,email,sex,nickName,roleId});
}
//删除
export const deleteApi = (userId:string)=>{
  return http.delete(`/api/sysUser/${userId}`);
}
//重置密码
export const resetPasswordApi = (parm:{userId:string,password:string}) => {
  return http.post("/api/sysUser/resetPassword",parm);
}
//验证码
export const getImgApi = ()=>{
  return http.post("/api/sysUser/getImage");
}
//登录
export const loginApi = (parm:Login)=>{
  return http.post("/api/sysUser/login",parm);
}
//查询菜单树
export const getAssignTreeApi = (parm:AssignTreeParm) => {
  return http.get("/api/sysUser/getAssingTree",parm);
}
//修改用户密码
export const updatePasswordApi = (parm:UpdatePasswordParm) => {
  return http.post("/api/sysUser/updatePassword", parm);
}
//获取用户信息
export const getUserInfoApi = (userId:string) => {
  return http.get("/api/sysUser/getUserInfo",{userId:userId});
}
//退出登录
export const loginOutApi = ()=>{
  return http.post("/api/sysUser/loginOut")
}
