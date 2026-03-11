<template>
  <el-dialog v-model="visibleModel" title="标记为典型病例" width="420px" align-center @open="handleOpen">
    <el-form :model="form" label-width="80px">
      <el-form-item label="分类标签">
        <el-input v-model="form.typicalTags" placeholder="如: 肺炎,结节（逗号分隔）" />
      </el-form-item>
      <el-form-item label="备注说明">
        <el-input v-model="form.typicalRemark" type="textarea" :rows="3" placeholder="请输入典型影像学特征说明" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visibleModel = false">取消</el-button>
      <el-button type="warning" @click="handleConfirm" :loading="loading">确认标记</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const visibleModel = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const form = ref({ typicalTags: '', typicalRemark: '' })

const handleOpen = () => {
  form.value = { typicalTags: '', typicalRemark: '' }
}

const handleConfirm = async () => {
  emit('confirm', { ...form.value })
}
</script>
