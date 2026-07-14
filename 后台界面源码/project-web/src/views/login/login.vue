<template>
  <div class="logincontainer">
    <el-form class="loginform" :model="loginModel" ref="form" :rules="rules" :inline="false" size="large">
      <el-form-item label="">
        <div class="logintitle">系统登录</div>
      </el-form-item>
      <el-form-item prop="username">
        <el-input placeholder="请输入账户" v-model="loginModel.username"></el-input>
      </el-form-item>
      <el-form-item prop="password">
        <el-input placeholder="请输入密码" type="password" v-model="loginModel.password"></el-input>
      </el-form-item>
      <el-form-item prop="code">
        <el-row style="width: 100%;" :gutter="0">
          <el-col :span="16" :offset="0">
              <el-input placeholder="请输入验证码" v-model="loginModel.code"></el-input>
          </el-col>
          <el-col :span="8" style="padding-left: 10px" :offset="0">
            <img class="images" @click="getImg" :src="imgsrc"/>
          </el-col>
        </el-row>
      </el-form-item>
      <el-row :gutter="20">
        <el-col :span="12" :offset="0">
          <el-button class="mybtn" type="primary" @click="commit">登录</el-button>
        </el-col>
        <el-col :span="12" :offset="0">
          <el-button type="danger" plain class="mybtn" @click="resetBtn">重置</el-button>
        </el-col>
      </el-row>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import {ref,reactive,onMounted} from 'vue'
import {getImgApi,loginApi} from '@/api/user/index'
import {type FormInstance } from 'element-plus'
import {useUserStore} from '@/store/user/index'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
const router = useRouter()
const userStore = useUserStore()
//表单绑定对象
const loginModel = reactive({
  username:'',
  password:'',
  captchaId:'',
  code:''
})
//表单ref属性
const form = ref<FormInstance>()
//表单验证规则
const rules = reactive({
  username: [{
    required: true,
    trigger: ['blur', 'change'],
    message: '请输入账号',
  }],
  password: [{
    required: true,
    trigger: ['blur', 'change'],
    message: '请输入密码',
  }],
  code: [{
    required: true,
    trigger: ['blur', 'change'],
    message: '请输入验证码',
  }],
})
const imgsrc = ref('')
//获取验证码
const getImg = async ()=>{
  let res = await getImgApi()
  if (res && res.code == 200) {
    loginModel.captchaId = res.data.captchaId;
    imgsrc.value = res.data.image;
  }
}
const commit = ()=>{
  form.value?.validate( async (valid)=>{
    if (valid) {
      let loginSuccess = false
      try {
        let res = await loginApi(loginModel);
        if (res && res.code == 200) {
          loginSuccess = true
          userStore.setUserId(res.data.userId);
          userStore.setNickName(res.data.nickName);
          userStore.setToken(res.data.token);
          router.push({path:'/'});
        }
      } catch {
        // 错误提示由 Axios 响应拦截器统一处理。
      } finally {
        if (!loginSuccess) {
          // 验证码在后端只允许使用一次，登录失败后必须重新获取。
          loginModel.code = ''
          await getImg()
        }
      }
    }
  })
}
const resetBtn = () => {
  form.value?.resetFields();
}
//登录提交
onMounted(()=>{
  getImg()
})
</script>

<style scoped>
.logincontainer{
  height:100%;
  background-origin: #FFF;
  background-image: url('../../assets/login.png');
  display: flex;
  justify-content: center;
  align-items: center;
}
.loginform{
  height:320px;
  width: 450px;
  padding: 20px 35px;
  border-radius: 10px;
  background-color: #FFF;

  .logintitle{
    display: flex;
    justify-content: center;
    color: #606266;
    width: 100%;
    font-size: 24px;
    font-weight: 600;
  }

  .images {
    height: 40px;
    width: 100%;
    cursor: pointer;
  }

  .mybtn{
    width: 100%
  }
}
</style>
