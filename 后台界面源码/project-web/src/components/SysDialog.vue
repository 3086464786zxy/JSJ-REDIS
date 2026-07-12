<template>
  <div>
  <el-dialog
    :model-value="props.visible"
    :title="props.title"
    :width="props.width + 'px'"
    :before-close="onClose"
    append-to-body
    :close-on-click-modal="false"
  >
    <!-- 展示内容 -->
    <div class="container" :style="{ height: props.height + 'px' }">
      <slot name="content"></slot>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button type="danger" @click="onClose">取消</el-button>
        <el-button type="primary" @click="onConfirm">确定</el-button>
      </span>
    </template>
  </el-dialog>
</div>
</template>

<script setup lang="ts">
//定义参数类型
interface DialogProps {
  title?: string;
  visible: boolean;
  width?: number;
  height?: number;
}
/**
 * 接收父组件传递的数据
 * withDefaults:设置默认值
 * defineProps：接收父组件传递的参数
 */

const props = withDefaults(defineProps<DialogProps>(), {
  title: "标题",
  visible: false,
  width: 630,
  height: 280,
});
//注册事件
const emit = defineEmits(["onClose", "onConfirm"]);
//关闭弹框
const onClose = () => {
  emit("onClose");
};
//确定弹框
const onConfirm = () => {
  emit("onConfirm");
};
</script>

<style lang="scss">
.el-dialog {
  padding: 0 !important;
  border-radius: 7px !important;
  overflow: hidden !important;

  .el-dialog__header {
    background-color: #009688 !important;
    padding: 12px 20px !important;
    margin: 0 !important;
    display: flex;
    align-items: center;
    border-radius: 7px 7px 0 0;

    .el-dialog__title {
      color: #fff;
      font-size: 16px;
      font-weight: 600;
    }

    .el-dialog__headerbtn .el-dialog__close {
      color: #fff !important;
    }
  }

  .el-dialog__body {
    padding: 10px !important;
  }

  .el-dialog__footer {
    border-top: 1px solid #e8eaec !important;
    padding: 10px !important;
  }
}

.container {
  overflow-y: auto;
}
</style>
