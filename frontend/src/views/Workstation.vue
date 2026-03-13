<template>
  <div class="workstation">
    <div v-if="isMobile" class="mobile-tabs">
      <button :class="['mobile-tab', mobileTab === 'cases' && 'active']" @click="mobileTab = 'cases'">病例</button>
      <button :class="['mobile-tab', mobileTab === 'workspace' && 'active']" @click="mobileTab = 'workspace'">工作�?/button>
    </div>
    <CasePanel
      v-show="!isMobile || mobileTab === 'cases'"
      :case-total="caseTotal"
      :is-admin="userStore.isAdmin"
      :search-keyword="searchKeyword"
      :status-filters="STATUS_FILTERS"
      :active-filter="activeFilter"
      :batch-generating="batchGenerating"
      :batch-progress="batchProgress"
      :none-count="noneCount"
      :case-list="caseList"
      :list-loading="listLoading"
      :selected-case-id="selectedCaseId"
      :has-more="hasMore"
      :status-color="statusColor"
      :status-label="statusLabel"
      :gender-label="genderLabel"
      :format-time="formatTime"
      @search-input="handleSearchInput"
      @set-filter="setFilter"
      @select-case="selectCase"
      @batch-generate="handleBatchGenerate"
      @load-more="loadMore"
      @create="openCreateDialog"
      @import="handleImport"
      @cancel-batch="cancelBatchGeneration"
    />

    <!-- ══ 右侧工作�?══ -->
    <div class="workspace" v-if="selectedCaseId" v-show="!isMobile || mobileTab === 'workspace'">
      <!-- 工作区顶部信息栏 -->
      <WorkstationHeader
        :case-info="caseInfo"
        :has-report="!!currentReport"
        :generating="generating"
        :can-delete="userStore.isAdmin"
        :status-color="statusColor"
        :status-label="statusLabel"
        :gender-label="genderLabel"
        :format-date="formatDate"
        @regenerate="handleRegenerate"
        @mark-typical="handleMarkTypical"
        @print="handlePrint"
        @delete-case="handleDeleteCase"
      />

      <!-- 主内容区 -->
      <div class="ws-scroll">
      <div class="ws-body">
        <!-- 左：DICOM 影像查看�?-->
        <div class="viewer-panel">
          <ViewerToolbar
            :viewer-header-text="viewerHeaderText"
            :viewer-shortcut-hint="viewerShortcutHint"
            :can-undo="canUndo"
            :can-redo="canRedo"
            :can-compare="canCompare"
            :compare-mode="compareMode"
            :anno-tool="annoTool"
            :show-ai-annos="showAiAnnos"
            :show-doctor-annos="showDoctorAnnos"
            :ai-annotation-count="aiAnnotationCount"
            :doctor-annotation-count="doctorAnnotationCount"
            :selected-anno-id="selectedAnnoId"
            @zoom="zoom"
            @rotate="rotate"
            @reset="resetViewer"
            @undo="undoAnnoAction"
            @redo="redoAnnoAction"
            @toggle-compare="toggleCompareMode"
            @select-tool="activateSelectTool"
            @rect-tool="activateRectTool"
            @line-tool="activateLineTool"
            @toggle-ai="toggleAiLayer"
            @toggle-doctor="toggleDoctorLayer"
            @delete-selected="deleteSelectedAnno"
          />
          <div class="viewer-canvas" ref="viewerRef" @wheel.prevent="onViewerWheel" @dblclick="resetViewer">
            <div v-if="currentImage" :class="['viewer-stage', compareMode && compareImage && 'viewer-stage-compare']">
              <div class="image-wrapper main-image-wrapper"
                @mouseleave="clearCompareCrosshair"
                :style="{ transform: `scale(${viewerScale}) rotate(${viewerRotate}deg)` }">
                <img :src="currentImage.fullUrl" class="dicom-img" ref="diagImgRef" alt="X光影�?
                  @load="onImgLoad" @error="onImgError" draggable="false" />
                <!-- 标注画布覆盖�?-->
                <canvas ref="annoCanvas" class="anno-overlay"
                  :class="{ 'anno-draw-mode': annoTool === 'rect' || annoTool === 'line' }"
                  @mousedown="onAnnoMouseDown" @mousemove="onAnnoMouseMove"
                  @mouseup="onAnnoMouseUp" @mouseleave="onAnnoMouseLeave"
                  @click="onAnnoClick" />
                <div v-if="currentReport && currentReport.modelConfidence"
                  class="ai-marker">
                  AI · {{ Math.round(currentReport.modelConfidence * 100) }}%
                </div>
                <div v-if="currentImage" class="viewer-meta-overlay">
                  <span class="viewer-meta-chip">{{ currentImage.viewPosition || 'PA' }}</span>
                  <span class="viewer-meta-chip">{{ currentImage.imgWidth || imgNW || '�? }} × {{ currentImage.imgHeight || imgNH || '�? }} px</span>
                  <span :class="['viewer-meta-chip', hasPixelSpacing ? 'chip-ok' : 'chip-warn']">
                    {{ hasPixelSpacing ? `像素间距 ${pixelSpacingText}` : '未读取像素间距，当前仅支�?px 尺寸' }}
                  </span>
                  <span class="viewer-meta-chip chip-info">缩放 {{ viewerScaleText }}</span>
                  <span v-if="mainScaleBarLabel" class="viewer-meta-chip chip-info">比例�?{{ mainScaleBarLabel }}</span>
                  <span v-if="compareMode && compareCrosshair.active && mainCrosshairText" class="viewer-meta-chip chip-info">
                    主片 {{ mainCrosshairText }}
                  </span>
                  <span v-if="selectedAnnotation" class="viewer-meta-chip chip-info">
                    已选标�?{{ formatAnnoMeasurement(selectedAnnotation) }}
                  </span>
                  <span v-if="compareMode && selectedAnnotation && compareSelectedMeasurementText" class="viewer-meta-chip chip-info">
                    对照测量 {{ compareSelectedMeasurementText }}
                  </span>
                </div>
                <div v-if="mainScaleBarWidthStyle" class="scale-bar" :style="mainScaleBarWidthStyle">
                  <span class="scale-bar-tick scale-bar-tick-start"></span>
                  <span class="scale-bar-tick scale-bar-tick-quarter"></span>
                  <span class="scale-bar-tick scale-bar-tick-mid"></span>
                  <span class="scale-bar-tick scale-bar-tick-three-quarter"></span>
                  <span class="scale-bar-tick scale-bar-tick-end"></span>
                  <span class="scale-bar-label">{{ mainScaleBarLabel }}</span>
                </div>
                <template v-if="compareMode && compareCrosshair.active">
                  <div class="crosshair-line crosshair-line-v" :style="crosshairLineStyle('x')"></div>
                  <div class="crosshair-line crosshair-line-h" :style="crosshairLineStyle('y')"></div>
                  <div class="crosshair-readout" :style="crosshairReadoutStyle('main')">{{ mainCrosshairText }}</div>
                </template>
              </div>
              <div v-if="compareMode && compareImage" class="image-wrapper compare-image-wrapper"
                @mousemove="onCompareImageMouseMove"
                @mouseleave="clearCompareCrosshair"
                :style="{ transform: `scale(${viewerScale}) rotate(${viewerRotate}deg)` }">
                <img :src="compareImage.fullUrl" class="dicom-img compare-dicom-img" ref="compareImgRef" alt="对比影像"
                  @load="onCompareImgLoad"
                  @error="onCompareImgError" draggable="false" />
                <div class="compare-image-tag">对比影像 · {{ compareImage.fileName || '历史影像' }}</div>
                <div v-if="compareScaleBadgeText" class="compare-image-badge">{{ compareScaleBadgeText }}</div>
                <div v-if="compareSelectedMeasurementText" class="compare-image-badge compare-image-badge-second">同步测量 · {{ compareSelectedMeasurementText }}</div>
                <div v-if="compareMeasurementDeltaText" class="compare-image-badge compare-image-badge-third">变化�?· {{ compareMeasurementDeltaText }}</div>
                <div v-if="compareScaleBarWidthStyle" class="scale-bar scale-bar-compare" :style="compareScaleBarWidthStyle">
                  <span class="scale-bar-tick scale-bar-tick-start"></span>
                  <span class="scale-bar-tick scale-bar-tick-quarter"></span>
                  <span class="scale-bar-tick scale-bar-tick-mid"></span>
                  <span class="scale-bar-tick scale-bar-tick-three-quarter"></span>
                  <span class="scale-bar-tick scale-bar-tick-end"></span>
                  <span class="scale-bar-label">{{ compareScaleBarLabel }}</span>
                </div>
                <template v-if="compareMode && compareCrosshair.active">
                  <div class="crosshair-line crosshair-line-v" :style="crosshairLineStyle('x')"></div>
                  <div class="crosshair-line crosshair-line-h" :style="crosshairLineStyle('y')"></div>
                  <div class="crosshair-readout crosshair-readout-compare" :style="crosshairReadoutStyle('compare')">{{ compareCrosshairText }}</div>
                </template>
              </div>
            </div>
            <div v-else class="viewer-empty">
              <el-icon :size="48" style="color:rgba(255,255,255,0.2)"><Picture /></el-icon>
              <p>请选择检查图像或上传新影�?/p>
            </div>
          </div>
          <!-- 影像缩略图条 -->
          <div class="thumb-strip" v-if="images.length > 0">
            <div v-for="img in images" :key="img.imageId"
              :class="['thumb-item', currentImage?.imageId === img.imageId && 'thumb-active', compareImage?.imageId === img.imageId && 'thumb-compare']"
              @click="handleThumbSelect(img)">
              <img :src="img.thumbnailUrl || img.fullUrl" :alt="img.viewPosition" />
              <span>{{ img.viewPosition || 'PA' }}</span>
              <div class="thumb-del" @click.stop="handleDeleteImage(img)"><el-icon><Close /></el-icon></div>
            </div>
            <!-- 上传按钮 -->
            <el-upload :show-file-list="false" :before-upload="beforeUpload"
              :http-request="handleUpload" accept=".jpg,.jpeg,.png,.dcm" class="thumb-upload">
              <div class="thumb-add"><el-icon><Plus /></el-icon></div>
            </el-upload>
          </div>
          <!-- 无影像时的上传区 -->
          <div v-else class="upload-zone">
            <el-upload drag :show-file-list="false" :before-upload="beforeUpload"
              :http-request="handleUpload" accept=".jpg,.jpeg,.png,.dcm">
              <el-icon :size="28" style="color:#40a9ff"><UploadFilled /></el-icon>
              <div style="font-size:13px;color:rgba(255,255,255,0.6);margin-top:6px">
                拖拽或点击上传检查图�?
              </div>
              <div style="font-size:11px;color:rgba(255,255,255,0.3);margin-top:4px">
                支持 JPG / PNG / DICOM，≤50MB
              </div>
            </el-upload>
          </div>

          <!-- 病灶标注列表 -->
          <AnnotationList
            :visible-annotations="visibleAnnotations"
            :total-count="annotations.length"
            :selected-anno-id="selectedAnnoId"
            :format-anno-measurement="formatAnnoMeasurement"
            @select="handleAnnoSelect"
            @delete="handleDeleteAnno"
          />
        </div>

        <!-- 全局标注名输入弹框（fixed定位，跟随鼠标） -->
        <teleport to="body">
          <div v-if="showLabelInput" class="anno-label-popup"
            :style="{ left: labelPopupPos.x + 'px', top: labelPopupPos.y + 'px' }">
            <div class="anno-popup-title">输入标注名称</div>
            <input ref="labelInputRef" v-model="pendingAnnoLabel" class="anno-popup-input"
              placeholder="如：右下肺实变、右侧胸腔积�?
              @keyup.enter="confirmAnnoLabel" @keyup.esc="cancelAnnoLabel" />
            <div class="anno-popup-hint">{{ drawMeasurementHint }}</div>
            <div class="anno-popup-subhint">{{ pixelSpacingGuideText }}</div>
            <div class="anno-popup-btns">
              <button class="anno-popup-ok" @click="confirmAnnoLabel">确定</button>
              <button class="anno-popup-cancel" @click="cancelAnnoLabel">取消</button>
            </div>
          </div>
        </teleport>

        <!-- 右：报告面板 -->
        <ReportPanel
          v-model:report-tab="reportTab"
          v-model:show-ai-compare="showAiCompare"
          v-model:findings="draftFindings"
          v-model:impression="draftImpression"
          :current-report="currentReport"
          :generating="generating"
          :has-current-image="!!currentImage"
          :polishing="polishing"
          :term-loading="termLoading"
          :term-last-count="termLastCount"
          :ai-advice-loading="aiAdviceLoading"
          :ai-advice="aiAdvice"
          :history-loading="historyLoading"
          :edit-history="editHistory"
          :doctor-name="userStore.userInfo?.realName || '当前医生'"
          :format-date="formatDate"
          :conf-color="confColor"
          @generate="handleGenerate"
          @polish="handlePolish"
          @term-normalize="handleTermNormalize"
          @get-ai-advice="handleGetAiAdvice"
          @apply-advice-findings="applyAdviceFindings"
          @apply-advice-impression="applyAdviceImpression"
          @restore-history="handleRestoreHistory"
        />
      </div>

      <!-- 相似病例检�?-->
      <SimilaritySection
        :similar-cases="similarCases"
        @select="selectCaseById"
      />

      <!-- 处理进度 -->
      <WorkflowProgress
        :workflow-steps="workflowSteps"
        :followup-summary="followupSummary"
        :has-pixel-spacing="hasPixelSpacing"
        :pixel-spacing-text="pixelSpacingText"
        :selected-doctor-annotation="selectedDoctorAnnotation"
        :format-anno-measurement="formatAnnoMeasurement"
      />

      </div><!-- /ws-scroll -->

      <!-- 底部操作�?-->
      <WorkstationFooter
        :current-report="currentReport"
        :current-image="currentImage"
        :prev-case-id="prevCaseId"
        :next-case-id="nextCaseId"
        :saving="saving"
        :signing="signing"
        :generating="generating"
        @navigate="navigateCase"
        @save-draft="handleSaveDraft"
        @sign="handleSign"
        @generate="handleGenerate"
      />
    </div>

    <!-- 未选择病例时的占位 -->
    <div class="workspace workspace-empty" v-else v-show="!isMobile || mobileTab === 'workspace'">
      <el-icon :size="64" style="color:rgba(255,255,255,0.28)"><Monitor /></el-icon>
      <p style="color:rgba(220,231,247,0.7);font-size:14px;margin:0">请从左侧选择病例开始阅片与报告书写</p>
    </div>

    <!-- 新建病例弹框 -->
    <CreateCaseDialog
      v-model="createDialogVisible"
      :loading="creating"
      @submit="handleCreateCase"
    />

    <!-- 典型病例标记弹框 -->
    <TypicalCaseDialog
      v-model="typicalDialogVisible"
      :loading="typicalLoading"
      @confirm="confirmMarkTypical"
    />

    <!-- 术语标准化弹�?-->
    <TermDialog
      v-model="termDialogVisible"
      :items="termDialogList"
      :auto-select-all="true"
      @confirm="confirmTermReplace"
    />

    <!-- AI 润色弹窗 -->
    <PolishDialog
      v-model="polishDialogVisible"
      :polish-result="polishResult"
      :draft-findings="draftFindings"
      :draft-impression="draftImpression"
      @apply="applyPolish"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listCases, getCaseById, markTypical, createCase, deleteCase, importCases } from '@/api/case'
import { listImages, listPriorImages, uploadImage, deleteImage, fetchImageBlob } from '@/api/image'
import { generateReport, regenerateReport, saveDraft, signReport, listReports, getReport, getEditHistory, polishReport, getAiAdvice } from '@/api/report'
import { searchRetrieval, listRetrievalByCaseId } from '@/api/retrieval'
import { analyzeTerms, acceptCorrection } from '@/api/term'
import { listAnnotations, createAnnotation, updateAnnotation, deleteAnnotation } from '@/api/annotation'
import CasePanel from '@/components/CasePanel.vue'
import ViewerToolbar from '@/components/ViewerToolbar.vue'
import AnnotationList from '@/components/AnnotationList.vue'
import ReportPanel from '@/components/ReportPanel.vue'
import SimilaritySection from '@/components/SimilaritySection.vue'
import WorkflowProgress from '@/components/WorkflowProgress.vue'
import WorkstationFooter from '@/components/WorkstationFooter.vue'
import PolishDialog from '@/components/PolishDialog.vue'
import CreateCaseDialog from '@/components/CreateCaseDialog.vue'
import TypicalCaseDialog from '@/components/TypicalCaseDialog.vue'
import TermDialog from '@/components/TermDialog.vue'
import WorkstationHeader from '@/components/WorkstationHeader.vue'

const route = useRoute()
const userStore = useUserStore()
const isMobile = ref(false)
const mobileTab = ref('cases')

/* ─────────────── 常量 ─────────────── */
const STATUS_FILTERS = [
  { label: '待生�?, value: 'NONE', color: 'orange' },
  { label: 'AI草稿', value: 'AI_DRAFT', color: 'blue' },
  { label: '编辑�?, value: 'EDITING', color: 'blue' },
  { label: '已签�?, value: 'SIGNED', color: 'green' },
]

/* ─────────────── 病例列表 ─────────────── */
const searchKeyword = ref('')
const activeFilter = ref('')
const caseList = ref([])
const caseTotal = ref(0)
const listLoading = ref(false)
const currentPage = ref(1)
const pageSize = 20
const hasMore = ref(false)

let searchTimer = null
const onSearch = () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { currentPage.value = 1; fetchCases() }, 400)
}
const handleSearchInput = (value) => {
  searchKeyword.value = value
  onSearch()
}

const updateIsMobile = () => {
  isMobile.value = window.innerWidth <= 768
  if (!isMobile.value && mobileTab.value !== 'workspace') {
    mobileTab.value = 'cases'
  }
}
const setFilter = (val) => {
  activeFilter.value = activeFilter.value === val ? '' : val
  currentPage.value = 1
  fetchCases()
}

const fetchCases = async (append = false) => {
  listLoading.value = true
  try {
    const params = { page: currentPage.value, pageSize, sortOrder: 'desc' }
    if (searchKeyword.value) params.examNo = searchKeyword.value
    if (activeFilter.value) params.reportStatus = activeFilter.value
    // 已签发只看当前医生自己签发的病例（追责隔离），编辑中为共享工作队�?
    if (activeFilter.value === 'SIGNED') {
      const uid = userStore.userInfo?.userId
      if (uid) params.doctorId = uid
    }
    const res = await listCases(params)
    const records = res.data?.list || res.data?.records || []
    caseTotal.value = res.data?.total || 0
    let sorted = append ? [...caseList.value, ...records] : records
    if (activeFilter.value === 'SIGNED') {
      const gradeOrder = { D: 0, C: 1, B: 2, A: 3 }
      sorted = [...sorted].sort((a, b) => {
        const ga = gradeOrder[a.qualityGrade] ?? 4
        const gb = gradeOrder[b.qualityGrade] ?? 4
        return ga - gb
      })
    }
    caseList.value = sorted
    hasMore.value = caseList.value.length < caseTotal.value
    // 同步当前选中病例的状态到工作区顶�?
    if (selectedCaseId.value) {
      const updated = caseList.value.find(c => c.caseId === selectedCaseId.value)
      if (updated) caseInfo.value = { ...caseInfo.value, ...updated }
    }
  } finally {
    listLoading.value = false
  }
}

const loadMore = () => { currentPage.value++; fetchCases(true) }

const openCreateDialog = () => {
  createDialogVisible.value = true
}

const cancelBatchGeneration = () => {
  batchCancel.value = true
}

/* ─────────────── 选中病例 ─────────────── */
const selectedCaseId = ref(null)
const caseInfo = ref({})
const images = ref([])
const currentImage = ref(null)
const compareMode = ref(false)
const compareImage = ref(null)
const currentReport = ref(null)
const editHistory = ref([])
const historyLoading = ref(false)
const similarCases = ref([])
const priorImages = ref([])
const showAiCompare = ref(false)
const aiAdvice = ref(null)
const aiAdviceLoading = ref(false)
const termLoading = ref(false)

const revokeImageUrls = (list) => {
  if (!list || !Array.isArray(list)) return
  list.forEach(img => {
    if (img?._thumbUrl && img._thumbUrl.startsWith('blob:')) URL.revokeObjectURL(img._thumbUrl)
    if (img?._fullUrl && img._fullUrl.startsWith('blob:')) URL.revokeObjectURL(img._fullUrl)
    if (img) {
      img._thumbUrl = null
      img._fullUrl = null
    }
  })
}

const buildImageUrl = async (imageId, thumbnail = false) => {
  const res = await fetchImageBlob(imageId, thumbnail)
  return URL.createObjectURL(res.data)
}

const ensureThumbnailUrl = async (img) => {
  if (!img || img._thumbUrl) return
  try {
    const url = await buildImageUrl(img.imageId, true)
    img._thumbUrl = url
    img.thumbnailUrl = url
  } catch { /* ignore */ }
}

const ensureFullUrl = async (img) => {
  if (!img || img._fullUrl) return
  try {
    const url = await buildImageUrl(img.imageId, false)
    img._fullUrl = url
    img.fullUrl = url
  } catch { /* ignore */ }
}

const selectCaseById = async (caseId) => {
  const found = caseList.value.find(c => String(c.caseId) === String(caseId))
  if (found) {
    await selectCase(found)
  } else {
    try {
      const res = await getCaseById(caseId)
      if (res.data) await selectCase(res.data)
    } catch { ElMessage.warning('相似病例数据加载失败') }
  }
}

const selectCase = async (c) => {
  if (selectedCaseId.value === c.caseId) return
  currentReport.value = null
  draftFindings.value = ''
  draftImpression.value = ''
  editHistory.value = []
  similarCases.value = []
  aiAdvice.value = null
  annotations.value = []
  selectedAnnoId.value = null
  undoStack.value = []
  redoStack.value = []
  compareMode.value = false
  compareImage.value = null
  selectedCaseId.value = c.caseId
  caseInfo.value = c
  reportTab.value = 'edit'
  if (isMobile.value) mobileTab.value = 'workspace'
  await Promise.all([loadCaseDetail(), loadImages()])
  await loadReport()
}

const loadCaseDetail = async () => {
  try {
    const res = await getCaseById(selectedCaseId.value)
    caseInfo.value = res.data || caseInfo.value
  } catch { /* ignore */ }
}

const loadImages = async () => {
  try {
    const res = await listImages(selectedCaseId.value)
    revokeImageUrls(images.value)
    images.value = res.data || []
    await Promise.all(images.value.map(ensureThumbnailUrl))
    currentImage.value = images.value[0] || null
    if (currentImage.value) await ensureFullUrl(currentImage.value)
  } catch {
    revokeImageUrls(images.value)
    images.value = []
  }
}

const loadPriorImageSummary = async (imageId) => {
  if (!selectedCaseId.value || !imageId) {
    revokeImageUrls(priorImages.value)
    priorImages.value = []
    return
  }
  try {
    const res = await listPriorImages(selectedCaseId.value, imageId)
    revokeImageUrls(priorImages.value)
    priorImages.value = res.data || []
    await Promise.all(priorImages.value.map(ensureThumbnailUrl))
  } catch {
    revokeImageUrls(priorImages.value)
    priorImages.value = []
  }
}

const loadReport = async () => {
  try {
    const res = await listReports({ caseId: selectedCaseId.value, page: 1, pageSize: 1 })
    const records = res.data?.list || res.data?.records || []
    if (records.length > 0) {
      currentReport.value = records[0]
      draftFindings.value = currentReport.value.finalFindings || currentReport.value.aiFindings || ''
      draftImpression.value = currentReport.value.finalImpression || currentReport.value.aiImpression || ''
      await Promise.all([loadHistory(currentReport.value.reportId), loadSimilarCases()])
    } else {
      currentReport.value = null
      draftFindings.value = ''
      draftImpression.value = ''
      similarCases.value = []
      editHistory.value = []
      // 数据自愈：case_info.report_status �?NONE �?report_info 无记录，本地纠正�?NONE
      if (caseInfo.value && caseInfo.value.reportStatus && caseInfo.value.reportStatus !== 'NONE') {
        caseInfo.value = { ...caseInfo.value, reportStatus: 'NONE' }
      }
    }
  } catch { currentReport.value = null }
}

const loadHistory = async (reportId) => {
  historyLoading.value = true
  try {
    const res = await getEditHistory(reportId)
    editHistory.value = res.data || []
  } catch { editHistory.value = [] } finally { historyLoading.value = false }
}

const loadSimilarCases = async () => {
  try {
    const retriRes = await listRetrievalByCaseId(selectedCaseId.value)
    const logs = retriRes.data || []
    const latestLog = logs[logs.length - 1]
    similarCases.value = (latestLog?.similarCases || []).slice(0, 3)
  } catch { similarCases.value = [] }
}

/* ─────────────── 术语标准�?─────────────── */
const termDialogVisible = ref(false)
const termDialogList = ref([])
const termLastCount = ref(0)

const handleTermNormalize = async () => {
  if (!currentReport.value) return
  termLoading.value = true
  try {
    const payload = { findings: draftFindings.value || '', impression: draftImpression.value || '' }
    const res = await analyzeTerms(currentReport.value.reportId, payload)
    const list = res.data || []
    // 筛选出待处理的纠正项（isAccepted === 0 �?null�?
    termDialogList.value = list.filter(t => !t.isAccepted || t.isAccepted === 0)
    if (termDialogList.value.length === 0) {
      ElMessage.success('未发现需要纠正的术语')
    } else {
      termDialogVisible.value = true
    }
  } catch {
    // 请求拦截器已展示错误
  } finally { termLoading.value = false }
}

const confirmTermReplace = async (items) => {
  let count = 0
  for (const item of items || []) {
    const orig = item.originalTerm
    const corr = item.suggestedTerm
    if (orig && corr) {
      const beforeF = draftFindings.value
      const beforeI = draftImpression.value
      draftFindings.value = (draftFindings.value || '').split(orig).join(corr)
      draftImpression.value = (draftImpression.value || '').split(orig).join(corr)
      if (draftFindings.value !== beforeF || draftImpression.value !== beforeI) count++
    }
    try { await acceptCorrection(item.correctionId) } catch { /* ignore */ }
  }
  termLastCount.value = count
  termDialogVisible.value = false
  ElMessage.success(`已替�?${count} 处术语`)
}

const handleRestoreHistory = async (h) => {
  try {
    await ElMessageBox.confirm(
      `将报告内容恢复到�?{formatDate(h.editTime)}」的版本？当前未保存的修改将丢失。`,
      '恢复历史版本', { confirmButtonText: '确认恢复', cancelButtonText: '取消', type: 'warning' }
    )
    if (h.findingsAfter != null) draftFindings.value = h.findingsAfter
    if (h.impressionAfter != null) draftImpression.value = h.impressionAfter
    ElMessage.success('已恢复到所选版本，记得保存草稿')
    reportTab.value = 'edit'
  } catch { /* ignore */ }
}

const handleDeleteCase = async () => {
  try {
    await ElMessageBox.confirm(`确认删除病例�?{caseInfo.value.examNo}」？删除后不可恢复`, '删除病例', { type: 'warning' })
    await deleteCase(selectedCaseId.value)
    ElMessage.success('病例已删�?)
    selectedCaseId.value = null
    caseInfo.value = {}
    images.value = []
    currentImage.value = null
    currentReport.value = null
    await fetchCases()
  } catch { /* ignore */ }
}

const handleImport = async (file) => {
  try {
    const res = await importCases(file)
    ElMessage.success(`导入完成：成�?${res.data?.successCount ?? 0} 条`)
    await fetchCases()
  } catch (e) {
    ElMessage.error('导入失败�? + (e?.message || '请检查文件格�?))
  }
  return false
}

const handleDeleteImage = async (img) => {
  try {
    await ElMessageBox.confirm('确认删除该影像？', '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteImage(img.imageId)
    revokeImageUrls([img])
    images.value = images.value.filter(i => i.imageId !== img.imageId)
    if (currentImage.value?.imageId === img.imageId) {
      currentImage.value = images.value[0] || null
    }
    ElMessage.success('影像已删�?)
  } catch { /* ignore */ }
}

/* ─────────────── 报告编辑 ─────────────── */
const reportTab = ref('edit')
const draftFindings = ref('')
const draftImpression = ref('')
const generating = ref(false)
const saving = ref(false)
const signing = ref(false)

/* ─────────────── 批量生成 ─────────────── */
const batchGenerating = ref(false)
const batchProgress = ref({ current: 0, total: 0 })
let batchCancel = false

const noneCount = computed(() => {
  if (activeFilter.value === 'NONE') return caseTotal.value
  return caseList.value.filter(c => !c.reportStatus || c.reportStatus === 'NONE').length
})

const handleBatchGenerate = async () => {
  try {
    await ElMessageBox.confirm(
      `将为 ${noneCount.value} 个待生成病例按时间顺序逐个生成AI报告，过程可能较长，是否继续？`,
      '批量生成AI报告', { confirmButtonText: '开始生�?, cancelButtonText: '取消', type: 'info' }
    )
  } catch { return }

  batchGenerating.value = true
  batchCancel = false

  try {
    const res = await listCases({ reportStatus: 'NONE', page: 1, pageSize: 200, sortOrder: 'asc' })
    const cases = res.data?.list || res.data?.records || []
    batchProgress.value = { current: 0, total: cases.length }

    for (let i = 0; i < cases.length; i++) {
      if (batchCancel) { ElMessage.info('已取消批量生�?); break }

      const c = cases[i]
      try {
        const imgRes = await listImages(c.caseId)
        const imgs = imgRes.data || []
        if (imgs.length === 0) {
          batchProgress.value = { current: i + 1, total: cases.length }
          continue
        }
        await generateReport({ caseId: c.caseId, imageId: imgs[0].imageId })
      } catch (err) {
        console.warn(`[batch] case ${c.examNo} failed:`, err)
      }
      batchProgress.value = { current: i + 1, total: cases.length }
    }

    if (!batchCancel) ElMessage.success(`批量生成完成，共处理 ${batchProgress.value.current} 例`)
    await fetchCases()
    if (selectedCaseId.value) await loadReport()
  } finally {
    batchGenerating.value = false
  }
}

const handleGenerate = async () => {
  if (!currentImage.value) { ElMessage.warning('请先选择影像'); return }
  generating.value = true
  try {
    const res = await generateReport({ caseId: selectedCaseId.value, imageId: currentImage.value.imageId })
    const reportId = res?.data?.reportId || res?.data
    if (reportId) {
      try {
        const detailRes = await getReport(reportId)
        currentReport.value = detailRes.data
        draftFindings.value = detailRes.data?.finalFindings || detailRes.data?.aiFindings || ''
        draftImpression.value = detailRes.data?.finalImpression || detailRes.data?.aiImpression || ''
      } catch (_) {
        // fallback to response data if detail fetch fails
        if (res?.data && typeof res.data === 'object') {
          currentReport.value = res.data
          draftFindings.value = res.data.finalFindings || res.data.aiFindings || ''
          draftImpression.value = res.data.finalImpression || res.data.aiImpression || ''
        }
      }
    }
    ElMessage.success('AI报告生成完成')
    fetchCases()
  } finally { generating.value = false }
}
const handleRegenerate = async () => {
  if (!currentReport.value?.reportId) {
    ElMessage.warning('当前无报告，请先生成')
    return
  }
  generating.value = true
  try {
    const res = await regenerateReport(currentReport.value.reportId)
    const reportId = res?.data?.reportId || res?.data || currentReport.value.reportId
    if (reportId) {
      try {
        const detailRes = await getReport(reportId)
        currentReport.value = detailRes.data
        draftFindings.value = detailRes.data?.finalFindings || detailRes.data?.aiFindings || ''
        draftImpression.value = detailRes.data?.finalImpression || detailRes.data?.aiImpression || ''
      } catch (_) {
        if (res?.data && typeof res.data === 'object') {
          currentReport.value = res.data
          draftFindings.value = res.data.finalFindings || res.data.aiFindings || ''
          draftImpression.value = res.data.finalImpression || res.data.aiImpression || ''
        }
      }
    }
    ElMessage.success('报告已重新生�?')
    fetchCases()
  } catch {
    // 请求拦截器已展示错误
  } finally { generating.value = false }
}
const ensureCurrentReportId = async () => {
  const reportId = currentReport.value?.reportId
  if (reportId) return reportId
  await loadReport()
  if (!currentReport.value?.reportId) {
    throw new Error('当前病例尚未生成有效报告')
  }
  return currentReport.value.reportId
}

const doSave = async () => {
  const reportId = await ensureCurrentReportId()
  await saveDraft(reportId, {
    finalFindings: draftFindings.value,
    finalImpression: draftImpression.value
  })
  if (!currentReport.value) currentReport.value = { reportId }
  currentReport.value.reportId = reportId
  currentReport.value.finalFindings = draftFindings.value
  currentReport.value.finalImpression = draftImpression.value
  currentReport.value.reportStatus = 'EDITING'
}

const handleSaveDraft = async () => {
  saving.value = true
  try {
    await doSave()
    ElMessage.success('草稿已保�?)
    fetchCases()
  } catch {
    // 请求拦截器已展示错误
  } finally { saving.value = false }
}

const handleSign = async () => {
  if (!currentReport.value?.reportId) {
    ElMessage.error('当前病例尚未生成有效报告，无法签�?)
    return
  }
  try {
    await ElMessageBox.confirm('确认签发该报告？签发后不可撤销', '签发报告', {
      confirmButtonText: '确认签发', cancelButtonText: '取消',
      confirmButtonClass: 'el-button--success', type: 'warning'
    })
  } catch { return }

  signing.value = true
  try {
    const reportId = currentReport.value.reportId

    if (currentReport.value.reportStatus === 'EDITING' || currentReport.value.reportStatus === 'AI_DRAFT') {
      // 签发前自动保存最新编辑内�?
      await saveDraft(reportId, {
        finalFindings: draftFindings.value,
        finalImpression: draftImpression.value
      })
    }

    await signReport(reportId)
    ElMessage.success('报告已成功签�?)
    await loadReport()
    fetchCases()

  } catch (err) {
    ElMessage.error('签发失败�? + (err?.message || '请检查网络或重新登录'))
    await loadReport()
  } finally { signing.value = false }
}


const handleGetAiAdvice = async () => {
  if (!currentReport.value?.reportId) return
  aiAdviceLoading.value = true
  try {
    const res = await getAiAdvice(currentReport.value.reportId)
    const data = res.data
    // 若返回空对象（AI服务未配置或解析失败），视为错误
    if (!data || !data.overall_assessment) {
      ElMessage.warning('AI建议服务暂不可用，请确认DeepSeek API已配�?)
      return
    }
    aiAdvice.value = data
  } catch {
    ElMessage.error('获取AI建议失败，请确认DeepSeek API密钥已配置并网络可达')
  } finally { aiAdviceLoading.value = false }
}

const applyAdviceFindings = () => {
  if (!aiAdvice.value?.suggested_findings) return
  draftFindings.value = aiAdvice.value.suggested_findings
  ElMessage.success('参考内容已应用，请返回修改后签�?)
}

const applyAdviceImpression = () => {
  if (!aiAdvice.value?.suggested_impression) return
  draftImpression.value = aiAdvice.value.suggested_impression
  ElMessage.success('参考内容已应用，请返回修改后签�?)
}

/* ─────────────── 影像操作 ─────────────── */
const viewerRef = ref(null)
const viewerScale = ref(1)
const viewerRotate = ref(0)
const clampViewerScale = (value) => Math.max(0.3, Math.min(4, value))
const normalizeViewerRotate = (value) => ((value % 360) + 360) % 360
const currentPixelSpacingX = computed(() => Number(currentImage.value?.pixelSpacingXmm) || null)
const currentPixelSpacingY = computed(() => Number(currentImage.value?.pixelSpacingYmm) || null)
const hasPixelSpacing = computed(() => !!(currentPixelSpacingX.value && currentPixelSpacingY.value))
const pixelSpacingText = computed(() => hasPixelSpacing.value
  ? `${currentPixelSpacingX.value.toFixed(3)} × ${currentPixelSpacingY.value.toFixed(3)} mm/px`
  : '未读取到')
const pixelSpacingGuideText = computed(() => hasPixelSpacing.value
  ? `已启用毫米实测：${pixelSpacingText.value}`
  : '未读取到像素间距，本次将仅保存像素尺寸；如为 DICOM，请优先使用原始文件�?)
const viewerScaleText = computed(() => `${Math.round(viewerScale.value * 100)}%`)
const viewerRotationText = computed(() => `${normalizeViewerRotate(viewerRotate.value)}°`)
const compareCrosshair = ref({ active: false, xRatio: 0.5, yRatio: 0.5, source: 'main' })
const compareImgRef = ref(null)
const mainRenderSize = ref({ width: 0, height: 0 })
const compareRenderSize = ref({ width: 0, height: 0 })

const getImagePixelSpacing = (image) => ({
  x: Number(image?.pixelSpacingXmm) || null,
  y: Number(image?.pixelSpacingYmm) || null,
})

const buildCrosshairMetric = (image) => {
  if (!compareCrosshair.value.active || !image) return null
  const widthPx = Number(image?.imgWidth) || imgNW || 0
  const heightPx = Number(image?.imgHeight) || imgNH || 0
  if (!widthPx || !heightPx) return null
  const xPx = Math.round(compareCrosshair.value.xRatio * widthPx)
  const yPx = Math.round(compareCrosshair.value.yRatio * heightPx)
  const spacing = getImagePixelSpacing(image)
  const xMm = spacing.x ? xPx * spacing.x : null
  const yMm = spacing.y ? yPx * spacing.y : null
  return { xPx, yPx, xMm, yMm }
}

const formatCrosshairMetric = (image, prefix) => {
  const metric = buildCrosshairMetric(image)
  if (!metric) return ''
  const xText = metric.xMm != null ? `${metric.xPx} px / ${metric.xMm.toFixed(1)} mm` : `${metric.xPx} px`
  const yText = metric.yMm != null ? `${metric.yPx} px / ${metric.yMm.toFixed(1)} mm` : `${metric.yPx} px`
  return `${prefix} X ${xText} · Y ${yText}`
}

const mainCrosshairText = computed(() => formatCrosshairMetric(currentImage.value, '坐标'))
const compareCrosshairText = computed(() => formatCrosshairMetric(compareImage.value, '对照'))

const calcAnnoMeasurementByImage = (anno, image) => {
  const widthPx = Math.max(0, (anno?.width || 0) * (Number(image?.imgWidth) || 0))
  const heightPx = Math.max(0, (anno?.height || 0) * (Number(image?.imgHeight) || 0))
  const spacing = getImagePixelSpacing(image)
  const widthMm = spacing.x ? widthPx * spacing.x : null
  const heightMm = spacing.y ? heightPx * spacing.y : null
  const lengthPx = Math.sqrt(widthPx * widthPx + heightPx * heightPx)
  const lengthMm = spacing.x && spacing.y
    ? Math.sqrt((widthPx * spacing.x) ** 2 + (heightPx * spacing.y) ** 2)
    : null
  return { widthPx, heightPx, widthMm, heightMm, lengthPx, lengthMm }
}

const formatAnnoMeasurementByImage = (anno, image) => {
  if (!anno || !image) return ''
  const metric = calcAnnoMeasurementByImage(anno, image)
  if (anno.annoType === 'LINE') {
    return metric.lengthMm != null ? `${metric.lengthMm.toFixed(1)} mm` : `${Math.round(metric.lengthPx)} px`
  }
  if (metric.widthMm != null && metric.heightMm != null) {
    return `${metric.widthMm.toFixed(1)}×${metric.heightMm.toFixed(1)} mm`
  }
  return `${Math.round(metric.widthPx)}×${Math.round(metric.heightPx)} px`
}

const compareSelectedMeasurementText = computed(() => {
  if (!compareMode.value || !selectedAnnotation.value || !compareImage.value) return ''
  return formatAnnoMeasurementByImage(selectedAnnotation.value, compareImage.value)
})

const calcComparableSizeMetric = (anno, image) => {
  if (!anno || !image) return null
  const metric = calcAnnoMeasurementByImage(anno, image)
  if (anno.annoType === 'LINE') {
    return {
      valuePx: metric.lengthPx,
      valueMm: metric.lengthMm,
      label: '径线'
    }
  }
  const maxPx = Math.max(metric.widthPx, metric.heightPx)
  const maxMm = metric.widthMm != null && metric.heightMm != null
    ? Math.max(metric.widthMm, metric.heightMm)
    : null
  return {
    valuePx: maxPx,
    valueMm: maxMm,
    label: '长径'
  }
}

const classifyComparableDelta = (delta, ratio) => {
  const absDelta = Math.abs(delta || 0)
  const absRatio = Math.abs(ratio || 0)
  if (absRatio < 5 && absDelta < 2) return '稳定'
  return delta > 0 ? '增大' : '缩小'
}

const compareMeasurementDeltaText = computed(() => {
  if (!compareMode.value || !selectedAnnotation.value || !currentImage.value || !compareImage.value) return ''
  const currentMetric = calcComparableSizeMetric(selectedAnnotation.value, currentImage.value)
  const compareMetric = calcComparableSizeMetric(selectedAnnotation.value, compareImage.value)
  if (!currentMetric || !compareMetric) return ''
  const useMm = currentMetric.valueMm != null && compareMetric.valueMm != null
  const currentValue = useMm ? currentMetric.valueMm : currentMetric.valuePx
  const compareValue = useMm ? compareMetric.valueMm : compareMetric.valuePx
  if (currentValue == null || compareValue == null) return ''
  const delta = currentValue - compareValue
  const ratio = compareValue ? (delta / compareValue) * 100 : null
  const sign = delta > 0 ? '+' : ''
  const label = classifyComparableDelta(delta, ratio)
  const valueText = useMm ? `${sign}${delta.toFixed(1)} mm` : `${sign}${Math.round(delta)} px`
  const ratioText = ratio != null ? ` / ${sign}${ratio.toFixed(1)}%` : ''
  return `${label} ? ${currentMetric.label}${valueText}${ratioText}`
})

const buildScaleBar = (image, renderSize) => {
  const spacingX = Number(image?.pixelSpacingXmm) || null
  const naturalWidth = Number(image?.imgWidth) || 0
  const renderWidth = Number(renderSize?.width) || 0
  if (!spacingX || !naturalWidth || !renderWidth) return { label: '', style: null }
  const mmOptions = [5, 10, 20, 50, 100]
  let mm = 20
  for (const candidate of mmOptions) {
    const pxWidth = (candidate / spacingX) * (renderWidth / naturalWidth)
    if (pxWidth >= 48 && pxWidth <= 120) { mm = candidate; break }
  }
  const widthPx = (mm / spacingX) * (renderWidth / naturalWidth)
  return {
    label: `${mm} mm`,
    style: { width: `${Math.max(32, Math.min(140, widthPx)).toFixed(1)}px` }
  }
}

const mainScaleBar = computed(() => buildScaleBar(currentImage.value, mainRenderSize.value))
const compareScaleBar = computed(() => buildScaleBar(compareImage.value, compareRenderSize.value))
const mainScaleBarLabel = computed(() => mainScaleBar.value.label)
const compareScaleBarLabel = computed(() => compareScaleBar.value.label)
const mainScaleBarWidthStyle = computed(() => mainScaleBar.value.style)
const compareScaleBarWidthStyle = computed(() => compareScaleBar.value.style)
const compareScaleBadgeText = computed(() => {
  if (!compareMode.value || !compareImage.value) return ''
  const scaleText = `缩放 ${viewerScaleText.value}`
  return compareScaleBarLabel.value ? `${scaleText} · 比例�?${compareScaleBarLabel.value}` : scaleText
})

const crosshairLineStyle = (axis) => {
  if (!compareCrosshair.value.active) return {}
  return axis === 'x'
    ? { left: `${(compareCrosshair.value.xRatio * 100).toFixed(2)}%` }
    : { top: `${(compareCrosshair.value.yRatio * 100).toFixed(2)}%` }
}

const crosshairReadoutStyle = (side) => {
  if (!compareCrosshair.value.active) return {}
  const xPercent = compareCrosshair.value.xRatio * 100
  const yPercent = compareCrosshair.value.yRatio * 100
  return {
    left: `clamp(8px, ${xPercent.toFixed(2)}%, calc(100% - 220px))`,
    top: `clamp(8px, calc(${yPercent.toFixed(2)}% + 10px), calc(100% - 30px))`,
    transform: side === 'compare' ? 'translateX(-100%)' : 'none'
  }
}

const syncCompareCrosshair = (event, source = 'main') => {
  if (!compareMode.value) return
  const rect = event?.currentTarget?.getBoundingClientRect?.()
  if (!rect?.width || !rect?.height) return
  const xRatio = Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width))
  const yRatio = Math.max(0, Math.min(1, (event.clientY - rect.top) / rect.height))
  compareCrosshair.value = { active: true, xRatio, yRatio, source }
}

const clearCompareCrosshair = () => {
  compareCrosshair.value = { ...compareCrosshair.value, active: false }
}

const onCompareImageMouseMove = (event) => {
  syncCompareCrosshair(event, 'compare')
}
const activeAnnoToolLabel = computed(() => ({ select: '选择标注', rect: '矩形标注', line: '双点测距' }[annoTool.value] || '选择标注'))
const viewerShortcutHint = computed(() => '快捷键：+ 放大�? 缩小�? 重置，R 顺时针旋转，Shift+R 逆时针旋转，V 选择标注，M 矩形标注；支持滚轮缩放、双击重�?)
const viewerHeaderText = computed(() => {
  if (!currentImage.value) return '�?
  return `${currentImage.value.fileName} · 缩放 ${viewerScaleText.value} · 旋转 ${viewerRotationText.value} · ${activeAnnoToolLabel.value}`
})
const zoom = (d) => { viewerScale.value = clampViewerScale(viewerScale.value + d) }
const rotate = (d) => { viewerRotate.value = normalizeViewerRotate(viewerRotate.value + d) }
const resetViewer = () => { viewerScale.value = 1; viewerRotate.value = 0 }
const onViewerWheel = (e) => {
  if (!currentImage.value) return
  const delta = e.deltaY < 0 ? 0.1 : -0.1
  zoom(delta)
}
const onImgError = (e) => {
  const fallback = currentImage.value?.thumbnailUrl
  if (fallback && e?.target && e.target.dataset?.fallbackApplied !== '1' && e.target.src !== fallback) {
    e.target.dataset.fallbackApplied = '1'
    e.target.src = fallback
    return
  }
  if (e?.target) e.target.src = ''
}
const syncRenderedImageSize = () => {
  mainRenderSize.value = {
    width: diagImgRef.value?.clientWidth || 0,
    height: diagImgRef.value?.clientHeight || 0,
  }
  compareRenderSize.value = {
    width: compareImgRef.value?.clientWidth || 0,
    height: compareImgRef.value?.clientHeight || 0,
  }
}
const onCompareImgLoad = () => {
  syncRenderedImageSize()
}

/* ─────────────── 病灶标注 ─────────────── */
const diagImgRef = ref(null)      // 影像 <img> 元素
const annoCanvas = ref(null)      // 画布覆盖�?
const labelInputRef = ref(null)   // 标注名输入框
const annotations = ref([])       // 当前影像的所有标�?
const annoTool = ref('select')    // 'select' | 'rect' | 'line'
const showAiAnnos = ref(true)
const showDoctorAnnos = ref(true)
const selectedAnnoId = ref(null)
const undoStack = ref([])
const redoStack = ref([])
let annoHistoryPendingBefore = null
const showLabelInput = ref(false)
const pendingAnnoLabel = ref('')
const labelPopupPos = ref({ x: 0, y: 0 })
const hoveredHandle = ref(null)
let drawState = null  // { x, y, w, h } 正在绘制的临时矩�?
let imgNW = 0         // 影像自然像素�?
let imgNH = 0         // 影像自然像素�?
let lineDragState = null
let suppressAnnoClick = false

const visibleAnnotations = computed(() =>
  annotations.value.filter(a =>
    (a.source === 'AI' && showAiAnnos.value) ||
    (a.source === 'DOCTOR' && showDoctorAnnos.value)
  )
)

const aiAnnotationCount = computed(() => annotations.value.filter(a => a.source === 'AI').length)
const doctorAnnotationCount = computed(() => annotations.value.filter(a => a.source === 'DOCTOR').length)
const selectedAnnotation = computed(() => annotations.value.find(a => a.annotationId === selectedAnnoId.value) || null)
const selectedDoctorAnnotation = computed(() => selectedAnnotation.value?.source === 'DOCTOR' ? selectedAnnotation.value : null)

const calcAnnoMeasurement = (anno) => {
  const widthPx = Math.max(0, (anno?.width || 0) * imgNW)
  const heightPx = Math.max(0, (anno?.height || 0) * imgNH)
  const widthMm = anno?.measuredWidthMm ?? (currentPixelSpacingX.value ? widthPx * currentPixelSpacingX.value : null)
  const heightMm = anno?.measuredHeightMm ?? (currentPixelSpacingY.value ? heightPx * currentPixelSpacingY.value : null)
  const lengthPx = Math.sqrt(widthPx * widthPx + heightPx * heightPx)
  const lengthMm = currentPixelSpacingX.value && currentPixelSpacingY.value
    ? Math.sqrt((widthPx * currentPixelSpacingX.value) ** 2 + (heightPx * currentPixelSpacingY.value) ** 2)
    : null
  return { widthPx, heightPx, widthMm, heightMm, lengthPx, lengthMm }
}

const formatAnnoMeasurement = (anno) => {
  if (!anno) return '�?
  const metric = calcAnnoMeasurement(anno)
  if (anno.annoType === 'LINE') {
    return metric.lengthMm != null ? `${metric.lengthMm.toFixed(1)} mm` : `${Math.round(metric.lengthPx)} px`
  }
  if (metric.widthMm != null && metric.heightMm != null) {
    return `${metric.widthMm.toFixed(1)}×${metric.heightMm.toFixed(1)} mm`
  }
  return `${Math.round(metric.widthPx)}×${Math.round(metric.heightPx)} px`
}

const drawMeasurementHint = computed(() => {
  if (!drawState || !imgNW || !imgNH) return '请为当前标注输入名称'
  const draftAnno = {
    annoType: annoTool.value === 'line' ? 'LINE' : 'RECTANGLE',
    width: drawState.w / imgNW,
    height: drawState.h / imgNH,
    measuredWidthMm: null,
    measuredHeightMm: null,
  }
  return `当前框选范围：${formatAnnoMeasurement(draftAnno)}`
})

const applyMeasuredSize = (target) => {
  if (!target) return target
  const metric = calcAnnoMeasurement(target)
  if (target.annoType === 'LINE') {
    target.measuredWidthMm = metric.lengthMm != null ? Number(metric.lengthMm.toFixed(3)) : null
    target.measuredHeightMm = null
  } else {
    target.measuredWidthMm = metric.widthMm != null ? Number(metric.widthMm.toFixed(3)) : null
    target.measuredHeightMm = metric.heightMm != null ? Number(metric.heightMm.toFixed(3)) : null
  }
  return target
}

let annoPersistTimer = null

const getLineEndpointsPx = (anno) => {
  const startX = anno.x * imgNW
  const startY = anno.y * imgNH
  return {
    startX,
    startY,
    endX: startX + anno.width * imgNW,
    endY: startY + anno.height * imgNH,
  }
}

const setLineEndpointsPx = (anno, startX, startY, endX, endY) => {
  anno.x = Math.max(0, Math.min(1, startX / imgNW))
  anno.y = Math.max(0, Math.min(1, startY / imgNH))
  anno.width = (endX - startX) / imgNW
  anno.height = (endY - startY) / imgNH
  applyMeasuredSize(anno)
}

const hitLineHandle = (pt, anno) => {
  if (!anno || anno.annoType !== 'LINE') return null
  const { startX, startY, endX, endY } = getLineEndpointsPx(anno)
  const threshold = Math.max(8, Math.round(imgNW * 0.012))
  const distStart = Math.hypot(pt.x - startX, pt.y - startY)
  if (distStart <= threshold) return 0
  const distEnd = Math.hypot(pt.x - endX, pt.y - endY)
  if (distEnd <= threshold) return 1
  return null
}

const updateCanvasCursor = () => {
  const canvas = annoCanvas.value
  if (!canvas) return
  if (lineDragState) {
    canvas.style.cursor = 'grabbing'
    return
  }
  if (hoveredHandle.value) {
    canvas.style.cursor = 'grab'
    return
  }
  if (annoTool.value === 'rect' || annoTool.value === 'line') {
    canvas.style.cursor = 'crosshair'
    return
  }
  canvas.style.cursor = 'default'
}

const activateSelectTool = () => {
  annoTool.value = 'select'
  ElMessage.info('已切换到选择模式：点击标注可选中，再点垃圾桶可删除医生标�?)
}

const activateRectTool = () => {
  annoTool.value = 'rect'
  ElMessage.info('已进入矩形标注模式：请在影像上按住左键拖拽框选病灶区�?)
}

const activateLineTool = () => {
  annoTool.value = 'line'
  ElMessage.info('已进入双点测距模式：请在影像上按住左键拖拽，记录两点间距�?)
}

const toggleAiLayer = () => {
  showAiAnnos.value = !showAiAnnos.value
  ElMessage.info(showAiAnnos.value
    ? `已显示AI标注层（${aiAnnotationCount.value}处）`
    : '已隐藏AI标注�?)
}

const toggleDoctorLayer = () => {
  showDoctorAnnos.value = !showDoctorAnnos.value
  ElMessage.info(showDoctorAnnos.value
    ? `已显示医生标注层�?{doctorAnnotationCount.value}处）`
    : '已隐藏医生标注层')
}

const cloneAnno = (anno) => anno ? JSON.parse(JSON.stringify(anno)) : null
const annoCreateDto = (anno) => applyMeasuredSize({
  imageId: currentImage.value?.imageId || anno.imageId,
  reportId: currentReport.value?.reportId || anno.reportId || null,
  annoType: anno.annoType,
  label: anno.label,
  remark: anno.remark,
  x: anno.x, y: anno.y, width: anno.width, height: anno.height,
  measuredWidthMm: anno.measuredWidthMm ?? null,
  measuredHeightMm: anno.measuredHeightMm ?? null,
  compareStatus: anno.compareStatus || null,
  compareNote: anno.compareNote || null,
  color: anno.color || '#52c41a'
})
const annoUpdateDto = (anno) => applyMeasuredSize({
  annoType: anno.annoType,
  label: anno.label,
  remark: anno.remark,
  x: anno.x, y: anno.y, width: anno.width, height: anno.height,
  measuredWidthMm: anno.measuredWidthMm ?? null,
  measuredHeightMm: anno.measuredHeightMm ?? null,
  compareStatus: anno.compareStatus || null,
  compareNote: anno.compareNote || null,
  color: anno.color || '#52c41a'
})
const sameAnnoGeometry = (left, right) => !!left && !!right
  && Number(left.x) === Number(right.x)
  && Number(left.y) === Number(right.y)
  && Number(left.width) === Number(right.width)
  && Number(left.height) === Number(right.height)
const pushAnnoHistory = (entry) => { undoStack.value.push(entry); redoStack.value = [] }
const canUndo = computed(() => undoStack.value.length > 0)
const canRedo = computed(() => redoStack.value.length > 0)
const compareCandidates = computed(() => {
  const merged = [...images.value, ...priorImages.value]
  const currentId = currentImage.value?.imageId
  const map = new Map()
  merged.forEach(img => {
    if (!img?.imageId || img.imageId === currentId || map.has(img.imageId)) return
    map.set(img.imageId, img)
  })
  return [...map.values()]
})
const canCompare = computed(() => compareCandidates.value.length > 0)
const syncCompareImage = () => {
  if (compareImage.value && compareCandidates.value.some(img => img.imageId === compareImage.value.imageId)) {
    return true
  }
  compareImage.value = compareCandidates.value[0] || null
  if (!compareImage.value) compareMode.value = false
  return !!compareImage.value
}
const toggleCompareMode = () => {
  if (compareMode.value) {
    compareMode.value = false
    compareImage.value = null
    return
  }
  if (!syncCompareImage()) { ElMessage.info('当前暂无可用于对比的其他影像'); return }
  compareMode.value = true
  ElMessage.success('已开启双屏对比：缩放与旋转保持同�?)
}
const handleThumbSelect = (img) => {
  if (compareMode.value && currentImage.value && img.imageId !== currentImage.value.imageId) {
    compareImage.value = img
    return
  }
  currentImage.value = img
}
const onCompareImgError = (e) => {
  const fallback = compareImage.value?.thumbnailUrl
  if (fallback && e?.target && e.target.dataset?.fallbackApplied !== '1' && e.target.src !== fallback) {
    e.target.dataset.fallbackApplied = '1'
    e.target.src = fallback
    return
  }
  if (e?.target) e.target.src = ''
}
const undoAnnoAction = async () => {
  const entry = undoStack.value.pop()
  if (!entry) return
  try {
    if (entry.type === 'create') {
      await deleteAnnotation(entry.currentId || entry.after.annotationId)
      annotations.value = annotations.value.filter(a => a.annotationId !== (entry.currentId || entry.after.annotationId))
      if (selectedAnnoId.value === (entry.currentId || entry.after.annotationId)) selectedAnnoId.value = null
    } else if (entry.type === 'delete') {
      const res = await createAnnotation(annoCreateDto(entry.before))
      annotations.value.push(res.data)
      selectedAnnoId.value = res.data.annotationId
      entry.currentId = res.data.annotationId
    } else if (entry.type === 'update') {
      const res = await updateAnnotation(entry.currentId || entry.annotationId, annoUpdateDto(entry.before))
      const idx = annotations.value.findIndex(a => a.annotationId === (entry.currentId || entry.annotationId))
      if (idx >= 0) annotations.value[idx] = res.data
      selectedAnnoId.value = res.data.annotationId
      entry.currentId = res.data.annotationId
    }
    redoStack.value.push(entry)
    redrawAnnotations()
  } catch {
    ElMessage.error('撤销失败')
    undoStack.value.push(entry)
  }
}
const redoAnnoAction = async () => {
  const entry = redoStack.value.pop()
  if (!entry) return
  try {
    if (entry.type === 'create') {
      const res = await createAnnotation(annoCreateDto(entry.after))
      annotations.value.push(res.data)
      selectedAnnoId.value = res.data.annotationId
      entry.currentId = res.data.annotationId
    } else if (entry.type === 'delete') {
      await deleteAnnotation(entry.currentId || entry.before.annotationId)
      annotations.value = annotations.value.filter(a => a.annotationId !== (entry.currentId || entry.before.annotationId))
      if (selectedAnnoId.value === (entry.currentId || entry.before.annotationId)) selectedAnnoId.value = null
    } else if (entry.type === 'update') {
      const res = await updateAnnotation(entry.currentId || entry.annotationId, annoUpdateDto(entry.after))
      const idx = annotations.value.findIndex(a => a.annotationId === (entry.currentId || entry.annotationId))
      if (idx >= 0) annotations.value[idx] = res.data
      selectedAnnoId.value = res.data.annotationId
      entry.currentId = res.data.annotationId
    }
    undoStack.value.push(entry)
    redrawAnnotations()
  } catch {
    ElMessage.error('重做失败')
    redoStack.value.push(entry)
  }
}

const schedulePersistSelectedAnno = () => {
  const anno = selectedDoctorAnnotation.value
  if (!anno) return
  clearTimeout(annoPersistTimer)
  annoPersistTimer = setTimeout(async () => {
    try {
      const beforeSnapshot = cloneAnno(annoHistoryPendingBefore)
      const res = await updateAnnotation(anno.annotationId, {
        x: anno.x,
        y: anno.y,
        width: anno.width,
        height: anno.height,
        measuredWidthMm: anno.measuredWidthMm,
        measuredHeightMm: anno.measuredHeightMm,
      })
      const updated = res.data
      const idx = annotations.value.findIndex(a => a.annotationId === updated.annotationId)
      if (idx >= 0) annotations.value[idx] = updated
      if (beforeSnapshot && !sameAnnoGeometry(beforeSnapshot, updated)) {
        pushAnnoHistory({ type: 'update', annotationId: updated.annotationId, currentId: updated.annotationId, before: beforeSnapshot, after: cloneAnno(updated) })
      }
      annoHistoryPendingBefore = null
      redrawAnnotations()
    } catch {
      annoHistoryPendingBefore = null
      ElMessage.error('标注微调保存失败')
    }
  }, 260)
}

const nudgeSelectedAnnotation = ({ dxPx = 0, dyPx = 0, dwPx = 0, dhPx = 0 }) => {
  const anno = selectedDoctorAnnotation.value
  if (!anno || !imgNW || !imgNH) return false
  const minWidth = 6 / imgNW
  const minHeight = 6 / imgNH
  if (!annoHistoryPendingBefore) annoHistoryPendingBefore = cloneAnno(anno)
  const next = {
    x: anno.x + dxPx / imgNW,
    y: anno.y + dyPx / imgNH,
    width: anno.width + dwPx / imgNW,
    height: anno.height + dhPx / imgNH,
  }
  next.width = Math.max(minWidth, Math.min(1, next.width))
  next.height = Math.max(minHeight, Math.min(1, next.height))
  next.x = Math.max(0, Math.min(1 - next.width, next.x))
  next.y = Math.max(0, Math.min(1 - next.height, next.y))
  Object.assign(anno, next)
  applyMeasuredSize(anno)
  redrawAnnotations()
  schedulePersistSelectedAnno()
  return true
}

const handleViewerShortcut = (e) => {
  const tagName = e.target?.tagName?.toLowerCase?.()
  const isTyping = tagName === 'input' || tagName === 'textarea' || e.target?.isContentEditable
  if (isTyping || !selectedCaseId.value || !currentImage.value) return
  if ((e.ctrlKey || e.metaKey) && !e.shiftKey && (e.key === 'z' || e.key === 'Z')) {
    e.preventDefault()
    return void undoAnnoAction()
  }
  if (((e.ctrlKey || e.metaKey) && (e.key === 'y' || e.key === 'Y')) || ((e.ctrlKey || e.metaKey) && e.shiftKey && (e.key === 'z' || e.key === 'Z'))) {
    e.preventDefault()
    return void redoAnnoAction()
  }
  if (e.key === '+' || e.key === '=') {
    e.preventDefault()
    zoom(0.2)
    return
  }
  if (e.key === '-' || e.key === '_') {
    e.preventDefault()
    zoom(-0.2)
    return
  }
  if (e.key === '0') {
    e.preventDefault()
    resetViewer()
    return
  }
  if (e.key.startsWith('Arrow') && selectedDoctorAnnotation.value) {
    e.preventDefault()
    const step = 1
    if (e.shiftKey) {
      if (e.key === 'ArrowLeft') return void nudgeSelectedAnnotation({ dwPx: -step })
      if (e.key === 'ArrowRight') return void nudgeSelectedAnnotation({ dwPx: step })
      if (e.key === 'ArrowUp') return void nudgeSelectedAnnotation({ dhPx: -step })
      if (e.key === 'ArrowDown') return void nudgeSelectedAnnotation({ dhPx: step })
    }
    if (e.key === 'ArrowLeft') return void nudgeSelectedAnnotation({ dxPx: -step })
    if (e.key === 'ArrowRight') return void nudgeSelectedAnnotation({ dxPx: step })
    if (e.key === 'ArrowUp') return void nudgeSelectedAnnotation({ dyPx: -step })
    if (e.key === 'ArrowDown') return void nudgeSelectedAnnotation({ dyPx: step })
  }
  if (e.key === 'r' || e.key === 'R') {
    e.preventDefault()
    rotate(e.shiftKey ? -90 : 90)
    return
  }
  if ((e.key === 'v' || e.key === 'V') && annoTool.value !== 'select') {
    e.preventDefault()
    activateSelectTool()
    return
  }
  if ((e.key === 'm' || e.key === 'M') && annoTool.value !== 'rect') {
    e.preventDefault()
    activateRectTool()
    return
  }
  if ((e.key === 'l' || e.key === 'L') && annoTool.value !== 'line') {
    e.preventDefault()
    activateLineTool()
  }
}

const loadAnnotations = async (imageId) => {
  try {
    const res = await listAnnotations(imageId)
    annotations.value = res.data || []
    nextTick(redrawAnnotations)
  } catch { annotations.value = [] }
}

const onImgLoad = () => {
  const img = diagImgRef.value
  if (!img) return
  imgNW = img.naturalWidth
  imgNH = img.naturalHeight
  const canvas = annoCanvas.value
  if (canvas) { canvas.width = imgNW; canvas.height = imgNH }
  updateCanvasCursor()
  syncRenderedImageSize()
  nextTick(redrawAnnotations)
}

watch(visibleAnnotations, () => nextTick(redrawAnnotations), { deep: true })

/* 画布重绘 */
const redrawAnnotations = () => {
  const canvas = annoCanvas.value
  if (!canvas || !imgNW) return
  const ctx = canvas.getContext('2d')
  ctx.clearRect(0, 0, canvas.width, canvas.height)
  visibleAnnotations.value.forEach(a => drawAnno(ctx, a, a.annotationId === selectedAnnoId.value))
  if (drawState) drawTempRect(ctx, drawState)
}

const drawAnno = (ctx, anno, selected) => {
  const px = anno.x * imgNW, py = anno.y * imgNH
  const pw = anno.width * imgNW, ph = anno.height * imgNH
  const color = anno.color || (anno.source === 'AI' ? '#1890ff' : '#52c41a')
  ctx.save()
  ctx.strokeStyle = color
  ctx.lineWidth = selected ? 3 : (anno.source === 'AI' ? 1.5 : 2)
  if (anno.source === 'AI') ctx.setLineDash([8, 4])
  else ctx.setLineDash([])
  if (anno.annoType === 'LINE') {
    ctx.beginPath()
    ctx.moveTo(px, py)
    ctx.lineTo(px + pw, py + ph)
    ctx.stroke()
  } else {
    ctx.strokeRect(px, py, pw, ph)
    ctx.globalAlpha = anno.source === 'AI' ? 0.08 : 0.12
    ctx.fillStyle = color
    ctx.fillRect(px, py, pw, ph)
    ctx.globalAlpha = 1
  }
  ctx.setLineDash([])
  if (anno.label) {
    const fontSize = Math.max(12, Math.round(imgNW * 0.014))
    ctx.font = `bold ${fontSize}px sans-serif`
    const labelText = anno.annoType === 'LINE' ? `${anno.label} ${formatAnnoMeasurement(anno)}` : anno.label
    const textW = ctx.measureText(labelText).width
    const padX = 6, padY = 4, boxH = fontSize + padY * 2
    ctx.fillStyle = color
    ctx.fillRect(px, py - boxH, textW + padX * 2, boxH)
    ctx.fillStyle = '#fff'
    ctx.fillText(labelText, px + padX, py - padY)
  }
  if (selected) {
    const hs = Math.max(6, Math.round(imgNW * 0.008))
    ctx.fillStyle = color
    ctx.setLineDash([])
    const handles = anno.annoType === 'LINE'
      ? [[px, py], [px + pw, py + ph]]
      : [[px, py], [px + pw, py], [px, py + ph], [px + pw, py + ph]]
    handles.forEach(([cx, cy], idx) => {
      const isHovered = hoveredHandle.value?.annotationId === anno.annotationId && hoveredHandle.value?.index === idx
      if (isHovered) {
        ctx.fillStyle = '#ffffff'
        ctx.fillRect(cx - hs / 2 - 2, cy - hs / 2 - 2, hs + 4, hs + 4)
        ctx.fillStyle = color
      }
      ctx.fillRect(cx - hs / 2, cy - hs / 2, hs, hs)
    })
  }
  ctx.restore()
}

const drawTempRect = (ctx, r) => {
  ctx.save()
  ctx.strokeStyle = '#ff4d4f'
  ctx.lineWidth = 2
  ctx.setLineDash([5, 4])
  if (annoTool.value === 'line') {
    ctx.beginPath()
    ctx.moveTo(r.x, r.y)
    ctx.lineTo(r.x + r.w, r.y + r.h)
    ctx.stroke()
  } else {
    ctx.strokeRect(r.x, r.y, r.w, r.h)
    ctx.globalAlpha = 0.07
    ctx.fillStyle = '#ff4d4f'
    ctx.fillRect(r.x, r.y, r.w, r.h)
  }
  ctx.restore()
}

/* 鼠标坐标 �?画布像素坐标 */
const toCanvasCoords = (e) => {
  const canvas = annoCanvas.value
  const rect = canvas.getBoundingClientRect()
  return {
    x: (e.clientX - rect.left) * (canvas.width / rect.width),
    y: (e.clientY - rect.top) * (canvas.height / rect.height)
  }
}

let _drawOrigin = null
const onAnnoMouseDown = (e) => {
  const pt = toCanvasCoords(e)
  const selectedLine = selectedDoctorAnnotation.value?.annoType === 'LINE' ? selectedDoctorAnnotation.value : null
  const handleIndex = selectedLine && annoTool.value === 'select' ? hitLineHandle(pt, selectedLine) : null
  if (e.button === 0 && handleIndex != null) {
    if (!annoHistoryPendingBefore) annoHistoryPendingBefore = cloneAnno(selectedLine)
    lineDragState = { annotationId: selectedLine.annotationId, handleIndex }
    hoveredHandle.value = { annotationId: selectedLine.annotationId, index: handleIndex }
    suppressAnnoClick = true
    updateCanvasCursor()
    return
  }
  if (e.button !== 0 || !['rect', 'line'].includes(annoTool.value)) return
  _drawOrigin = pt
  drawState = { x: _drawOrigin.x, y: _drawOrigin.y, w: 0, h: 0 }
}
const onAnnoMouseMove = (e) => {
  syncCompareCrosshair(e, 'main')
  const pt = toCanvasCoords(e)
  if (lineDragState && selectedDoctorAnnotation.value?.annotationId === lineDragState.annotationId) {
    const anno = selectedDoctorAnnotation.value
    const { startX, startY, endX, endY } = getLineEndpointsPx(anno)
    if (lineDragState.handleIndex === 0) setLineEndpointsPx(anno, pt.x, pt.y, endX, endY)
    else setLineEndpointsPx(anno, startX, startY, pt.x, pt.y)
    redrawAnnotations()
    updateCanvasCursor()
    return
  }
  if (annoTool.value === 'select' && selectedDoctorAnnotation.value?.annoType === 'LINE') {
    const handleIndex = hitLineHandle(pt, selectedDoctorAnnotation.value)
    hoveredHandle.value = handleIndex != null
      ? { annotationId: selectedDoctorAnnotation.value.annotationId, index: handleIndex }
      : null
    updateCanvasCursor()
    redrawAnnotations()
    return
  }
  if (!_drawOrigin || !['rect', 'line'].includes(annoTool.value)) return
  drawState = {
    x: Math.min(_drawOrigin.x, pt.x), y: Math.min(_drawOrigin.y, pt.y),
    w: Math.abs(pt.x - _drawOrigin.x), h: Math.abs(pt.y - _drawOrigin.y)
  }
  if (annoTool.value === 'line') {
    drawState = { x: _drawOrigin.x, y: _drawOrigin.y, w: pt.x - _drawOrigin.x, h: pt.y - _drawOrigin.y }
  }
  redrawAnnotations()
  updateCanvasCursor()
}
const onAnnoMouseUp = (e) => {
  if (lineDragState && selectedDoctorAnnotation.value?.annotationId === lineDragState.annotationId) {
    lineDragState = null
    schedulePersistSelectedAnno()
    updateCanvasCursor()
    return
  }
  if (!_drawOrigin || !['rect', 'line'].includes(annoTool.value)) return
  const finalState = drawState
  _drawOrigin = null
  const tooSmall = annoTool.value === 'line'
    ? Math.sqrt((finalState?.w || 0) ** 2 + (finalState?.h || 0) ** 2) < 8
    : (!finalState || finalState.w < 8 || finalState.h < 8)
  if (tooSmall) {
    drawState = null; redrawAnnotations(); return
  }
  // 显示标注名输入弹框（位置跟随屏幕鼠标�?
  labelPopupPos.value = { x: e.clientX, y: e.clientY + 12 }
  showLabelInput.value = true
  pendingAnnoLabel.value = ''
  nextTick(() => labelInputRef.value?.focus())
}
const onAnnoMouseLeave = () => {
  clearCompareCrosshair()
  hoveredHandle.value = null
  if (lineDragState && selectedDoctorAnnotation.value) {
    lineDragState = null
    schedulePersistSelectedAnno()
  }
  if (_drawOrigin) { _drawOrigin = null; drawState = null; redrawAnnotations() }
  updateCanvasCursor()
}
const onAnnoClick = (e) => {
  if (suppressAnnoClick) {
    suppressAnnoClick = false
    return
  }
  if (annoTool.value !== 'select') return
  const pt = toCanvasCoords(e)
  const hit = visibleAnnotations.value.find(a => {
    const ax = a.x * imgNW, ay = a.y * imgNH
    if (a.annoType === 'LINE') {
      const bx = ax + a.width * imgNW, by = ay + a.height * imgNH
      const dx = bx - ax, dy = by - ay
      const len2 = dx * dx + dy * dy
      if (!len2) return false
      const t = Math.max(0, Math.min(1, ((pt.x - ax) * dx + (pt.y - ay) * dy) / len2))
      const projX = ax + t * dx
      const projY = ay + t * dy
      const dist = Math.sqrt((pt.x - projX) ** 2 + (pt.y - projY) ** 2)
      return dist <= 8
    }
    return pt.x >= ax && pt.x <= ax + a.width * imgNW && pt.y >= ay && pt.y <= ay + a.height * imgNH
  })
  selectedAnnoId.value = hit ? hit.annotationId : null
  hoveredHandle.value = null
  redrawAnnotations()
  updateCanvasCursor()
}

const confirmAnnoLabel = async () => {
  if (!drawState || !currentImage.value) { cancelAnnoLabel(); return }
  const dto = applyMeasuredSize({
    imageId: currentImage.value.imageId,
    reportId: currentReport.value?.reportId || null,
    annoType: annoTool.value === 'line' ? 'LINE' : 'RECTANGLE',
    label: pendingAnnoLabel.value.trim() || (annoTool.value === 'line' ? '测距' : '病灶'),
    x: drawState.x / imgNW, y: drawState.y / imgNH,
    width: drawState.w / imgNW, height: drawState.h / imgNH,
    color: '#52c41a'
  })
  try {
    const res = await createAnnotation(dto)
    annotations.value.push(res.data)
    selectedAnnoId.value = res.data.annotationId
    pushAnnoHistory({ type: 'create', after: cloneAnno(res.data), currentId: res.data.annotationId })
  } catch { ElMessage.error('标注保存失败') }
  cancelAnnoLabel()
}

const cancelAnnoLabel = () => {
  showLabelInput.value = false
  pendingAnnoLabel.value = ''
  drawState = null
  redrawAnnotations()
}

const handleAnnoSelect = (anno) => {
  if (!anno) return
  selectedAnnoId.value = anno.annotationId
  redrawAnnotations()
}

const handleDeleteAnno = async (annotationId) => {
  try {
    const deletedAnno = cloneAnno(annotations.value.find(a => a.annotationId === annotationId))
    await deleteAnnotation(annotationId)
    annotations.value = annotations.value.filter(a => a.annotationId !== annotationId)
    if (deletedAnno) pushAnnoHistory({ type: 'delete', before: deletedAnno, currentId: annotationId })
    if (selectedAnnoId.value === annotationId) selectedAnnoId.value = null
    hoveredHandle.value = null
    lineDragState = null
    redrawAnnotations()
    updateCanvasCursor()
  } catch { ElMessage.error('删除标注失败') }
}

const deleteSelectedAnno = () => {
  if (selectedAnnoId.value) handleDeleteAnno(selectedAnnoId.value)
}

/* currentImage 切换时加载标�?*/
watch(currentImage, async (img) => {
  annotations.value = []
  selectedAnnoId.value = null
  hoveredHandle.value = null
  clearCompareCrosshair()
  lineDragState = null
  drawState = null
  imgNW = 0; imgNH = 0
  priorImages.value = []
  if (img) {
    await ensureFullUrl(img)
    loadAnnotations(img.imageId)
    loadPriorImageSummary(img.imageId)
  }
})

watch(compareImage, (img) => {
  if (img) ensureFullUrl(img)
})

const beforeUpload = (file) => {
  if (file.size > 50 * 1024 * 1024) { ElMessage.error('文件大小不能超过50MB'); return false }
  return true
}
const handleUpload = async ({ file }) => {
  try {
    const res = await uploadImage(file, selectedCaseId.value, 'PA')
    const data = res.data
    await ensureThumbnailUrl(data)
    await ensureFullUrl(data)
    images.value.push(data)
    currentImage.value = data
    ElMessage.success('影像上传成功')
    searchRetrieval(selectedCaseId.value, res.data.imageId).catch(() => {})
    if (!currentReport.value) {
      ElMessage.info('检测到新影像，正在自动生成AI报告�?)
      await handleGenerate()
    }
  } catch { /* ignore */ }
}

/* ─────────────── 典型病例标记 ─────────────── */
const typicalDialogVisible = ref(false)
const typicalLoading = ref(false)

const handleMarkTypical = async () => {
  if (caseInfo.value.isTypical) {
    try {
      await markTypical(selectedCaseId.value, { isTypical: 0, typicalTags: '', typicalRemark: '' })
      caseInfo.value.isTypical = 0
      ElMessage.success('已取消典型标�?)
      fetchCases()
    } catch { /* ignore */ }
  } else {
    typicalDialogVisible.value = true
  }
}

const confirmMarkTypical = async (payload) => {
  typicalLoading.value = true
  try {
    await markTypical(selectedCaseId.value, { isTypical: 1, ...payload })
    caseInfo.value.isTypical = 1
    typicalDialogVisible.value = false
    ElMessage.success('已标记为典型病例')
    fetchCases()
  } finally { typicalLoading.value = false }
}

/* ─────────────── 打印 ─────────────── */
const handlePrint = () => { window.print() }

/* ─────────────── 工作流步�?─────────────── */
const workflowSteps = computed(() => {
  const r = currentReport.value
  const status = r?.reportStatus
  const examTime = caseInfo.value.examTime
  const isSigned = status === 'SIGNED'
  
  return [
    { name: '影像上传', status: images.value.length > 0 ? 'done' : 'pending',
      time: formatTime(examTime), hint: '待操�? },
    { name: 'AI分析', status: r ? 'done' : (images.value.length > 0 ? 'active' : 'pending'),
      time: formatTime(r?.aiGenerateTime), hint: r ? '已完�? : '待生�? },
    { name: '医生审阅', status: isSigned ? 'done' : (r ? 'active' : 'pending'),
      time: isSigned ? formatTime(r?.signTime) : '', hint: isSigned ? '已完�? : (r ? '进行�? : '待操�?) },
    { name: '签发报告', status: isSigned ? 'done' : 'pending',
      time: formatTime(r?.signTime), hint: isSigned ? '已完�? : '待操�? },
    { name: '评测存档', status: isSigned ? 'done' : 'pending',
      time: '', hint: isSigned ? '已完�? : '待操�? },
  ]
})

const followupSummary = computed(() => {
  if (!currentImage.value) return ''
  if (!priorImages.value.length) {
    return hasPixelSpacing.value ? '当前图像已具备实测条件，可直接记录病灶尺寸�? : '暂无历史影像；当前可先完成本次阅片与标注�?
  }
  const latest = priorImages.value[0]
  const latestTime = latest?.shootTime || latest?.createdAt
  const latestText = latestTime ? formatDate(latestTime) : '最近一次历史检�?
  return `本病例存�?${priorImages.value.length} 次历史影像，最近一次为 ${latestText}，可用于随访对照。`
})

/* ─────────────── 病例导航 ─────────────── */
const prevCaseId = computed(() => {
  const idx = caseList.value.findIndex(c => c.caseId === selectedCaseId.value)
  return idx > 0 ? caseList.value[idx - 1].caseId : null
})
const nextCaseId = computed(() => {
  const idx = caseList.value.findIndex(c => c.caseId === selectedCaseId.value)
  return idx < caseList.value.length - 1 ? caseList.value[idx + 1].caseId : null
})
const navigateCase = (dir) => {
  const id = dir === 'prev' ? prevCaseId.value : nextCaseId.value
  if (id) {
    const c = caseList.value.find(x => x.caseId === id)
    if (c) selectCase(c)
  }
}

/* ─────────────── 新建病例 ─────────────── */
const createDialogVisible = ref(false)
const creating = ref(false)
const handleCreateCase = async (payload) => {
  creating.value = true
  try {
    const res = await createCase(payload)
    ElMessage.success('病例创建成功')
    createDialogVisible.value = false
    currentPage.value = 1
    await fetchCases()
    const newId = res.data
    if (newId) {
      const found = caseList.value.find(c => c.caseId === newId)
      if (found) {
        await selectCase(found)
      } else {
        try { const dr = await getCaseById(newId); if (dr.data) await selectCase(dr.data) } catch { /* ignore */ }
      }
    }
  } finally { creating.value = false }
}

onUnmounted(() => {
  clearTimeout(annoPersistTimer)
  window.removeEventListener('keydown', handleViewerShortcut)
  window.removeEventListener('resize', syncRenderedImageSize)
  window.removeEventListener('resize', updateIsMobile)
  revokeImageUrls(images.value)
  revokeImageUrls(priorImages.value)
})

/* ─────────────── AI 报告润色 ─────────────── */
const polishing = ref(false)
const polishResult = ref(null)
const polishDialogVisible = ref(false)

const handlePolish = async () => {
  polishing.value = true
  polishResult.value = null
  try {
    const res = await polishReport({ findings: draftFindings.value, impression: draftImpression.value })
    polishResult.value = res.data
    polishDialogVisible.value = true
  } catch (e) {
    ElMessage.error('润色失败�? + (e?.message || '请确认AI服务可用'))
  } finally { polishing.value = false }
}

const applyPolish = () => {
  if (polishResult.value) {
    draftFindings.value = polishResult.value.polished_findings || draftFindings.value
    draftImpression.value = polishResult.value.polished_impression || draftImpression.value
    polishDialogVisible.value = false
    ElMessage.success('AI润色内容已应�?)
  }
}

/* ─────────────── 置信度颜�?─────────────── */
const confColor = (v) => {
  if (v >= 0.8) return '#52c41a'
  if (v >= 0.6) return '#faad14'
  return '#ff4d4f'
}

/* ─────────────── 工具方法 ─────────────── */
const statusLabel = (s) => ({ NONE: '待生�?, AI_DRAFT: 'AI草稿', EDITING: '编辑�?, SIGNED: '已签�? }[s] || s || '�?)
const statusColor = (s) => ({ NONE: 'orange', AI_DRAFT: 'blue', EDITING: 'blue', SIGNED: 'green' }[s] || 'gray')
const genderLabel = (g) => ({ M: '�?, F: '�? }[g] || g || '�?)

const formatDate = (d) => {
  if (!d) return '�?
  return new Date(d).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
const formatTime = (d) => {
  if (!d) return ''
  const dt = new Date(d)
  return `${String(dt.getHours()).padStart(2,'0')}:${String(dt.getMinutes()).padStart(2,'0')}`
}

/* ─────────────── 初始�?─────────────── */
onMounted(async () => {
  window.addEventListener('keydown', handleViewerShortcut)
  window.addEventListener('resize', syncRenderedImageSize)
  updateIsMobile()
  window.addEventListener('resize', updateIsMobile)
  await fetchCases()
  const targetId = route.query.caseId ? String(route.query.caseId) : null
  if (targetId) {
    const found = caseList.value.find(c => String(c.caseId) === targetId)
    if (found) {
      await selectCase(found)
    } else {
      try {
        const res = await getCaseById(targetId)
        if (res.data) await selectCase(res.data)
      } catch { /* ignore */ }
    }
  }
})
</script>

<style scoped>
/* ══════════════════════════════════════════�?
   整体布局
══════════════════════════════════════════�?*/
.workstation {
  display: flex;
  height: 100%;
  overflow: hidden;
}

.mobile-tabs {
  display: none;
  gap: 6px;
  padding: 10px 12px 6px;
  background: var(--xrag-panel);
  border-bottom: 1px solid var(--xrag-border);
}

.mobile-tab {
  flex: 1;
  height: 34px;
  border-radius: 8px;
  border: 1px solid var(--xrag-border-strong);
  background: transparent;
  color: var(--xrag-text-soft);
  font-size: 12px;
  cursor: pointer;
  transition: all .2s ease;
}

.mobile-tab.active {
  background: rgba(74, 158, 255, 0.18);
  border-color: rgba(74, 158, 255, 0.35);
  color: #f4f8ff;
}

@media (max-width: 1024px) {
  .workstation {
    flex-direction: column;
  }

  .workspace {
    flex: 1;
    min-height: 0;
  }
}

@media (max-width: 768px) {
  .mobile-tabs {
    display: flex;
  }

  .viewer-panel {
    width: 100%;
  }
}
.card-time { font-size: 10px; color: var(--xrag-text-faint); }

.load-more {
  text-align: center;
  padding: 8px;
  font-size: 12px;
  color: #40a9ff;
  cursor: pointer;
}

/* ══════════════════════════════════════════�?
   右侧工作�?
══════════════════════════════════════════�?*/
.workspace {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--xrag-bg);
}
.workspace-empty {
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: rgba(220, 231, 247, 0.7);
  font-size: 14px;
}

/* 滚动内容�?*/
.ws-scroll {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  padding: 12px;
  gap: 10px;
  min-height: 0;
}
.ws-scroll::-webkit-scrollbar { width: 6px; }
.ws-scroll::-webkit-scrollbar-track { background: transparent; }
.ws-scroll::-webkit-scrollbar-thumb { background: rgba(111,134,166,0.36); border-radius: 3px; }

/* 主内容分�?*/
.ws-body {
  display: flex;
  flex: 0 0 440px;
  overflow: hidden;
  gap: 12px;
}

/* ─── 影像查看�?─── */
.viewer-panel {
  width: 420px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #111820;
  border-radius: 8px;
  overflow: hidden;
}

.viewer-canvas {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 8px;
  min-height: 200px;
}
.image-wrapper {
  transition: transform .2s;
  position: relative;
  display: inline-block;
}
.dicom-img {
  max-width: 100%;
  max-height: 280px;
  display: block;
  object-fit: contain;
}
.ai-marker {
  position: absolute;
  top: 8px; right: 8px;
  background: rgba(24,144,255,0.85);
  color: #fff;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 3px;
}
.viewer-empty {
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  color: rgba(220,231,247,0.65); font-size: 12px;
}

.thumb-strip {
  display: flex; gap: 6px; padding: 8px 10px;
  background: rgba(255,255,255,0.03);
  border-top: 1px solid rgba(255,255,255,0.06);
  overflow-x: auto; flex-shrink: 0;
}
.thumb-item {
  width: 48px; flex-shrink: 0;
  cursor: pointer; text-align: center;
  position: relative;
}
.thumb-item img {
  width: 48px; height: 40px; object-fit: cover;
  border-radius: 3px; border: 2px solid transparent;
}
.thumb-active img { border-color: #40a9ff; }
.thumb-item span { font-size: 9px; color: rgba(255,255,255,0.4); }
.thumb-upload .thumb-add {
  width: 48px; height: 40px;
  border: 1px dashed rgba(255,255,255,0.2);
  border-radius: 3px;
  display: flex; align-items: center; justify-content: center;
  color: rgba(255,255,255,0.3); cursor: pointer;
}
.thumb-upload :deep(.el-upload) { display: block; }

.upload-zone {
  padding: 12px;
  flex-shrink: 0;
}
.upload-zone :deep(.el-upload-dragger) {
  background: rgba(255,255,255,0.03);
  border-color: rgba(255,255,255,0.15);
  padding: 16px;
  height: auto;
}

/* ─── 标注工具 & 画布 ─── */
.tool-sep-v {
  width: 1px; height: 14px; background: rgba(255,255,255,0.15);
  margin: 0 2px; align-self: center; flex-shrink: 0;
}
.tool-active {
  background: rgba(24,144,255,0.25) !important;
  color: #40a9ff !important;
  border-color: rgba(24,144,255,0.4) !important;
}
.tool-danger { color: #ff7875 !important; }
.layer-toggle { font-size: 10px !important; font-weight: 700; min-width: 22px; }

.anno-overlay {
  position: absolute;
  top: 0; left: 0;
  width: 100%; height: 100%;
  pointer-events: auto;
  cursor: default;
}
.anno-draw-mode { cursor: crosshair; }
.crosshair-line {
  position: absolute;
  background: rgba(24, 144, 255, 0.9);
  pointer-events: none;
  z-index: 4;
  box-shadow: 0 0 0 1px rgba(255,255,255,0.16);
}
.crosshair-line-v {
  top: 0;
  bottom: 0;
  width: 1px;
  transform: translateX(-0.5px);
}
.crosshair-line-h {
  left: 0;
  right: 0;
  height: 1px;
  transform: translateY(-0.5px);
}
.crosshair-readout {
  position: absolute;
  z-index: 5;
  max-width: 210px;
  padding: 4px 8px;
  border-radius: 4px;
  background: rgba(0,0,0,0.72);
  color: #fff;
  font-size: 11px;
  line-height: 1.4;
  pointer-events: none;
  white-space: nowrap;
}
.crosshair-readout-compare {
  text-align: right;
}
.scale-bar {
  position: absolute;
  right: 12px;
  bottom: 12px;
  height: 14px;
  border-bottom: 3px solid rgba(255,255,255,0.95);
  pointer-events: none;
  z-index: 4;
}
.scale-bar-compare {
  right: 12px;
  bottom: 12px;
}
.scale-bar-tick {
  position: absolute;
  bottom: -3px;
  width: 0;
  border-left: 2px solid rgba(255,255,255,0.95);
}
.scale-bar-tick-start,
.scale-bar-tick-end,
.scale-bar-tick-mid {
  height: 14px;
}
.scale-bar-tick-quarter,
.scale-bar-tick-three-quarter {
  height: 9px;
}
.scale-bar-tick-start { left: 0; }
.scale-bar-tick-quarter { left: 25%; }
.scale-bar-tick-mid { left: 50%; }
.scale-bar-tick-three-quarter { left: 75%; }
.scale-bar-tick-end { right: 0; }
.scale-bar-label {
  position: absolute;
  right: 0;
  bottom: 16px;
  padding: 1px 6px;
  border-radius: 3px;
  background: rgba(0,0,0,0.72);
  color: #fff;
  font-size: 11px;
  white-space: nowrap;
}
.compare-image-badge {
  position: absolute;
  left: 8px;
  top: 34px;
  background: rgba(0,0,0,0.55);
  color: #fff;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 3px;
  z-index: 4;
  pointer-events: none;
}
.compare-image-badge-second {
  top: 60px;
}
.compare-image-badge-third {
  top: 86px;
}

/* ─── 标注名输入弹框（fixed定位�?─── */
.anno-label-popup {
  position: fixed;
  z-index: 9999;
  background: #1d2433;
  border: 1px solid rgba(64,169,255,0.4);
  border-radius: 8px;
  padding: 12px 14px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.5);
  min-width: 240px;
}
.anno-popup-title {
  font-size: 12px; font-weight: 600; color: rgba(255,255,255,0.8);
  margin-bottom: 8px;
}
.anno-popup-hint {
  margin-top: 8px;
  font-size: 11px;
  color: rgba(255,255,255,0.72);
}
.anno-popup-subhint {
  margin-top: 4px;
  font-size: 10px;
  color: rgba(255,255,255,0.45);
  line-height: 1.5;
}
.anno-popup-input {
  width: 100%; box-sizing: border-box;
  background: rgba(255,255,255,0.07);
  border: 1px solid rgba(255,255,255,0.15);
  border-radius: 4px; padding: 6px 10px;
  color: #fff; font-size: 13px; outline: none;
}
.anno-popup-input::placeholder { color: rgba(255,255,255,0.3); }
.anno-popup-input:focus { border-color: #40a9ff; }
.anno-popup-btns { display: flex; gap: 8px; margin-top: 8px; }
.anno-popup-ok {
  flex: 1; padding: 5px; border: none; border-radius: 4px;
  background: #1890ff; color: #fff; cursor: pointer; font-size: 12px;
}
.anno-popup-ok:hover { background: #40a9ff; }
.anno-popup-cancel {
  padding: 5px 12px; border: 1px solid rgba(255,255,255,0.15);
  border-radius: 4px; background: transparent; color: rgba(255,255,255,0.5);
  cursor: pointer; font-size: 12px;
}
.anno-popup-cancel:hover { border-color: rgba(255,255,255,0.3); color: rgba(255,255,255,0.8); }

.viewer-meta-overlay {
  position: absolute;
  left: 10px;
  bottom: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-width: calc(100% - 20px);
  pointer-events: none;
}
.viewer-meta-chip {
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(0,0,0,0.45);
  border: 1px solid rgba(255,255,255,0.08);
  color: rgba(255,255,255,0.82);
  font-size: 11px;
  line-height: 1.4;
  backdrop-filter: blur(6px);
}
.viewer-meta-chip.chip-ok {
  color: #b7eb8f;
  border-color: rgba(149,222,100,0.25);
}
.viewer-meta-chip.chip-warn {
  color: #ffd591;
  border-color: rgba(255,169,64,0.25);
}
.viewer-meta-chip.chip-info {
  color: #91d5ff;
  border-color: rgba(64,169,255,0.25);
}

/* ══════════════════════════════════════════�?
   缩略图删除按�?
══════════════════════════════════════════�?*/
.thumb-del {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: rgba(0,0,0,0.55);
  color: #fff;
  display: none;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  cursor: pointer;
}
.thumb-item:hover .thumb-del { display: flex; }

</style>


<style scoped>
.viewer-stage { display: flex; align-items: center; justify-content: center; gap: 12px; width: 100%; }
.viewer-stage-compare { justify-content: space-between; }
.compare-image-wrapper { position: relative; display: inline-block; }
.compare-image-wrapper .dicom-img { max-height: 280px; }
.compare-image-tag { position: absolute; left: 8px; top: 8px; background: rgba(0,0,0,0.55); color: #fff; font-size: 11px; padding: 2px 8px; border-radius: 3px; }
.thumb-compare img { border-color: #faad14 !important; box-shadow: 0 0 0 1px rgba(250,173,20,0.35); }
</style>









