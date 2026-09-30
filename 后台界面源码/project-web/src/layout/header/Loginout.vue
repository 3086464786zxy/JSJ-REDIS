<template>
  <el-dropdown>
    <span class="el-dropdown-link">
      <img class="userimg" src="@/assets/user2.png"/>
    </span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item @click="updateBtn">修改密码</el-dropdown-item>
        <el-dropdown-item @click="loginoutBtn">退出登录</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
  <!-- 修改密码 -->
  <SysDialog
  :title="dialog.title"
  :width="dialog.width"
  :height="dialog.height"
  :visible="dialog.visible"
  @on-close="onClose"
  @on-confirm="commit"
  >
  <template v-slot:content>
    <el-form :model="upModel" ref="form" :rules="rules" label-width="80px" :inline="false" size="default">
      <el-form-item prop="oldPassword" label="原密码">
        <el-input type="password" v-model="upModel.oldPassword"></el-input>
      </el-form-item>
      <el-form-item prop="password" label="新密码">
        <el-input type="password" v-model="upModel.password"></el-input>
      </el-form-item>
      <el-form-item prop="confirm" label="确认密码">
        <el-input type="password" v-model="upModel.confirm"></el-input>
      </el-form-item>
    </el-form>

  </template>
  </SysDialog>
</template>

<script setup lang="ts">
import SysDialog from '@/components/SysDialog.vue';
import useDialog from '@/hooks/useDialog'
import {ref,reactive} from 'vue'
import {type FormInstance } from 'element-plus';
import { ElMessage } from 'element-plus';
import {updatePasswordApi} from '@/api/user/index'
import { useUserStore } from '@/store/user';
import useInstance from '@/hooks/useInstance';
import { endSession } from '@/http';
const {global} = useInstance()
const userStore = useUserStore()
//表单属性
const form = ref<FormInstance>()
//弹框属性
const {dialog,onClose,onShow} = useDialog()
//修改密码
const updateBtn = () => {
  dialog.title = '修改密码';
  dialog.height = 180;
  onShow()
  form.value?.resetFields();
}
const loginoutBtn = async () => {
  //信息确定
  const confirm = await global.$myconfirm('确定退出登录吗?');
  if (confirm) {
    endSession();
  }
}
//表单对象
const upModel = reactive({
  userId: '',
  oldPassword:'',
  password:'',
  confirm:''
})
//表单验证规则
const rules = reactive({
  oldPassword:[{
    required:true,
    trigger:['blur','change'],
    message:'请输入原密码',
  }],
  password:[{
    required:true,
    trigger:['blur','change'],
    message:'请输入新密码',
  }, {
    validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
      callback(value.length >= 12 && value.length <= 64 && new TextEncoder().encode(value).length <= 72
        ? undefined : new Error('密码须为12至64个字符，UTF-8编码不超过72字节'))
    },
    trigger: ['blur', 'change'],
  }],
  confirm:[{
    required:true,
    trigger:['blur','change'],
    message:'请输入确定密码',
  }],
})
const commit = () => {
  upModel.userId = userStore.getUserId;
  form.value?.validate(async (valid)=>{
    if (valid) {
      //判断新密码和确定密码是否一致
      if (upModel.password != upModel.confirm) {
        ElMessage.warning('新密码和确定密码不一致!');
        return;
      }
      let res = await updatePasswordApi(upModel);
      if (res && res.code == 200) {
        ElMessage.success(res.msg)
        endSession();
      }
    }
  })
}
</script>

<style scoped lang="scss">
.el-dropdown-link:focus{
  cursor: pointer;
  outline: none;
}
.userimg{
  height: 48px;
  width: 48px;
  border-radius: 50%;
  cursor: pointer;
}
</style>
