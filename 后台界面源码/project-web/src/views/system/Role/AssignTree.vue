<template>
  <SysDialog
    :title="dialog.title"
    :width="dialog.width"
    :height="dialog.height"
    :visible="dialog.visible"
    @on-close="onClose"
    @on-confirm="commit"
  >
    <template v-slot:content>
      <el-tree
        ref="treeRef"
        node-key="menuId"
        show-checkbox
        default-expand-all
        :default-checked-keys="assignTreeData.assignTreeChecked"
        :check-strictly="false"
        :data="assignTreeData.list"
        :props="defaultProps"
    />
    </template>
  </SysDialog>
</template>

<script setup lang="ts">
import {getAssignTreeApi} from '@/api/user/index'
import { reactive, ref } from 'vue';
import SysDialog from '@/components/SysDialog.vue'
import useDialog from '@/hooks/useDialog'
import {useUserStore} from '@/store/user/index'
import { saveRoleMenuApi } from '@/api/role';
import { ElMessage } from 'element-plus';
import type { RenderContentContext, TreeInstance } from 'element-plus'
const treeRef = ref<TreeInstance>()
const userStore = useUserStore()
const {dialog,onClose,onShow} = useDialog()
//树的属性配置
const defaultProps = {
  children:'children',
  label: 'title',
}
//树数据
const assignTreeData = reactive({
  list:[],
  assignTreeChecked:[] //原来分配的权限id的集合
})
//树数据查询的参数
const parms = reactive({
  userId:'',
  roleId:''
})
//查询菜单树
const getAssignTree = async () => {
  let res = await getAssignTreeApi(parms);
  if (res && res.code == 200) {
    //设置权限树数据
    assignTreeData.list = res.data.menuList;
    //设置角色原来的权限id
    assignTreeData.assignTreeChecked = res.data.checkList;
    //数据回显, 判断角色原来是否已经分配过权限, 如果有，回显
    if (assignTreeData.assignTreeChecked.length > 0) {
      let newArr: any = [];
      assignTreeData.assignTreeChecked.forEach((item => {
        checked(item, assignTreeData.list, newArr);
      }))
      assignTreeData.assignTreeChecked = newArr;
    }
  }
}
const checked = (id: number, data: any, newArr: any) => {
   data.forEach((item: any) => {
    if (item.menuId == id) {
      if (item.children && item.children.length == 0) {
        newArr.push(item.menuId);
      }
    } else {
      if (item.children && item.children.length != 0) {
        //递归调用
        checked(id, item.children, newArr);
      }
    }
  });
}
//提交数据
const commitParm = reactive({
  roleId:'',
  list:[] as string[]
})
//弹框显示
const show = async (roleId:string,name:string) => {
  commitParm.roleId = roleId;
  assignTreeData.assignTreeChecked = [];
  assignTreeData.list = [];
  commitParm.list = [];
  parms.roleId = roleId;
  parms.userId = userStore.getUserId;
  //设置弹框属性
  dialog.height = 450;
  dialog.width = 300;
  dialog.title = '为【'+ name+'】分配菜单'
  //获取树数据
  await getAssignTree();
  onShow();
}
//提交表单
const commit = async () => {
  //获取选择的菜单数据
  console.log(treeRef.value?.getCheckedKeys());
  console.log(treeRef.value?.getHalfCheckedKeys());
  const checkIds = treeRef.value?.getCheckedKeys() as string[];
  const halfcheckIds = treeRef.value?.getHalfCheckedKeys() as string[];
  let ids = checkIds?.concat(halfcheckIds);
  //设置提交的数据
  commitParm.list = ids;
  //判断是否已经选择菜单
  if (ids.length == 0) {
    ElMessage.warning('请选择菜单');
    return;
  }
  let res = await saveRoleMenuApi(commitParm);
  if (res && res.code == 200) {
    ElMessage.success(res.msg);
    onClose();
  }
}
//暴露出去，给外部组件使用
defineExpose({
  show
})
</script>

<style scoped>

</style>
