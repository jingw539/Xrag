<template>
  <el-dialog v-model="visibleModel" title="新建病例" width="480px" align-center @open="handleOpen">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="检查号" prop="examNo">
        <el-input v-model="form.examNo" placeholder="如：CX-2024-0001" />
      </el-form-item>
      <el-form-item label="患者匿名ID" prop="patientAnonId">
        <el-input v-model="form.patientAnonId" placeholder="患者匿名标识" />
      </el-form-item>
      <el-form-item label="性别" prop="gender">
        <el-radio-group v-model="form.gender">
          <el-radio label="M">男</el-radio>
          <el-radio label="F">女</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="年龄" prop="age">
        <el-input-number v-model="form.age" :min="0" :max="150" style="width:100%" />
      </el-form-item>
      <el-form-item label="检查部位" prop="bodyPart">
        <el-input v-model="form.bodyPart" placeholder="如：胸部" />
      </el-form-item>
      <el-form-item label="科室">
        <el-input v-model="form.department" placeholder="如：影像科" />
      </el-form-item>
      <el-form-item label="检查时间" prop="examTime">
        <el-date-picker v-model="form.examTime" type="datetime"
          placeholder="选择检查时间" style="width:100%"
          format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visibleModel = false">取消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="loading">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'submit'])
const formRef = ref(null)
const form = ref({
  examNo: '', patientAnonId: '', gender: 'M', age: null,
  bodyPart: '胸部', department: '', examTime: ''
})
const rules = {
  examNo:       [{ required: true, message: '请输入检查号', trigger: 'blur' }],
  patientAnonId:[{ required: true, message: '请输入患者匿名ID', trigger: 'blur' }],
  gender:       [{ required: true, message: '请选择性别', trigger: 'change' }],
  age:          [{ required: true, message: '请输入年龄', trigger: 'blur' }],
  bodyPart:     [{ required: true, message: '请输入检查部位', trigger: 'blur' }],
  examTime:     [{ required: true, message: '请选择检查时间', trigger: 'change' }]
}

const visibleModel = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const handleOpen = () => {
  form.value = { examNo:'', patientAnonId:'', gender:'M', age:null, bodyPart:'胸部', department:'', examTime:'' }
  formRef.value?.clearValidate()
}

const handleSubmit = async () => {
  await formRef.value.validate()
  emit('submit', { ...form.value })
}
</script>
