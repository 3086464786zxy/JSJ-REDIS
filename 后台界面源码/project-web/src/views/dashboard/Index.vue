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
import { init, use, type ECharts } from 'echarts/core'
import { BarChart, PieChart } from 'echarts/charts'
import { GridComponent, TitleComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
use([BarChart, PieChart, GridComponent, TitleComponent, TooltipComponent, LegendComponent, CanvasRenderer])
const chartInstances: ECharts[] = []
const initChart = (element: HTMLElement) => {
  const chart = init(element)
  chartInstances.push(chart)
  return chart
}
let resizeObserver: ResizeObserver | undefined
const mianHeight = ref(0)
const myChart = ref<HTMLElement>();
const myChart1 = ref<HTMLElement>();
const myChart2 = ref<HTMLElement>();
//柱状图
const charts1 = () => {
  //初始化echarts
  const echartInstance = initChart(myChart.value!);
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
  const myChart = initChart(myChart1.value!);
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
  const myChart = initChart(myChart2.value!);
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
            fontSize: 40,
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
  resizeObserver = new ResizeObserver(() => chartInstances.forEach(chart => chart.resize()))
  for (const element of [myChart.value, myChart1.value, myChart2.value]) {
    if (element) resizeObserver.observe(element)
  }
  nextTick(() => {
    mianHeight.value = window.innerHeight - 100
  })
})
onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chartInstances.forEach(chart => chart.dispose())
})
</script>
<style scoped lang='scss'></style>
