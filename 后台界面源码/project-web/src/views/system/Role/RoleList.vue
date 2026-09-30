<template>
  <el-main>
    <!-- 搜索栏 -->
    <el-form :model="searchParm" :inline="true" size="default">
      <el-form-item label="">
        <el-input placeholder="请输入关键字" v-model="searchParm.roleName"></el-input>
      </el-form-item>
      <el-form-item>
        <el-button icon="Search" @click="searchBtn">搜索</el-button>
        <el-button icon="Close" type="danger" plain @click="resetBtn">重置</el-button>
        <el-button v-if="global.$hasPerm(['sys:role:add'])" icon="Plus" type="primary" @click="addBtn">新增</el-button>
      </el-form-item>
    </el-form>
    <!-- 表格数据 -->
    <el-table :height="tableHeight" :data="tableList" stripe border >
      <el-table-column prop="roleName" label="角色名称" />
      <el-table-column prop="remark" label="角色备注" />
      <el-table-column label="操作" v-if="global.$hasPerm(['sys:role:edit','sys:role:assign','sys:role:delete'])" width="320" align="center">
        <template #default="scope">
          <el-button v-if="global.$hasPerm(['sys:role:edit'])" icon="Edit" type="primary" size="default" @click="editBtn(scope.row)">编辑</el-button>
          <el-button v-if="global.$hasPerm(['sys:role:assign'])" icon="Edit" type="success" size="default" @click="assignBtn(scope.row)">分配菜单</el-button>
          <el-button v-if="global.$hasPerm(['sys:role:delete'])" icon="Delete" type="danger" size="default" @click="deleteBtn(scope.row.roleId)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <!-- 分页 -->
    <el-pagination
      @size-change="sizeChange"
      @current-change="currentChange"
      :current-page.sync="searchParm.currentPage"
      :page-sizes="[10, 20, 30, 40]"
      :page-size="searchParm.pageSize"
      layout="total, sizes, prev, pager, next, jumper"
      :total="searchParm.total" background>
    </el-pagination>

    <!-- 新增,编辑弹框 -->
    <SysDialog :title="dialog.title" :width="dialog.width" :height="dialog.height" :visible="dialog.visible"
      @on-close="onClose" @on-confirm="commit">
      <template v-slot:content>
        <el-form :model="addModel" ref="addRef" :rules="rules" label-width="80px" :inline="false" size="default">
          <el-form-item prop="roleName" label="角色名称">
            <el-input v-model="addModel.roleName"></el-input>
          </el-form-item>
          <el-form-item prop="remark" label="备注">
            <el-input v-model="addModel.remark"></el-input>
          </el-form-item>
        </el-form>

      </template>
    </SysDialog>
    <!-- 分配菜单 -->
    <AssignTree ref="assignTree"></AssignTree>
  </el-main>
</template>

<script setup lang="ts">
import {reactive, ref, onMounted, nextTick} from 'vue'
import SysDialog from '@/components/SysDialog.vue'
import useDialog from '@/hooks/useDialog'
import { ElMessage, type FormInstance } from 'element-plus';
import { addApi,getListApi,editApi,deleteApi } from '@/api/role/index'
import { type SysRole } from '@/api/role/RoleModel';
import useInstance from '@/hooks/useInstance'
import AssignTree from "./AssignTree.vue"
//菜单树的ref属性
const assignTree = ref();
//获取全局golbal
const {global} = useInstance()
//表单ref属性
const addRef = ref<FormInstance>()
//弹框属性
const {dialog,onClose,onShow} = useDialog()
//表单绑定的对象
const searchParm = reactive({
  currentPage: 1,
  //pageSize初始值要与分页中page-sizes数组第一个元素值相等
  pageSize:10,
  roleName:'',
  total:0
})
//判断新增还是编辑的标识 0: 新增 1: 编辑
const tags = ref('')
//新增按钮
const addBtn = () => {
  tags.value = '0'
  dialog.title = '新增';
  dialog.height = 180;
  //显示弹框
  onShow();
  //清空表单
  addRef.value?.resetFields();
}
//新增表单对象
const addModel = reactive({
  roleId:'',
  roleName:'',
  remark:''
})

//表单验证规则
const rules = reactive({
  roleName:[
    {
      required: true,
      message: "请输入角色名称",
      trigger: "change",
    },
  ]
})
//编辑按钮
const editBtn = (row:SysRole) => {
  tags.value = '1';
  //显示弹窗
  dialog.visible = true;
  dialog.title = '编辑';
  dialog.height = 180;
  nextTick(()=>{
    //回显数据
    Object.assign(addModel,row);
  })
  //清空表单
  addRef.value?.resetFields();
}
//分配菜单按钮
const assignBtn = (row:SysRole) => {
  assignTree.value.show(row.roleId,row.roleName);
}
//删除按钮
const deleteBtn = async (roleId:string) => {
  const confirm = await global.$myconfirm('确定删除该数据吗?');
  if (confirm) {
    let res = await deleteApi(roleId)
    if (res.code && res.code == 200) {
      ElMessage.success(res.msg)
      //刷新列表
      getList()
    }
  }
}
//页容量改变时触发
const sizeChange = (size:number)=> {
  searchParm.pageSize = size;
  getList();
}
//页数改变时触发
const currentChange = (page:number)=>{
  searchParm.currentPage = page;
  getList();
}
//表单提交
const commit = () => {
  addRef.value?.validate(async (valid) => {
    if (valid) {
      //提交请求
      let res = null;
      if (tags.value == '0') {
        res = await addApi(addModel); //新增
      } else {
        res = await editApi(addModel); //编辑
      }
      if(res && res.code == 200) {
        ElMessage.success(res.msg)
        //刷新列表
        getList();
        //关闭弹窗
        onClose();
      }
    }
  })
}
//表格高度
const tableHeight = ref(0)
//表格数据
const tableList = ref([])
//查询列表
const getList = async ()=>{
  let res = await getListApi(searchParm)
  if (res && res.code == 200) {
    //设置表格数据
    tableList.value = res.data.records
    //设置分页总条数
    searchParm.total = res.data.total
  }
}
//搜索
const searchBtn = () => {
  getList();
}
//重置
const resetBtn = () => {
  searchParm.roleName = '';
  searchParm.currentPage = 1;
  getList();
}
onMounted(()=>{
  //计算表格高度
  nextTick(()=>{
    tableHeight.value = window.innerHeight - 230;
  })
  getList()
})
</script>

<style scoped>

</style>
