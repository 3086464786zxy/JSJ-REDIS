<template>
  <div class="header-container">
    <div class="left">
      <Collapse></Collapse>
      <BreadCrumb></BreadCrumb>
    </div>
    <div class="right">
      <span style="padding-right: 15px; color: #fff;">欢迎您, {{name}}</span>
      <Loginout></Loginout>
    </div>
  </div>
</template>

<script setup lang="ts">
import Collapse  from './Collapse.vue'
import BreadCrumb from './BreadCrumb.vue'
import Loginout from './Loginout.vue'
import {useUserStore} from '@/store/user/index'
import {computed, onMounted} from 'vue'
const userStore = useUserStore()
const name = computed(()=>{
  return userStore.getNickName
})
</script>

<style scoped lang="scss">
.header-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;

  .left{
    display: flex;
    justify-content: center;
    align-items: center;
  }

  .right{
    display: flex;
    align-items: center;
  }
}
</style>
