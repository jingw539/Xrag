<template>
  <div class="ws-footer">
    <div class="footer-nav">
      <el-button size="small" plain :disabled="!prevCaseId" @click="handleNavigate('prev')">
        ← 上一份
      </el-button>
      <el-button size="small" plain :disabled="!nextCaseId" @click="handleNavigate('next')">
        下一份 →
      </el-button>
    </div>

    <!-- 内联决策摘要：置信度 -->
    <div class="footer-actions" v-if="currentReport">
      <el-button size="default" plain @click="handleSaveDraft"
        :loading="saving" :disabled="currentReport.reportStatus === 'SIGNED'">
        <el-icon><FolderOpened /></el-icon> 保存草稿
      </el-button>
      <el-button size="default" type="success" @click="handleSign"
        :loading="signing"
        v-if="currentReport.reportStatus !== 'SIGNED'">
        <el-icon><Check /></el-icon> 签发报告
      </el-button>
    </div>
    <div class="footer-actions" v-else-if="currentImage">
      <el-button size="default" type="primary" :loading="generating" @click="handleGenerate">
        <el-icon><MagicStick /></el-icon> 生成 AI 草稿
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { FolderOpened, Check, MagicStick } from '@element-plus/icons-vue'
const emit = defineEmits(['navigate', 'save-draft', 'sign', 'generate'])

defineProps({
  currentReport: { type: Object, default: null },
  currentImage: { type: Object, default: null },
  prevCaseId: { type: [Number, String], default: null },
  nextCaseId: { type: [Number, String], default: null },
  saving: { type: Boolean, default: false },
  signing: { type: Boolean, default: false },
  generating: { type: Boolean, default: false }
})

const handleNavigate = (dir) => emit('navigate', dir)
const handleSaveDraft = () => emit('save-draft')
const handleSign = () => emit('sign')
const handleGenerate = () => emit('generate')
</script>

<style scoped>
.ws-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: var(--xrag-panel);
  border-top: 1px solid var(--xrag-border);
  flex-shrink: 0;
  margin-top: 10px;
}
.footer-nav { display: flex; gap: 8px; }
.footer-actions { display: flex; gap: 8px; }

</style>
