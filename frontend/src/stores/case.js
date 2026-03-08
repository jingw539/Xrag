import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useCaseStore = defineStore('case', () => {
  const currentCase = ref(null)
  const caseList = ref([])
  const total = ref(0)

  const setCurrentCase = (caseData) => {
    currentCase.value = caseData
  }

  const setCaseList = (list, totalCount) => {
    caseList.value = list
    total.value = totalCount
  }

  return {
    currentCase,
    caseList,
    total,
    setCurrentCase,
    setCaseList
  }
})
