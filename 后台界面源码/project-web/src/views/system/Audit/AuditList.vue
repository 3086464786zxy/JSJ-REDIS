<template>
  <el-main>
    <el-form :inline="true" :model="searchParm">
      <el-form-item label="请求编号"><el-input v-model="searchParm.requestId" maxlength="36" clearable /></el-form-item>
      <el-form-item label="用户ID"><el-input-number v-model="searchParm.userId" :min="1" :precision="0" /></el-form-item>
      <el-form-item label="阶段">
        <el-select v-model="searchParm.phase" clearable style="width: 150px">
          <el-option label="收到请求" value="RECEIVED" />
          <el-option label="变更已提交" value="CHANGED" />
          <el-option label="请求结束" value="COMPLETED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button icon="Search" @click="searchBtn">查询</el-button>
        <el-button icon="Close" @click="resetBtn">重置</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="tableList" stripe border>
      <el-table-column prop="occurredAt" label="时间(UTC)" width="210" />
      <el-table-column prop="requestId" label="请求编号" width="310" />
      <el-table-column label="阶段" width="120">
        <template #default="scope">{{ phaseLabels[scope.row.phase as AuditPhase] }}</template>
      </el-table-column>
      <el-table-column prop="userId" label="用户ID" width="100" />
      <el-table-column prop="targetId" label="目标ID" width="100" />
      <el-table-column prop="method" label="方法" width="90" />
      <el-table-column prop="path" label="接口" min-width="220" show-overflow-tooltip />
      <el-table-column prop="details" label="变更内容" min-width="220" show-overflow-tooltip />
      <el-table-column prop="httpStatus" label="HTTP状态" width="100" />
      <el-table-column prop="resultCode" label="业务状态" width="100" />
      <el-table-column prop="durationMs" label="耗时(ms)" width="110" />
      <el-table-column prop="remoteAddress" label="来源地址" width="150" />
    </el-table>
    <el-pagination v-model:current-page="searchParm.currentPage" v-model:page-size="searchParm.pageSize"
      :page-sizes="[10,20,50,100]" :total="searchParm.total" layout="total, sizes, prev, pager, next"
      @size-change="searchBtn" @current-change="loadList" />
  </el-main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { getListApi } from '@/api/audit'
import type { AuditEvent, AuditPhase } from '@/api/audit/AuditModel'
const phaseLabels: Record<AuditPhase,string> = { RECEIVED:'收到请求', CHANGED:'变更已提交', COMPLETED:'请求结束' }
const searchParm = reactive({ currentPage:1, pageSize:20, total:0, requestId:'', userId:undefined as number | undefined, phase:'' as AuditPhase | '' })
const tableList = ref<AuditEvent[]>([])
const loading = ref(false)
const loadList = async () => {
  loading.value = true
  try {
    const res = await getListApi({ currentPage:searchParm.currentPage, pageSize:searchParm.pageSize,
      requestId:searchParm.requestId || undefined, userId:searchParm.userId, phase:searchParm.phase || undefined })
    tableList.value = res.data.records
    searchParm.total = res.data.total
  } catch { /* Shared transport displays API errors. */ }
  finally { loading.value = false }
}
const searchBtn = () => { searchParm.currentPage = 1; void loadList() }
const resetBtn = () => { searchParm.requestId=''; searchParm.userId=undefined; searchParm.phase=''; searchBtn() }
onMounted(loadList)
</script>
