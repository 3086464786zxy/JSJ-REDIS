<template>
  <el-breadcrumb  class="bred" separator="/">
    <el-breadcrumb-item v-for="item in tabs">{{ item.meta.title }}</el-breadcrumb-item>
  </el-breadcrumb>
</template>

<script setup lang="ts">
import { ref, type Ref,watch} from "vue";
import { useRoute, type RouteLocationMatched } from "vue-router";
const route = useRoute()
//定义面包屑导航数据
const tabs : Ref<RouteLocationMatched[]> = ref([])
//获取面包屑数据
const getBredcrumb = ()=>{
  let mached = route.matched.filter((item)=>item.meta && item.meta.title)
  //获取第一个数据
  const first = mached[0]
  //判断第一个数据是否是首页，不是自己构造
  if( first.path !== '/dashboard') {
    mached = [{path:'/dashboard',meta:{title:'首页'}} as any].concat(mached)
  }
  tabs.value = mached;
}
getBredcrumb()
//监听当前路由
watch(
  ()=>route.path,
  ()=>getBredcrumb()
)
</script>

<style scoped lang="scss">
.bred {
  margin-left: 20px;
}
//修改字体颜色
:deep(.el-breadcrumb__inner) {
  color: #fff !important;
}

:deep(.el-breadcrumb__inner a) {
  color: #fff !important;
}

// 修改字体大小
// :deep(.el-breadcrumb__item) {
// font-size: 16px !important;
// }
</style>
