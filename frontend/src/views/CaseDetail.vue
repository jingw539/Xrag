<template>
  <div v-loading="pageLoading" element-loading-text="加载中...">
    <div class="detail-header">
      <el-breadcrumb>
        <el-breadcrumb-item :to="{ path: '/cases' }">病例管理</el-breadcrumb-item>
        <el-breadcrumb-item>{{ caseInfo.examNo || '病例详情' }}</el-breadcrumb-item>
      </el-breadcrumb>
      <div style="display:flex;gap:8px;align-items:center">
        <el-tag :type="statusType(caseInfo.reportStatus)" size="large">{{ statusLabel(caseInfo.reportStatus) }}</el-tag>
        <el-button v-if="caseInfo.isTypical" type="warning" plain size="small" @click="handleUnmarkTypical">
          <el-icon><StarFilled /></el-icon> 取消典型
        </el-button>
        <el-button v-else type="warning" size="small" @click="showTypicalDialog = true">
          <el-icon><Star /></el-icon> 标为典型
        </el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="detail-tabs">
      <!-- Tab 1: 病例信息 + 影像 -->
      <el-tab-pane label="📋 病例信息" name="info">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-card header="基本信息">
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="检查号">{{ caseInfo.examNo }}</el-descriptions-item>
                <el-descriptions-item label="患者ID">{{ caseInfo.patientAnonId }}</el-descriptions-item>
                <el-descriptions-item label="性别">{{ caseInfo.gender === 'M' ? '男' : '女' }}</el-descriptions-item>
                <el-descriptions-item label="年龄">{{ caseInfo.age }} 岁</el-descriptions-item>
                <el-descriptions-item label="检查部位">{{ caseInfo.bodyPart }}</el-descriptions-item>
                <el-descriptions-item label="科室">{{ caseInfo.department }}</el-descriptions-item>
                <el-descriptions-item label="检查时间" :span="2">{{ formatDate(caseInfo.examTime) }}</el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card header="影像管理">
              <el-upload drag :before-upload="beforeUpload" :http-request="handleUpload"
                accept=".jpg,.jpeg,.png,.dcm" :show-file-list="false">
                <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                <div class="el-upload__text">拖拽或 <em>点击上传</em> 影像</div>
                <template #tip><div class="el-upload__tip">JPG/PNG/DICOM，≤50MB</div></template>
              </el-upload>
              <div class="image-gallery" v-if="images.length">
                <div v-for="img in images" :key="img.imageId"
                  :class="['image-item', selectedImageId === img.imageId && 'selected']"
                  @click="selectedImageId = img.imageId">
                  <el-image :src="img.thumbnailUrl || img.fullUrl" fit="cover"
                    :preview-src-list="images.map(i => i.fullUrl)" style="width:100%;height:100%">
                    <template #error><div class="image-error"><el-icon><Picture /></el-icon></div></template>
                  </el-image>
                  <div class="image-overlay">
                    <span>{{ img.viewPosition || '正位' }}</span>
                    <el-button link size="small" style="color:white" @click.stop="deleteImg(img)">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </div>
                  <el-icon v-if="selectedImageId === img.imageId" class="check-icon"><Select /></el-icon>
                </div>
              </div>
              <el-empty v-else description="暂无影像，请上传" :image-size="60" style="padding:10px 0" />
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- Tab 2: 报告 -->
      <el-tab-pane label="📝 报告" name="report">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-card header="操作">
              <el-space direction="vertical" style="width:100%">
                <el-button type="primary" :disabled="!selectedImageId" :loading="generating"
                  style="width:100%" @click="handleGenerate">
                  <el-icon><MagicStick /></el-icon>
                  {{ report ? '重新生成报告' : '生成AI报告' }}
                </el-button>
                <el-button v-if="report && report.reportStatus !== 'SIGNED'" type="success"
                  style="width:100%" @click="handleSign">
                  <el-icon><Check /></el-icon> 签发报告
                </el-button>
                <el-button v-if="report" type="info" plain style="width:100%" @click="handleAnalyzeTerms" :loading="analyzingTerms">
                  <el-icon><EditPen /></el-icon> 术语校正分析
                </el-button>
              </el-space>
              <el-alert v-if="!selectedImageId" type="warning" :closable="false"
                description="请先上传并选择影像" style="margin-top:12px" />
            </el-card>
            <el-card header="RAG 参考病例" style="margin-top:16px" v-if="retrieval">
              <div v-for="(c, i) in retrieval.similarCases" :key="i" class="rag-case">
                <div class="rag-case-header">
                  <span>参考 {{ i + 1 }}：{{ c.examNo }}</span>
                  <el-tag size="small">{{ c.similarityScore }}</el-tag>
                </div>
                <div v-if="c.impression" class="rag-impression">{{ c.impression }}</div>
              </div>
              <el-empty v-if="!retrieval.similarCases?.length" description="暂无相似病例" :image-size="40" />
            </el-card>
          </el-col>
          <el-col :span="16">
            <el-card v-if="report">
              <template #header>
                <div style="display:flex;justify-content:space-between;align-items:center">
                  <span>报告内容</span>
                  <div>
                    <el-tag :type="statusType(report.reportStatus)">{{ statusLabel(report.reportStatus) }}</el-tag>
                    <el-button v-if="report.reportStatus !== 'SIGNED'" link type="primary"
                      style="margin-left:8px" @click="handleSaveDraft" :loading="saving">保存草稿</el-button>
                  </div>
                </div>
              </template>
              <el-form label-width="80px">
                <el-form-item label="AI置信度">
                  <el-progress :percentage="Math.round((report.modelConfidence || 0) * 100)"
                    :color="(report.modelConfidence || 0) > 0.8 ? '#67c23a' : '#e6a23c'" />
                </el-form-item>
                <el-form-item label="影像所见">
                  <el-input v-model="editFindings" type="textarea" :rows="6"
                    :disabled="report.reportStatus === 'SIGNED'" />
                </el-form-item>
                <el-form-item label="诊断意见">
                  <el-input v-model="editImpression" type="textarea" :rows="4"
                    :disabled="report.reportStatus === 'SIGNED'" />
                </el-form-item>
                <el-form-item v-if="report.reportStatus === 'SIGNED'" label="签发医生">
                  <span>{{ report.doctorName }} · {{ formatDate(report.signTime) }}</span>
                </el-form-item>
              </el-form>
              <el-collapse v-if="editHistory.length">
                <el-collapse-item title="修改历史" name="h">
                  <el-timeline>
                    <el-timeline-item v-for="h in editHistory" :key="h.historyId"
                      :timestamp="formatDate(h.editedAt)" placement="top">
                      <el-tag size="small">{{ h.editType }}</el-tag> {{ h.editNote }}
                    </el-timeline-item>
                  </el-timeline>
                </el-collapse-item>
              </el-collapse>
            </el-card>
            <el-empty v-else description="请选择影像后点击「生成AI报告」" style="margin-top:40px" />
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- Tab 3: CheXbert评测 -->
      <el-tab-pane label="📊 CheXbert评测" name="eval">
        <el-card v-if="evalResult">
          <template #header>
            <div style="display:flex;justify-content:space-between">
              <span>评测结果</span>
              <el-button type="primary" size="small" @click="handleTriggerEval" :loading="evaluating">重新评测</el-button>
            </div>
          </template>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-descriptions title="评测指标" :column="2" border size="small">
                <el-descriptions-item label="F1 Score">
                  <el-tag :type="evalResult.f1Score > 0.7 ? 'success' : 'warning'">{{ ((evalResult.f1Score || 0) * 100).toFixed(1) }}%</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="BLEU-4">{{ ((evalResult.bleu4Score || 0) * 100).toFixed(1) }}%</el-descriptions-item>
                <el-descriptions-item label="Precision">{{ ((evalResult.precisionScore || 0) * 100).toFixed(1) }}%</el-descriptions-item>
                <el-descriptions-item label="ROUGE-L">{{ ((evalResult.rougeLScore || 0) * 100).toFixed(1) }}%</el-descriptions-item>
                <el-descriptions-item label="Recall">{{ ((evalResult.recallScore || 0) * 100).toFixed(1) }}%</el-descriptions-item>
                <el-descriptions-item label="质量评级">
                  <el-tag :type="gradeType(evalResult.qualityGrade)">{{ evalResult.qualityGrade }}</el-tag>
                </el-descriptions-item>
              </el-descriptions>
            </el-col>
            <el-col :span="12">
              <div style="font-weight:600;margin-bottom:8px">额外预测标签</div>
              <el-tag v-for="label in evalResult.extraLabels" :key="label" type="danger" style="margin:3px">{{ label }}</el-tag>
              <div v-if="evalResult.missingLabels?.length" style="margin-top:8px">
                <div style="font-weight:600;margin-bottom:4px;color:#e6a23c">漏检标签</div>
                <el-tag v-for="label in evalResult.missingLabels" :key="label" type="warning" style="margin:3px">{{ label }}</el-tag>
              </div>
              <el-empty v-if="!evalResult.extraLabels?.length && !evalResult.missingLabels?.length" description="标签匹配良好" :image-size="40" />
              <el-alert v-if="caseAlerts.length" type="error" :closable="false" style="margin-top:12px"
                :title="`⚠️ ${caseAlerts.length} 条危急值预警`"
                :description="caseAlerts.map(a => a.labelType).join('、')" />
            </el-col>
          </el-row>
        </el-card>
        <div v-else style="text-align:center;padding:40px">
          <el-empty description="暂无评测结果" />
          <el-button type="primary" @click="handleTriggerEval" :loading="evaluating" style="margin-top:16px">触发评测</el-button>
        </div>
      </el-tab-pane>

      <!-- Tab 4: 术语校正 -->
      <el-tab-pane label="📌 术语校正" name="terms">
        <el-card>
          <template #header>
            <div style="display:flex;justify-content:space-between">
              <span>术语规范化建议</span>
              <el-button type="primary" size="small" @click="handleAnalyzeTerms" :loading="analyzingTerms">重新分析</el-button>
            </div>
          </template>
          <el-table :data="termCorrections" border>
            <el-table-column prop="originalTerm" label="原始术语" width="160" />
            <el-table-column prop="suggestedTerm" label="建议术语" width="160">
              <template #default="{ row }"><span style="color:#67c23a;font-weight:600">{{ row.suggestedTerm }}</span></template>
            </el-table-column>
            <el-table-column prop="contextSentence" label="上下文" show-overflow-tooltip />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="termStatusType(row.isAccepted)" size="small">
                  {{ termStatusLabel(row.isAccepted) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" align="center">
              <template #default="{ row }">
                <template v-if="row.isAccepted === 0 || row.isAccepted == null">
                  <el-button size="small" type="success" @click="handleAcceptTerm(row)">采纳</el-button>
                  <el-button size="small" @click="handleDismissTerm(row)">忽略</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!termCorrections.length" description="暂无术语校正建议" />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 标为典型病例对话框 -->
    <el-dialog v-model="showTypicalDialog" title="标记为典型病例" width="400px">
      <el-form label-width="80px">
        <el-form-item label="标签"><el-input v-model="typicalForm.tags" placeholder="如：气胸,肺炎" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="typicalForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTypicalDialog = false">取消</el-button>
        <el-button type="primary" @click="handleMarkTypical">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCaseById, markTypical } from '@/api/case'
import { listImages, uploadImage, deleteImage } from '@/api/image'
import { generateReport, regenerateReport, saveDraft, signReport, listReports, getReport } from '@/api/report'
import { searchRetrieval } from '@/api/retrieval'
import { triggerEval, getEvalByReportId } from '@/api/eval'
import { analyzeTerms, acceptCorrection, dismissCorrection } from '@/api/term'
import { getAlertsByCaseId } from '@/api/alert'

const route = useRoute()
const caseId = computed(() => Number(route.params.id))

const activeTab = ref('info')
const pageLoading = ref(false)
const caseInfo = ref({})
const images = ref([])
const selectedImageId = ref(null)
const report = ref(null)
const editFindings = ref('')
const editImpression = ref('')
const editHistory = ref([])
const retrieval = ref(null)
const evalResult = ref(null)
const termCorrections = ref([])
const caseAlerts = ref([])
const generating = ref(false)
const saving = ref(false)
const evaluating = ref(false)
const analyzingTerms = ref(false)
const showTypicalDialog = ref(false)
const typicalForm = reactive({ tags: '', remark: '' })

const loadAll = async () => {
  pageLoading.value = true
  try {
    const [caseRes, imgRes] = await Promise.all([getCaseById(caseId.value), listImages(caseId.value)])
    caseInfo.value = caseRes.data
    images.value = imgRes.data || []
    if (images.value.length) selectedImageId.value = images.value[0].imageId
    try {
      const listRes = await listReports({ caseId: caseId.value, pageSize: 1, page: 1 })
      const list = listRes.data?.list || []
      if (list.length) {
        await loadReportDetail(list[0].reportId)
      }
    } catch (_) {}
    try {
      const alertRes = await getAlertsByCaseId(caseId.value)
      caseAlerts.value = alertRes.data || []
    } catch (_) {}
  } finally {
    pageLoading.value = false
  }
}

const loadReportDetail = async (reportId) => {
  const res = await getReport(reportId)
  const detail = res.data
  report.value = detail
  editFindings.value = detail.finalFindings || detail.aiFindings || ''
  editImpression.value = detail.finalImpression || detail.aiImpression || ''
  editHistory.value = detail.editHistory || []
  if (detail.latestEval) evalResult.value = detail.latestEval
  if (detail.termCorrections) termCorrections.value = detail.termCorrections
}

const beforeUpload = (file) => {
  if (file.size > 50 * 1024 * 1024) { ElMessage.error('文件不能超过50MB'); return false }
  return true
}
const handleUpload = async ({ file }) => {
  const res = await uploadImage(file, caseId.value)
  ElMessage.success('上传成功')
  images.value.push(res.data)
  selectedImageId.value = res.data.imageId
}
const deleteImg = async (img) => {
  await ElMessageBox.confirm('确认删除该影像？', '警告', { type: 'warning' })
  await deleteImage(img.imageId)
  images.value = images.value.filter(i => i.imageId !== img.imageId)
  if (selectedImageId.value === img.imageId) selectedImageId.value = images.value[0]?.imageId || null
  ElMessage.success('删除成功')
}

const handleGenerate = async () => {
  if (!selectedImageId.value) return
  generating.value = true
  activeTab.value = 'report'
  try {
    let rptRes
    if (report.value) {
      rptRes = await regenerateReport(report.value.reportId)
    } else {
      const retRes = await searchRetrieval(caseId.value, selectedImageId.value)
      retrieval.value = retRes.data
      rptRes = await generateReport({ caseId: caseId.value, imageId: selectedImageId.value })
    }
    await loadReportDetail(rptRes.data.reportId || rptRes.data)
    ElMessage.success('报告生成成功')
  } finally { generating.value = false }
}

const handleSaveDraft = async () => {
  saving.value = true
  try {
    await saveDraft(report.value.reportId, { finalFindings: editFindings.value, finalImpression: editImpression.value })
    report.value.reportStatus = 'EDITING'
    caseInfo.value.reportStatus = 'EDITING'
    ElMessage.success('草稿已保存')
  } finally { saving.value = false }
}

const handleSign = async () => {
  await ElMessageBox.confirm('签发后报告不可修改，确认签发？', '提示', { type: 'warning' })
  await signReport(report.value.reportId)
  report.value.reportStatus = 'SIGNED'
  caseInfo.value.reportStatus = 'SIGNED'
  ElMessage.success('报告已签发')
}

const handleTriggerEval = async () => {
  if (!report.value) return ElMessage.warning('请先生成报告')
  evaluating.value = true
  try {
    await triggerEval(report.value.reportId)
    const [evalRes, alertRes] = await Promise.all([
      getEvalByReportId(report.value.reportId),
      getAlertsByCaseId(caseId.value)
    ])
    const evalList = Array.isArray(evalRes.data) ? evalRes.data : [evalRes.data]
    evalResult.value = evalList.length ? evalList[evalList.length - 1] : null
    caseAlerts.value = alertRes.data || []
    ElMessage.success('评测完成')
  } finally { evaluating.value = false }
}

const handleAnalyzeTerms = async () => {
  if (!report.value) return ElMessage.warning('请先生成报告')
  analyzingTerms.value = true
  activeTab.value = 'terms'
  try {
    const res = await analyzeTerms(report.value.reportId)
    termCorrections.value = res.data || []
    ElMessage.success(`发现 ${termCorrections.value.length} 条建议`)
  } finally { analyzingTerms.value = false }
}

const handleAcceptTerm = async (row) => {
  await acceptCorrection(row.correctionId); row.isAccepted = 1; ElMessage.success('已采纳')
}
const handleDismissTerm = async (row) => {
  await dismissCorrection(row.correctionId); row.isAccepted = -1
}

const handleMarkTypical = async () => {
  await markTypical(caseId.value, { isTypical: 1, typicalTags: typicalForm.tags, typicalRemark: typicalForm.remark })
  caseInfo.value.isTypical = 1; showTypicalDialog.value = false; ElMessage.success('已标记为典型病例')
}
const handleUnmarkTypical = async () => {
  await markTypical(caseId.value, { isTypical: 0 }); caseInfo.value.isTypical = 0; ElMessage.success('已取消典型标记')
}

const formatDate = (val) => val ? val.replace('T', ' ').substring(0, 16) : '-'
const statusLabel = (s) => ({ NONE: '待生成', AI_DRAFT: 'AI草稿', EDITING: '编辑中', SIGNED: '已签发' }[s] || s || '-')
const statusType = (s) => ({ NONE: 'info', AI_DRAFT: '', EDITING: 'warning', SIGNED: 'success' }[s] || '')
const gradeType = (g) => ({ A: 'success', B: 'success', C: 'warning', D: 'danger', F: 'danger' }[g] || '')
const termStatusLabel = (v) => v === 1 ? '已采纳' : v === -1 ? '已忽略' : '待处理'
const termStatusType = (v) => v === 1 ? 'success' : v === -1 ? 'info' : 'warning'

watch(() => route.params.id, () => loadAll())
onMounted(loadAll)
</script>

<style scoped>
.detail-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; }
.detail-tabs :deep(.el-tabs__content) { padding-top:12px; }
.rag-case { padding:8px 0; border-bottom:1px solid #f0f0f0; }
.rag-case:last-child { border-bottom:none; }
.rag-case-header { display:flex; justify-content:space-between; font-weight:600; margin-bottom:4px; }
.rag-impression { font-size:13px; color:#666; }
.image-gallery { display:grid; grid-template-columns:repeat(3,1fr); gap:8px; margin-top:8px; }
.image-item { position:relative; height:100px; border-radius:4px; overflow:hidden; border:2px solid transparent; cursor:pointer; }
.image-item.selected { border-color:#409eff; }
.image-item:hover .image-overlay { opacity:1; }
.image-overlay { position:absolute; bottom:0; left:0; right:0; background:rgba(0,0,0,0.55); color:white; font-size:12px; padding:3px 6px; display:flex; justify-content:space-between; opacity:0; transition:opacity 0.2s; }
.image-error { display:flex; align-items:center; justify-content:center; height:100%; background:#f5f7fa; color:#bbb; }
.check-icon { position:absolute; top:4px; right:4px; color:#409eff; background:white; border-radius:50%; }
</style>
