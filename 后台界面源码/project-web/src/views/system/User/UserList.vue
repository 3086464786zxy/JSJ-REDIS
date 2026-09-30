<template>
  <el-main>
    <!-- 搜索表单 -->
    <el-form :model="searchParm" :inline="true" size="default">
      <el-form-item>
        <el-input placeholder="请输入姓名" v-model="searchParm.nickname"></el-input>
      </el-form-item>
      <el-form-item>
        <el-input placeholder="请输入电话" v-model="searchParm.phone"></el-input>
      </el-form-item>
      <el-form-item>
        <el-button icon="Search" @click="searchBtn">搜索</el-button>
        <el-button icon="Close" type="danger" @click="resetBtn">重置</el-button>
        <el-button icon="Plus" v-if="global.$hasPerm(['sys:user:add'])" type="primary" @click="addBtn">新增</el-button>
      </el-form-item>
    </el-form>
    <!-- 表格 -->
     <el-table :height="tableHeight" :data="tableList" border stripe>
      <el-table-column prop="nickName" label="姓名"></el-table-column>
      <el-table-column prop="sex" label="性别">
        <template #default="scope">
          <el-tag v-if="scope.row.sex == '0'" type="primary" size="default" effect="dark">男</el-tag>
          <el-tag v-if="scope.row.sex == '1'" type="danger" size="default" effect="dark">女</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="电话"></el-table-column>
      <el-table-column prop="email" label="邮箱"></el-table-column>
      <el-table-column prop="username" label="用户名"></el-table-column>
      <el-table-column v-if="global.$hasPerm(['sys:user:edit','sys:user:reset','sys:user:delete'])" align="center" width="320" label="操作">
        <template #default="scope">
          <el-button type="primary" v-if="global.$hasPerm(['sys:user:edit'])" icon="Edit" size="default" @click="editBtn(scope.row)">编辑</el-button>
          <el-button type="warning" v-if="global.$hasPerm(['sys:user:reset'])" icon="Setting" size="default" @click="resetPasswordBtn(scope.row.userId)">重置密码</el-button>
          <el-button type="danger" v-if="global.$hasPerm(['sys:user:delete'])" icon="Delete" size="default" @click="deleteBtn(scope.row.userId)">删除</el-button>
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


    <!-- 新增,编辑弹窗 -->
    <SysDialog
      :title="dialog.title"
      :height="dialog.height"
      :width="dialog.width"
      :visible="dialog.visible"
      @on-close="onClose"
      @on-confirm="commit"
    >
      <template v-slot:content>
        <el-form :model="addModel" ref="addForm" :rules="rules" label-width="80px" :inline="false" size="default">
          <el-row>
            <el-col :span="12" :offset="0">
              <el-form-item prop="nickName" label="姓名: ">
                <el-input v-model="addModel.nickName"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12" :offset="0">
              <el-form-item prop="sex" label="性别: ">
                <el-radio-group v-model="addModel.sex">
                  <el-radio :value="'0'">男</el-radio>
                  <el-radio :value="'1'">女</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row>
            <el-col :span="12" :offset="0">
              <el-form-item prop="phone" label="电话: ">
                <el-input v-model="addModel.phone"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12" :offset="0">
              <el-form-item prop="email" label="邮箱: ">
                <el-input v-model="addModel.email"></el-input>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row>
            <el-col :span="12" :offset="0">
              <el-form-item prop="roleId" label="角色: ">
                <SelectChecked
                  ref="selectRef"
                  :options="options"
                  @selected="selected"
                  :bindValue="bindValue"
                >
                </SelectChecked>
              </el-form-item>
            </el-col>
            <el-col :span="12" :offset="0">
              <el-form-item prop="username" label="账户: ">
                <el-input v-model="addModel.username"></el-input>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row v-if="btnTags == '0'">
            <el-col :span="12" :offset="0">
              <el-form-item prop="password" label="密码: ">
                <el-input type="password" v-model="addModel.password"></el-input>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </template>
    </SysDialog>
  </el-main>
</template>

<script setup lang="ts">
import {ref,reactive,onMounted, nextTick} from 'vue'
import SysDialog from '@/components/SysDialog.vue'
import useDialog from '@/hooks/useDialog'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus';
import SelectChecked from '@/components/SelectChecked.vue'
import {getSelectApi} from '@/api/role/index'
import {addApi,getListApi,getRoleListApi,editApi,deleteApi,resetPasswordApi} from '@/api/user/index'
import {type SysUser} from '@/api/user/UserModel'
import useInstance from '@/hooks/useInstance'
//获取全局global
const {global} = useInstance()
//搜索栏绑定对象
const searchParm = reactive({
  phone:'',
  nickname:'',
  currentPage: 1,
  pageSize: 10,
  total:0
})
//弹框属性
const {dialog,onClose,onShow} = useDialog()
//搜索按钮点击事件
const searchBtn = ()=>{
  getList();
}
//重置按钮按钮点击事件
const resetBtn = ()=>{
  searchParm.nickname = '';
  searchParm.phone = '';
  searchParm.currentPage = 1;
  getList();
}
//0: 新增按钮 1: 编辑按钮
const btnTags = ref('')
//新增按钮点击事件
const addBtn = ()=>{
  btnTags.value = '0';
  dialog.title = '新增';
  dialog.height = 220;
  //显示弹框
  onShow();
  //清空下拉数据
  options.value = [];
  bindValue.value = [];
  //获取下拉数据
  getSelect()
  nextTick(()=>{
    //清空下拉的数据
    selectRef.value.clear()
  })
  //清空表单
  addForm.value?.resetFields();
}
//新增绑定的对象
const addModel = reactive({
  userId:'',
  username:'',
  password:'',
  phone:'',
  email:'',
  sex:'',
  nickName:'',
  roleId:''
})
//表单ref属性
const addForm = ref<FormInstance>()
//表单验证规则
const rules = reactive({
  nickName:[{
    required:true,
    trigger:['blur','change'],
    message:'请输入姓名',
  }],
  sex:[{
    required:true,
    trigger:['blur','change'],
    message:'请输入性别',
  }],
  email:[{
    required:true,
    trigger:['blur','change'],
    message:'请输入邮箱',
  }],
  phone:[{
    required:true,
    trigger:['blur','change'],
    message:'请输入电话',
  }],
  password:[{
    required:true,
    trigger:['blur','change'],
    min:12, max:64,
    message:'请输入12至64个字符的密码',
  }],
  username:[{
    required:true,
    trigger:['blur','change'],
    message:'请输入账号',
  }],
  roleId:[{
    required:true,
    trigger:['blur','change'],
    message:'请选择角色',
  }],
})
//提交表单
const commit = ()=>{
  //验证表单
  addForm.value?.validate( async (valid)=>{
    if (valid) {
      let res = null;
      if (btnTags.value == '0') {
        res = await addApi(addModel);
      } else {
        res = await editApi(addModel);
      }
      if (res && res.code == 200) {
        ElMessage.success(res.msg);
        getList();
        onClose();
      }
    }
  })
}
const selectRef = ref()
//下拉数据
let options = ref([])
//勾选的值
const selected = (value: Array<string | number>) => {
  //console.log(value.join(','));
  addModel.roleId = value.join(',')
}
//查询角色下拉数据
const getSelect = async()=>{
  let res = await getSelectApi()
  if (res && res.code == 200) {
    options.value = res.data;
  }
}
//表格高度
const tableHeight = ref(0)
//表格数据
const tableList = ref([])
//查询表格数据
const getList = async()=>{
  let res = await getListApi(searchParm);
  if(res && res.code==200) {
    tableList.value = res.data.records;
    searchParm.total = res.data.total;
  }
}
//用户拥有的角色id
const bindValue = ref([])
const roleIds = ref('')
//根据用户id查询角色
const getRoleList = async (userId:string)=>{
  let res = await getRoleListApi(userId);
  if (res && res.code == 200) {
    bindValue.value = res.data;
    roleIds.value = res.data.join(",");
  }
}
//编辑
const editBtn = async (row:SysUser)=>{
  btnTags.value = '1';
  dialog.title = '编辑';
  dialog.height = 220;
  //清空下拉数据
  options.value = [];
  //获取下拉数据
  await getSelect();
  //查询角色Id
  await getRoleList(row.userId);
  //显示弹框
  onShow();
  nextTick(()=>{
    //数据回显
    for (const key of Object.keys(addModel) as (keyof typeof addModel)[]) addModel[key] = row[key] || "";
    //设置角色的id
    addModel.roleId = roleIds.value;
    //编辑完后密码设为空，防止新增时候密码不为空
    addModel.password = '';
  });
  //清空表单
  addForm.value?.resetFields();
}
//重置密码
const resetPasswordBtn = async (userId:string) => {
  try {
    const {value} = await ElMessageBox.prompt('设置12至64个字符的新密码（UTF-8最多72字节）', '重置密码', {
      inputType: 'password', confirmButtonText: '重置', cancelButtonText: '取消',
      inputValidator: (value: string) => value && value.length >= 12 && value.length <= 64 && new TextEncoder().encode(value).length <= 72 || '密码不符合长度要求',
    });
    const res = await resetPasswordApi({userId,password:value});
    if (res && res.code == 200) ElMessage.success(res.msg);
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') { /* 请求层显示错误 */ }
  }
}
//删除
const deleteBtn = async (userId:string)=>{
  const confirm = await global.$myconfirm('确定删除该数据吗?');
  if (confirm) {
    let res = await deleteApi(userId);
    if (res && res.code == 200) {
      ElMessage.success(res.msg);
      getList();
    }
  }
}
//页容量改变时触发
const sizeChange = (size:number)=>{
  searchParm.pageSize = size;
  getList();
}
//页数改变时触发
const currentChange = (page:number) => {
  searchParm.currentPage = page;
  getList();
}
onMounted(()=>{
  getList()
  nextTick(()=>{
    tableHeight.value = window.innerHeight - 230
  })
})
</script>

<style scoped>
</style>
