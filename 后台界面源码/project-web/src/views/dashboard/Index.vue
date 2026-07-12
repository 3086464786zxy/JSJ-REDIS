<template>
  <el-main :style="{ height: mianHeight + 'px' }">
    <div style="display: flex;">
      <el-card style="flex: 1;">
        <template #header>
          <div class="card-header">
            <span>订单统计</span>
          </div>
        </template>
        <div ref="myChart" :style="{ width: '400px', height: '300px' }"></div>
      </el-card>
      <el-card style="margin-left: 20px;flex: 1;">
        <template #header>
          <div class="card-header">
            <span>热门商品</span>
          </div>
        </template>
        <div ref="myChart1" :style="{ width: '400px', height: '300px' }"></div>
      </el-card>
      <el-card style="margin-left: 20px;flex: 1;">
        <template #header>
          <div class="card-header">
            <span>最受欢迎</span>
          </div>
        </template>
        <div ref="myChart2" :style="{ width: '400px', height: '300px' }"></div>
      </el-card>
    </div>
  </el-main>
</template>
<script setup lang='ts'>
import { ref, nextTick, onMounted, reactive, onBeforeUnmount } from 'vue'
import useInstance from '@/hooks/useInstance';
const mianHeight = ref(0)
const { global } = useInstance()
const myChart = ref<HTMLElement>();
const myChart1 = ref<HTMLElement>();
const myChart2 = ref<HTMLElement>();
//柱状图
const charts1 = () => {
  //初始化echarts
  const echartInstance = global.$echarts.init(myChart.value);
  //配置项
  let option = reactive({
    xAxis: {
      type: 'category',
      data: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        data: [120, 200, 150, 80, 70, 110, 130],
        type: 'bar'
      }
    ]
  });
  //通过axios发送请求，获取数据，设置到上面的option的x轴和y轴的数据里面即可
  echartInstance.setOption(option)
}
//饼图
const charts2 = () => {
  const myChart = global.$echarts.init(myChart1.value);
  let option = reactive({
    title: {
      subtext: 'Fake Data',
      left: 'center'
    },
    tooltip: {
      trigger: 'item'
    },
    legend: {
      orient: 'vertical',
      left: 'left'
    },
    series: [
      {
        name: 'Access From',
        type: 'pie',
        radius: '50%',
        data: [
          { value: 1048, name: 'Search Engine' },
          { value: 735, name: 'Direct' },
          { value: 580, name: 'Email' },
          { value: 484, name: 'Union Ads' },
          { value: 300, name: 'Video Ads' }
        ],
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  });
  //通过axios发送请求，获取数据，设置到上面的option的x轴和y轴的数据里面即可
  myChart.setOption(option)
}
//环图
const charts3 = () => {
  const myChart = global.$echarts.init(myChart2.value);
  let option = reactive({
    tooltip: {
      trigger: 'item'
    },
    legend: {
      top: '5%',
      left: 'center'
    },
    series: [
      {
        name: 'Access From',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        label: {
          show: false,
          position: 'center'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: '40',
            fontWeight: 'bold'
          }
        },
        labelLine: {
          show: false
        },
        data: [
          { value: 1048, name: 'Search Engine' },
          { value: 735, name: 'Direct' },
          { value: 580, name: 'Email' },
          { value: 484, name: 'Union Ads' },
          { value: 300, name: 'Video Ads' }
        ]
      }
    ]
  });
  //通过axios发送请求，获取数据，设置到上面的option的x轴和y轴的数据里面即可
  myChart.setOption(option)
}
onMounted(() => {
  charts1();
  charts2();
  charts3();
  nextTick(() => {
    mianHeight.value = window.innerHeight - 100
  })
})
</script>
<style scoped lang='scss'></style>
