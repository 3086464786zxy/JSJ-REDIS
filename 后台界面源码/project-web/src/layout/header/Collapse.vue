<template>
  <el-icon @click="setCollapse" color="#FFF" style="cursor:pointer" size="30">
    <component :is="status ? Fold : Expand"></component>
  </el-icon>
</template>

<script setup lang="ts">
import {computed} from 'vue'
import { Fold, Expand } from "@element-plus/icons-vue";
import {useMenuStore} from '@/store/menu/index'
//获取store
const menuStore = useMenuStore()
//获取状态
const status = computed(()=>{
  return menuStore.getCollapse;
})
//切换图标点击事件
const setCollapse = () => {
  menuStore.setCollapse(!menuStore.getCollapse)
}
</script>

<style scoped>

</style>
