<template>
  <div class="viewer-toolbar">
    <span class="viewer-filename" :title="viewerShortcutHint">{{ viewerHeaderText }}</span>
    <div class="viewer-tools">
      <button class="tool-btn" title="放大（+）" @click="handleZoom(0.2)"><el-icon><ZoomIn /></el-icon></button>
      <button class="tool-btn" title="缩小（-）" @click="handleZoom(-0.2)"><el-icon><ZoomOut /></el-icon></button>
      <button class="tool-btn" title="顺时针旋转（R）" @click="handleRotate(90)"><el-icon><RefreshRight /></el-icon></button>
      <button class="tool-btn" title="逆时针旋转（Shift+R）" @click="handleRotate(-90)"><el-icon><RefreshLeft /></el-icon></button>
      <button class="tool-btn" title="重置视图（0 / 双击）" @click="handleReset"><el-icon><FullScreen /></el-icon></button>
      <button class="tool-btn" title="撤销（Ctrl+Z）" :disabled="!canUndo" @click="handleUndo">撤</button>
      <button class="tool-btn" title="重做（Ctrl+Y）" :disabled="!canRedo" @click="handleRedo">重</button>
      <button class="tool-btn" :class="compareMode && 'tool-active'" title="双屏对比" :disabled="!canCompare" @click="handleToggleCompare">对比</button>
      <span class="tool-sep-v"></span>
      <button :class="['tool-btn', annoTool === 'select' && 'tool-active']" title="选择标注（V）" @click="handleSelectTool"><el-icon><Pointer /></el-icon></button>
      <button :class="['tool-btn', annoTool === 'rect' && 'tool-active']" title="矩形标注（M）" @click="handleRectTool"><el-icon><Crop /></el-icon></button>
      <button :class="['tool-btn', annoTool === 'line' && 'tool-active']" title="双点测距（L）" @click="handleLineTool">尺</button>
      <span class="tool-sep-v"></span>
      <button :class="['tool-btn', 'layer-toggle', showAiAnnos && 'tool-active']" :title="`AI 标注层（${aiAnnotationCount}处）`" @click="handleToggleAi">AI</button>
      <button :class="['tool-btn', 'layer-toggle', showDoctorAnnos && 'tool-active']" :title="`人工标注层（${doctorAnnotationCount}处）`" @click="handleToggleDoctor">医</button>
      <button class="tool-btn" :class="selectedAnnoId && 'tool-danger'" title="删除选中标注" :disabled="!selectedAnnoId" @click="handleDeleteSelected"><el-icon><Delete /></el-icon></button>
    </div>
  </div>
</template>

<script setup>
import {
  ZoomIn,
  ZoomOut,
  RefreshRight,
  RefreshLeft,
  FullScreen,
  Pointer,
  Crop,
  Delete
} from '@element-plus/icons-vue'
const emit = defineEmits([
  'zoom',
  'rotate',
  'reset',
  'undo',
  'redo',
  'toggle-compare',
  'select-tool',
  'rect-tool',
  'line-tool',
  'toggle-ai',
  'toggle-doctor',
  'delete-selected'
])

defineProps({
  viewerHeaderText: { type: String, default: '' },
  viewerShortcutHint: { type: String, default: '' },
  canUndo: { type: Boolean, default: false },
  canRedo: { type: Boolean, default: false },
  canCompare: { type: Boolean, default: false },
  compareMode: { type: Boolean, default: false },
  annoTool: { type: String, default: 'select' },
  showAiAnnos: { type: Boolean, default: true },
  showDoctorAnnos: { type: Boolean, default: true },
  aiAnnotationCount: { type: Number, default: 0 },
  doctorAnnotationCount: { type: Number, default: 0 },
  selectedAnnoId: { type: [Number, String, null], default: null }
})

const handleZoom = (delta) => emit('zoom', delta)
const handleRotate = (delta) => emit('rotate', delta)
const handleReset = () => emit('reset')
const handleUndo = () => emit('undo')
const handleRedo = () => emit('redo')
const handleToggleCompare = () => emit('toggle-compare')
const handleSelectTool = () => emit('select-tool')
const handleRectTool = () => emit('rect-tool')
const handleLineTool = () => emit('line-tool')
const handleToggleAi = () => emit('toggle-ai')
const handleToggleDoctor = () => emit('toggle-doctor')
const handleDeleteSelected = () => emit('delete-selected')
</script>

<style scoped>
.viewer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--xrag-border);
  background: rgba(15, 25, 35, 0.9);
}

.viewer-filename {
  font-size: 12px;
  color: var(--xrag-text-soft);
  max-width: 40%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.viewer-tools {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.tool-btn {
  height: 26px;
  min-width: 26px;
  border-radius: 6px;
  border: 1px solid var(--xrag-border);
  background: rgba(255, 255, 255, 0.02);
  color: var(--xrag-text);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 12px;
  transition: all .15s;
}

.tool-btn:hover {
  background: rgba(74, 158, 255, 0.12);
  border-color: rgba(74, 158, 255, 0.3);
}

.tool-active {
  background: rgba(74, 158, 255, 0.2);
  border-color: rgba(74, 158, 255, 0.5);
  color: #f4f8ff;
}

.tool-danger {
  background: rgba(245, 34, 45, 0.2);
  border-color: rgba(245, 34, 45, 0.5);
  color: #ffd6d6;
}

.tool-sep-v {
  width: 1px;
  height: 18px;
  background: rgba(111, 134, 166, 0.35);
  margin: 0 6px;
}

.layer-toggle {
  font-weight: 600;
}

@media (max-width: 1024px) {
  .viewer-filename {
    max-width: 100%;
  }
}

@media (max-width: 768px) {
  .viewer-toolbar {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .viewer-tools {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
