<template>
  <el-dialog v-model="visibleModel" title="AI 术语标准化" width="520px" align-center>
    <div v-if="items.length === 0" class="term-empty">
      未发现需要纠正的术语
    </div>
    <el-table v-else :data="items" size="small" class="term-table"
      @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="40" />
      <el-table-column type="index" label="#" width="40" />
      <el-table-column label="原始术语" min-width="140">
        <template #default="{ row }">
          <span class="term-original">{{ row.originalTerm }}</span>
        </template>
      </el-table-column>
      <el-table-column label="标准术语" min-width="140">
        <template #default="{ row }">
          <span class="term-suggested">{{ row.suggestedTerm }}</span>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="visibleModel = false">取消</el-button>
      <el-button type="primary" :disabled="selectedItems.length === 0"
        @click="handleConfirm">
        确认替换（{{ selectedItems.length }} 项）
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  items: { type: Array, default: () => [] },
  autoSelectAll: { type: Boolean, default: true }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const visibleModel = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const selectedItems = ref([])

const handleSelectionChange = (rows) => { selectedItems.value = rows }

const handleConfirm = async () => {
  emit('confirm', selectedItems.value)
}

watch(
  () => props.items,
  (list) => {
    if (props.autoSelectAll) selectedItems.value = [...(list || [])]
    else selectedItems.value = []
  },
  { immediate: true }
)

watch(
  () => props.modelValue,
  (open) => {
    if (open && props.autoSelectAll) selectedItems.value = [...(props.items || [])]
  }
)
</script>

<style scoped>
.term-empty {
  text-align: center;
  color: var(--xrag-text-faint);
  padding: 20px 0;
}
.term-table { width: 100%; }
.term-original { color: #f56c6c; text-decoration: line-through; }
.term-suggested { color: #52c41a; font-weight: 600; }
</style>
