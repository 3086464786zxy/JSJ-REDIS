<template>
  <div class="logo">
    <img :src="MenuLogo"></img>
    <span v-if="show" class="logo-title">{{ title }}</span>
  </div>
</template>

<script setup lang="ts">
import MenuLogo from '@/assets/MenuLogo.png'
import {ref, computed} from 'vue'
import { useMenuStore } from '@/store/menu'
const title = ref('后台管理')
//获取Store
const menuStore = useMenuStore()
const show = computed(()=>{
  return menuStore.getCollapse
})

</script>

<style scoped lang="scss">
.logo {
  display: flex;
  width: 100%;
  height: 60px;
  background-color: #2b2f3a;
  text-align:center;
  cursor: pointer;
  align-items: center;
  img{
    width: 40px;
    height: 40px;
    //margin: 0 auto;
    margin-left: 10px;
    margin-right: 5px;
  }
  .logo-title{
    color: #fff;
    font-weight: 800;
    line-height: 60px;
    font-size: 20px;
    font-family: FangSong;
  }
}
</style>
