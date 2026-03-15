<template>
  <div class="workstation">
    <div v-if="isMobile" class="mobile-tabs">
      <button :class="['mobile-tab', mobileTab === 'cases' && 'active']" @click="mobileTab = 'cases'">病例</button>
      <button :class="['mobile-tab', mobileTab === 'workspace' && 'active']" @click="mobileTab = 'workspace'">工作区</button>
    </div>
    <CasePanel
      class="perf-section"
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

    <!--  ? -->
    <div class="workspace" v-if="selectedCaseId" v-show="!isMobile || mobileTab === 'workspace'">
      <!--  -->
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

      <!--  -->
      <div class="ws-scroll">
      <div class="ws-body">
        <!-- DICOM ?-->
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
                :style="{ transform: 'scale(' + viewerScale + ') rotate(' + viewerRotate + 'deg)' }">
                <div v-if="isCurrentDicom" ref="dicomViewportRef" class="dicom-viewport"></div>
                <img v-else :src="currentImage.fullUrl" class="dicom-img" ref="diagImgRef" alt="image" decoding="async" fetchpriority="high"
                  @load="onImgLoad" @error="onImgError" draggable="false" @click.stop="openFullscreen(currentImage?.fullUrl)" />
                <!-- ?-->
                <canvas ref="annoCanvas" class="anno-overlay"
                  :class="{ 'anno-draw-mode': annoTool === 'rect' || annoTool === 'line' }"
                  @mousedown="onAnnoMouseDown" @mousemove="onAnnoMouseMove"
                  @mouseup="onAnnoMouseUp" @mouseleave="onAnnoMouseLeave"
                  @click="onAnnoClick" />
                <div v-if="currentImage" class="viewer-meta-overlay">
                  <span class="viewer-meta-chip">{{ currentImage.viewPosition || 'PA' }}</span>
                  <span class="viewer-meta-chip">{{ currentImage.imgWidth || imgNW || '-' }}×{{ currentImage.imgHeight || imgNH || '-' }}px</span>
                  <span v-if="hasPixelSpacing" class="viewer-meta-chip chip-ok">像素间距 {{ pixelSpacingText }}</span>
                  <span v-else class="viewer-meta-chip chip-warn">未读取像素间距</span>
                  <span class="viewer-meta-chip chip-info">缩放 {{ viewerScaleText }}</span>
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
              <div v-if="compareMode" class="compare-pane">
                <template v-if="compareImage">
                  <div class="image-wrapper compare-image-wrapper"
                    @mousemove="onCompareImageMouseMove"
                    @mouseleave="clearCompareCrosshair"
                    :style="{ transform: 'scale(' + viewerScale + ') rotate(' + viewerRotate + 'deg)' }">
                    <div v-if="isCompareDicom" ref="compareDicomViewportRef" class="dicom-viewport compare-dicom-viewport"></div>
                    <img v-else :src="compareImage.fullUrl" class="dicom-img compare-dicom-img" ref="compareImgRef" alt="" decoding="async" loading="lazy"
                      @load="onCompareImgLoad"
                      @error="onCompareImgError" draggable="false" />
                    <div class="compare-image-tag">{{ compareImage.fileName || '' }}</div>
                    <div v-if="compareScaleBadgeText" class="compare-image-badge">{{ compareScaleBadgeText }}</div>
                    <div v-if="compareSelectedMeasurementText" class="compare-image-badge compare-image-badge-second">{{ compareSelectedMeasurementText }}</div>
                    <div v-if="compareMeasurementDeltaText" class="compare-image-badge compare-image-badge-third">{{ compareMeasurementDeltaText }}</div>
                    <div v-if="compareScaleBarWidthStyle" class="scale-bar scale-bar-compare" :style="compareScaleBarWidthStyle">
                      <span class="scale-bar-tick scale-bar-tick-start"></span>
                      <span class="scale-bar-tick scale-bar-tick-quarter"></span>
                      <span class="scale-bar-tick scale-bar-tick-mid"></span>
                      <span class="scale-bar-tick scale-bar-tick-three-quarter"></span>
                      <span class="scale-bar-tick scale-bar-tick-end"></span>
                      <span class="scale-bar-label">{{ compareScaleBarLabel }}</span>
                    </div>
                    <template v-if="compareCrosshair.active">
                      <div class="crosshair-line crosshair-line-v" :style="crosshairLineStyle('x')"></div>
                      <div class="crosshair-line crosshair-line-h" :style="crosshairLineStyle('y')"></div>
                      <div class="crosshair-readout crosshair-readout-compare" :style="crosshairReadoutStyle('compare')">{{ compareCrosshairText }}</div>
                    </template>
                  </div>
                </template>
                <template v-else>
                  <div class="compare-placeholder">
                    <p>请选择缩略图设为对比影像</p>
                  </div>
                </template>
              </div>
            </div>
            <div v-else class="viewer-empty">
              <el-icon :size="48" class="empty-icon"><Picture /></el-icon>
              <p>请选择影像或上传新图</p>
            </div>
          </div>
          <!-- fullscreen preview -->
          <div v-if="fullscreenUrl" class="fullscreen-overlay" @click="closeFullscreen">
            <img :src="fullscreenUrl" class="fullscreen-img" alt="full" />
            <span class="fullscreen-hint">点击关闭</span>
          </div>
          <!--  -->
          <div class="thumb-strip" v-if="images.length > 0">
            <div v-for="img in images" :key="img.imageId"
              :class="['thumb-item', currentImage?.imageId === img.imageId && 'thumb-active', compareImage?.imageId === img.imageId && 'thumb-compare']"
              @click="handleThumbSelect(img)">
              <img :src="img.thumbnailUrl || img.fullUrl" :alt="img.viewPosition" loading="lazy" decoding="async" fetchpriority="low" />
              <span>{{ img.viewPosition || 'PA' }}</span>
              <div class="thumb-del" @click.stop="handleDeleteImage(img)"><el-icon><Close /></el-icon></div>
            </div>
            <!--  -->
            <el-upload :show-file-list="false" :before-upload="beforeUpload"
              :http-request="handleUpload" accept=".jpg,.jpeg,.png,.dcm" class="thumb-upload">
              <div class="thumb-add"><el-icon><Plus /></el-icon></div>
            </el-upload>
          </div>
          <!--  -->
          <div v-else class="upload-zone">
            <el-upload drag :show-file-list="false" :before-upload="beforeUpload"
              :http-request="handleUpload" accept=".jpg,.jpeg,.png,.dcm">
              <el-icon :size="28" class="upload-icon"><UploadFilled /></el-icon>
              <div class="upload-title">
                拖拽或点击上传检查图像
              </div>
              <div class="upload-tip">
                支持 JPG / PNG / DICOM，50MB
              </div>
            </el-upload>
          </div>

          <!--  -->
          <AnnotationList
            :visible-annotations="visibleAnnotations"
            :total-count="annotations.length"
            :selected-anno-id="selectedAnnoId"
            :format-anno-measurement="formatAnnoMeasurement"
            @select="handleAnnoSelect"
            @delete="handleDeleteAnno"
          />
        </div>

        <!-- fixed -->
        <teleport to="body">
          <div v-if="showLabelInput" class="anno-label-popup"
            :style="{ left: labelPopupPos.x + 'px', top: labelPopupPos.y + 'px' }">
            <div class="anno-popup-title">输入标注名称</div>
            <input ref="labelInputRef" v-model="pendingAnnoLabel" class="anno-popup-input"
              placeholder="请输入标注名称"
              @keyup.enter="confirmAnnoLabel" @keyup.esc="cancelAnnoLabel" />
            <div class="anno-popup-hint">{{ drawMeasurementHint }}</div>
            <div class="anno-popup-subhint">{{ pixelSpacingGuideText }}</div>
            <div class="anno-popup-btns">
              <button class="anno-popup-ok" @click="confirmAnnoLabel">确定</button>
              <button class="anno-popup-cancel" @click="cancelAnnoLabel">取消</button>
            </div>
          </div>
        </teleport>

        <!--  -->
        <ReportPanel
          v-model:report-tab="reportTab"
          v-model:show-ai-compare="showAiCompare"
          v-model:findings="draftFindings"
          v-model:impression="draftImpression"
          :case-info="caseInfo"
          :current-report="currentReport"
          :generating="generating"
          :has-current-image="!!currentImage"
          :polishing="polishing"
          :term-loading="termLoading"
          :term-last-count="termLastCount"
          :doctor-name="userStore.userInfo?.realName || '当前医生'"
          :format-date="formatDate"
          @generate="handleGenerate"
          @polish="handlePolish"
          @term-normalize="handleTermNormalize"
        />
      </div>

      <!-- ?-->
      <SimilaritySection
        :similar-cases="similarCases"
        :current-doctor-id="userStore.userInfo?.userId"
        :allow-all="userStore.isAdmin"
        @select="selectCaseById"
      />

      <!--  -->
      <WorkflowProgress
        :workflow-steps="workflowSteps"
        :followup-summary="followupSummary"
        :has-pixel-spacing="hasPixelSpacing"
        :pixel-spacing-text="pixelSpacingText"
        :selected-doctor-annotation="selectedDoctorAnnotation"
        :format-anno-measurement="formatAnnoMeasurement"
      />

      </div><!-- /ws-scroll -->

      <!-- ?-->
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

    <!--  -->
    <div class="workspace workspace-empty" v-else v-show="!isMobile || mobileTab === 'workspace'">
      <el-icon :size="64" class="placeholder-icon"><Monitor /></el-icon>
      <p class="placeholder-text">请从左侧选择病例开始阅片与报告书写</p>
    </div>

    <!--  -->
    <CreateCaseDialog
      v-if="createDialogVisible"
      v-model="createDialogVisible"
      :loading="creating"
      @submit="handleCreateCase"
    />

    <!--  -->
    <TypicalCaseDialog
      v-if="typicalDialogVisible"
      v-model="typicalDialogVisible"
      :loading="typicalLoading"
      @confirm="confirmMarkTypical"
    />

    <!-- ?-->
    <TermDialog
      v-if="termDialogVisible"
      v-model="termDialogVisible"
      :items="termDialogList"
      :auto-select-all="true"
      @confirm="confirmTermReplace"
    />

    <!-- AI  -->
    <PolishDialog
      v-if="polishDialogVisible"
      v-model="polishDialogVisible"
      :polish-result="polishResult"
      :draft-findings="draftFindings"
      :draft-impression="draftImpression"
      @apply="applyPolish"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close, Plus, UploadFilled, Picture, Monitor } from '@element-plus/icons-vue'
import { listCases, getCaseById, markTypical, createCase, deleteCase, importCases } from '@/api/case'
import { listImages, listPriorImages, uploadImage, deleteImage, fetchImageBlob } from '@/api/image'
import { generateReport, regenerateReport, saveDraft, signReport, listReports, getReport, polishReport } from '@/api/report'
import { searchRetrieval, listRetrievalByCaseId } from '@/api/retrieval'
import { analyzeTerms, acceptCorrection } from '@/api/term'
import { listAnnotations, createAnnotation, updateAnnotation, deleteAnnotation } from '@/api/annotation'
import { runWhenIdle } from '@/utils/idle'
import { loadDicom } from '@/utils/dicom'
import { reportStatusLabel } from '@/utils/format'
const CasePanel = defineAsyncComponent(() => import('@/components/CasePanel.vue'))
const ViewerToolbar = defineAsyncComponent(() => import('@/components/ViewerToolbar.vue'))
const AnnotationList = defineAsyncComponent(() => import('@/components/AnnotationList.vue'))
const ReportPanel = defineAsyncComponent(() => import('@/components/ReportPanel.vue'))
const SimilaritySection = defineAsyncComponent(() => import('@/components/SimilaritySection.vue'))
const WorkflowProgress = defineAsyncComponent(() => import('@/components/WorkflowProgress.vue'))
const WorkstationFooter = defineAsyncComponent(() => import('@/components/WorkstationFooter.vue'))
const WorkstationHeader = defineAsyncComponent(() => import('@/components/WorkstationHeader.vue'))
const PolishDialog = defineAsyncComponent(() => import('@/components/PolishDialog.vue'))
const CreateCaseDialog = defineAsyncComponent(() => import('@/components/CreateCaseDialog.vue'))
const TypicalCaseDialog = defineAsyncComponent(() => import('@/components/TypicalCaseDialog.vue'))
const TermDialog = defineAsyncComponent(() => import('@/components/TermDialog.vue'))

const route = useRoute()
const userStore = useUserStore()
const isMobile = ref(false)
const mobileTab = ref('cases')

/*    */
const STATUS_FILTERS = [
  { label: '待生成', value: 'NONE', color: 'orange' },
  { label: 'AI草稿', value: 'AI_DRAFT', color: 'blue' },
  { label: '编辑中', value: 'EDITING', color: 'blue' },
  { label: '已签发', value: 'SIGNED', color: 'green' },
]

/*    */
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
    // ?
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
    // ?
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

/*    */
const selectedCaseId = ref(null)
const caseInfo = ref({})
const images = ref([])
const currentImage = ref(null)
const compareMode = ref(false)
const compareImage = ref(null)
const currentReport = ref(null)
const similarCases = ref([])
const priorImages = ref([])
const showAiCompare = ref(false)
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
  } catch (err) {
    const status = err?.response?.status
    if (status === 413) {
      ElMessage.error('影像过大，无法生成缩略图')
      return
    }
    ElMessage.error(err?.response?.data?.message || '缩略图加载失败')
  }
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
  similarCases.value = []
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
      await Promise.all([loadSimilarCases()])
      return
    }

    currentReport.value = null
    draftFindings.value = ''
    draftImpression.value = ''
    similarCases.value = []
    // 若后端返回无报告，重置状态为 NONE
    if (caseInfo.value && caseInfo.value.reportStatus && caseInfo.value.reportStatus !== 'NONE') {
      const prevStatus = caseInfo.value.reportStatus
      caseInfo.value = { ...caseInfo.value, reportStatus: 'NONE' }
      const idx = caseList.value.findIndex(c => c.caseId === selectedCaseId.value)
      if (idx >= 0) {
        caseList.value[idx] = { ...caseList.value[idx], reportStatus: 'NONE' }
      }
      ElMessage.warning('已将原状态 ' + reportStatusLabel(prevStatus) + ' 重置为未生成')
    }
  } catch (e) {
    currentReport.value = null
    ElMessage.error('加载报告失败：' + (e?.message || '未知错误'))
  }
}

const loadSimilarCases = async () => {
  try {
    if (currentImage.value?.imageId) {
      const retriRes = await searchRetrieval(selectedCaseId.value, currentImage.value.imageId, 3)
      const list = (retriRes.data?.similarCases || []).slice(0, 3)
      if (list.length) { similarCases.value = list; return }
    }
    const retriRes = await listRetrievalByCaseId(selectedCaseId.value)
    const logs = retriRes.data || []
    const latestLog = logs[logs.length - 1]
    const list = (latestLog?.similarCases || []).slice(0, 3)
    if (list.length) similarCases.value = list
  } catch {
    // 保留已有结果，避免签署后回退为空
  }
}

/*  ? */
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
    // isAccepted === 0 ?null?
    termDialogList.value = list.filter(t => !t.isAccepted || t.isAccepted === 0)
    if (termDialogList.value.length === 0) {
      ElMessage.success('未发现需要纠正的术语')
    } else {
      termDialogVisible.value = true
    }
  } catch {
    ElMessage.error('术语标准化失败，请稍后重试')
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
      draftFindings.value = (draftFindings.value || "").split(orig).join(corr)
      draftImpression.value = (draftImpression.value || "").split(orig).join(corr)
      if (draftFindings.value !== beforeF || draftImpression.value !== beforeI) count++
    }
    try { await acceptCorrection(item.correctionId) } catch { /* ignore */ }
  }
  termLastCount.value = count
  termDialogVisible.value = false
  ElMessage.success('已替换 ' + count + ' 处术语')
}

const handleDeleteCase = async () => {
  try {
    await ElMessageBox.confirm('确认删除病例 ' + caseInfo.value.examNo + ' 吗？删除后不可恢复', '删除病例', { type: 'warning' })
    ElMessage.success('病例已删除')
    selectedCaseId.value = null
    caseInfo.value = {}
    images.value = []
    currentImage.value = null
    await fetchCases()
  } catch { /* ignore */ }
}

const handleImport = async (file) => {
  try {
    const res = await importCases(file)
    const count = res.data?.successCount ?? 0
    ElMessage.success('导入完成：成功 ' + count)
  } catch (e) {
    ElMessage.error('导入失败：' + (e?.message || '请检查文件格式'))
  }
  return false
}

const handleDeleteImage = async (img) => {
  try {
    await ElMessageBox.confirm('确认删除该影像吗？删除后不可恢复', '删除影像', { type: 'warning' })
  } catch { return }
  try {
    await deleteImage(img.imageId)
    revokeImageUrls([img])
    images.value = images.value.filter(i => i.imageId !== img.imageId)
    if (currentImage.value?.imageId === img.imageId) {
      currentImage.value = images.value[0] || null
    }
    ElMessage.success('影像已删除')
  } catch { /* ignore */ }
}

/*    */
const reportTab = ref('edit')
const draftFindings = ref('')
const draftImpression = ref('')
const generating = ref(false)
const saving = ref(false)
const signing = ref(false)

/*    */
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
      '确认为 ' + noneCount.value + ' 条未生成病例批量生成 AI 草稿？',
      '批量生成',
      { confirmButtonText: '开始生成', cancelButtonText: '取消', type: 'info' }
    )
  } catch { return }

  batchGenerating.value = true
  batchCancel = false

  try {
    const res = await listCases({ reportStatus: 'NONE', page: 1, pageSize: 200, sortOrder: 'asc' })
    const cases = res.data?.list || res.data?.records || []
    batchProgress.value = { current: 0, total: cases.length }

    for (let i = 0; i < cases.length; i++) {
      if (batchCancel) { ElMessage.info('已取消批量生成'); break }

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
        console.warn('[batch] case ' + c.examNo + ' failed:', err)
      }
      batchProgress.value = { current: i + 1, total: cases.length }
    }

    if (!batchCancel) ElMessage.success('已完成 ' + batchProgress.value.current + ' 条病例生成')
    await fetchCases()
    if (selectedCaseId.value) await loadReport()
  } finally {
    batchGenerating.value = false
  }
}

const handleGenerate = async () => {
  if (!currentImage.value) { ElMessage.warning('请先选择或上传影像'); return }
  generating.value = true
  try {
    const res = await generateReport({ caseId: selectedCaseId.value, imageId: currentImage.value.imageId })
    currentReport.value = res.data
    draftFindings.value = res.data.finalFindings || res.data.aiFindings || ''
    draftImpression.value = res.data.finalImpression || res.data.aiImpression || ''
    
    ElMessage.success('AI 草稿已生成')
    fetchCases()
  } finally { generating.value = false }
}

const handleRegenerate = async () => {
  if (!currentReport.value?.reportId) {
    ElMessage.warning('暂无可重生的报告')
    return
  }
  generating.value = true
  try {
    const res = await regenerateReport(currentReport.value.reportId)
    currentReport.value = res.data
    draftFindings.value = res.data.finalFindings || res.data.aiFindings || ''
    draftImpression.value = res.data.finalImpression || res.data.aiImpression || ''
    
    ElMessage.success('已重新生成 AI 草稿')
    fetchCases()
  } catch {
    // 
  } finally { generating.value = false }
}

const ensureCurrentReportId = async () => {
  const reportId = currentReport.value?.reportId
  if (reportId) return reportId
  await loadReport()
  if (!currentReport.value?.reportId) {
    throw new Error('未找到报告 ID')
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
  if (currentReport.value?.reportStatus === 'SIGNED') {
    ElMessage.warning('已签发的报告不可再编辑')
    return
  }
  saving.value = true
  try {
    await doSave()
    ElMessage.success('草稿已保存')
    fetchCases()
  } catch {
    // 
  } finally { saving.value = false }
}

const handleSign = async () => {
  if (!currentReport.value?.reportId) {
    ElMessage.error('当前无可签署的报告')
    return
  }
  if (currentReport.value?.reportStatus === 'SIGNED') {
    ElMessage.info('报告已签发，无需重复签署')
    return
  }
  try {
    await ElMessageBox.confirm('确认签署并提交该报告吗？', '签署报告', {
      confirmButtonText: '确认签署', cancelButtonText: '取消',
      confirmButtonClass: 'el-button--success', type: 'warning'
    })
  } catch { return }

  signing.value = true
  try {
    const reportId = currentReport.value.reportId

    if (currentReport.value.reportStatus === 'EDITING' || currentReport.value.reportStatus === 'AI_DRAFT') {
      // ?
      await saveDraft(reportId, {
        finalFindings: draftFindings.value,
        finalImpression: draftImpression.value
      })
    }

    await signReport(reportId)
    try {
      const detailRes = await getReport(reportId)
      currentReport.value = detailRes.data
      draftFindings.value = detailRes.data?.finalFindings || detailRes.data?.aiFindings || ''
      draftImpression.value = detailRes.data?.finalImpression || detailRes.data?.aiImpression || ''
      if (caseInfo.value) caseInfo.value.reportStatus = 'SIGNED'
    } catch {
      await loadReport()
    }
    ElMessage.success('报告已签署')
    fetchCases()

  } catch (err) {
    ElMessage.error('签署失败：' + (err?.message || ''))
    await loadReport()
  } finally { signing.value = false }
}


/*    */
const viewerRef = ref(null)
const dicomViewportRef = ref(null)
const compareDicomViewportRef = ref(null)
const viewerScale = ref(1)
const viewerRotate = ref(0)
const fullscreenUrl = ref('')
// 仅在浏览模式下允许点图放大，避免标注/测距被打断
const openFullscreen = (url) => {
  if (annoTool.value !== 'select') return
  if (url) fullscreenUrl.value = url
}
const closeFullscreen = () => { fullscreenUrl.value = '' }
const clampViewerScale = (value) => Math.max(0.3, Math.min(4, value))
const normalizeViewerRotate = (value) => ((value % 360) + 360) % 360
const isDicomImage = (image) => {
  const type = (image?.fileType || '').toString().toLowerCase()
  if (type === 'dcm' || type === 'dicom') return true
  const name = (image?.fileName || '').toString().toLowerCase()
  return name.endsWith('.dcm')
}
const isCurrentDicom = computed(() => isDicomImage(currentImage.value))
const isCompareDicom = computed(() => isDicomImage(compareImage.value))
const currentPixelSpacingX = computed(() => Number(currentImage.value?.pixelSpacingXmm) || null)
const currentPixelSpacingY = computed(() => Number(currentImage.value?.pixelSpacingYmm) || null)
const hasPixelSpacing = computed(() => !!(currentPixelSpacingX.value && currentPixelSpacingY.value))
const pixelSpacingText = computed(() => hasPixelSpacing.value
  ? currentPixelSpacingX.value.toFixed(3) + ' × ' + currentPixelSpacingY.value.toFixed(3) + ' mm/px'
  : '')
const pixelSpacingGuideText = computed(() => hasPixelSpacing.value
  ? pixelSpacingText.value
  : '当前影像缺少像素间距信息，测量以像素为单位')
const viewerScaleText = computed(() => Math.round(viewerScale.value * 100) + '%')
const viewerRotationText = computed(() => normalizeViewerRotate(viewerRotate.value).toString())
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
  const xText = metric.xMm != null ? (metric.xPx + ' px / ' + metric.xMm.toFixed(1) + ' mm') : (metric.xPx + ' px')
  const yText = metric.yMm != null ? (metric.yPx + ' px / ' + metric.yMm.toFixed(1) + ' mm') : (metric.yPx + ' px')
  return (prefix ? prefix + ' ' : '') + 'X ' + xText + '  Y ' + yText
}

const mainCrosshairText = computed(() => formatCrosshairMetric(currentImage.value, ''))
const compareCrosshairText = computed(() => formatCrosshairMetric(compareImage.value, ''))

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
    return metric.lengthMm != null ? (metric.lengthMm.toFixed(1) + ' mm') : (Math.round(metric.lengthPx) + ' px')
  }
  if (metric.widthMm != null && metric.heightMm != null) {
    return metric.widthMm.toFixed(1) + '×' + metric.heightMm.toFixed(1) + ' mm'
  }
  return Math.round(metric.widthPx) + '×' + Math.round(metric.heightPx) + ' px'
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
      label: ''
    }
  }
  const maxPx = Math.max(metric.widthPx, metric.heightPx)
  const maxMm = metric.widthMm != null && metric.heightMm != null
    ? Math.max(metric.widthMm, metric.heightMm)
    : null
  return {
    valuePx: maxPx,
    valueMm: maxMm,
    label: ''
  }
}

const classifyComparableDelta = (delta, ratio) => {
  const absDelta = Math.abs(delta || 0)
  const absRatio = Math.abs(ratio || 0)
  if (absRatio < 5 && absDelta < 2) return ''
  return delta > 0 ? '' : ''
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
  const valueText = useMm ? (sign + delta.toFixed(1) + ' mm') : (sign + Math.round(delta) + ' px')
  const ratioText = ratio != null ? (' / ' + sign + ratio.toFixed(1) + '%') : ''
  const labelText = label || '变化'
  return labelText + ': ' + valueText + ratioText
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
    label: mm + ' mm',
    style: { width: Math.max(32, Math.min(140, widthPx)).toFixed(1) + 'px' }
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
  const scaleText = ' ' + viewerScaleText.value
  return compareScaleBarLabel.value ? scaleText + ' ｜ ' + compareScaleBarLabel.value : scaleText
})

const crosshairLineStyle = (axis) => {
  if (!compareCrosshair.value.active) return {}
  return axis === 'x'
    ? { left: (compareCrosshair.value.xRatio * 100).toFixed(2) + '%' }
    : { top: (compareCrosshair.value.yRatio * 100).toFixed(2) + '%' }
}

const crosshairReadoutStyle = (side) => {
  if (!compareCrosshair.value.active) return {}
  const xPercent = compareCrosshair.value.xRatio * 100
  const yPercent = compareCrosshair.value.yRatio * 100
  return {
    left: 'clamp(8px, ' + xPercent.toFixed(2) + '%, calc(100% - 220px))',
    top: 'clamp(8px, calc(' + yPercent.toFixed(2) + '% + 10px), calc(100% - 30px))',
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
const activeAnnoToolLabel = computed(() => ({ select: '浏览', rect: '框选', line: '测量' }[annoTool.value] || ''))
const viewerShortcutHint = computed(() => '滚轮缩放 · R 旋转 · Shift+R 重置 · V 对比')
const viewerHeaderText = computed(() => {
  if (!currentImage.value) return '未选择影像'
  return currentImage.value.fileName + '   ' + viewerScaleText.value + '   ' + viewerRotationText.value + '  ' + activeAnnoToolLabel.value
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
let cornerstoneRef = null
const ensureCornerstoneReady = async () => {
  if (cornerstoneRef) return cornerstoneRef
  const { cornerstone } = await loadDicom()
  cornerstoneRef = cornerstone
  return cornerstoneRef
}
const ensureCornerstoneEnabled = async (el) => {
  if (!el) return false
  const cornerstone = await ensureCornerstoneReady()
  if (!cornerstone) return false
  try {
    cornerstone.getEnabledElement(el)
    return true
  } catch {
    cornerstone.enable(el)
    return true
  }
}
const renderDicomImage = async (img, targetRef, { isMain } = { isMain: false }) => {
  if (!img?.fullUrl || !targetRef?.value) return
  try {
    const cornerstone = await ensureCornerstoneReady()
    if (!cornerstone) return
    const el = targetRef.value
    await ensureCornerstoneEnabled(el)
    const imageId = 'wadouri:' + img.fullUrl
    const image = await cornerstone.loadAndCacheImage(imageId)
    cornerstone.displayImage(el, image)
    cornerstone.resize(el, true)
    if (isMain) {
      imgNW = image.width
      imgNH = image.height
      const canvas = annoCanvas.value
      if (canvas) { canvas.width = imgNW; canvas.height = imgNH }
      updateCanvasCursor()
      syncRenderedImageSize()
      nextTick(redrawAnnotations)
    } else {
      syncRenderedImageSize()
    }
  } catch (err) {
    console.error('DICOM render failed', err)
  }
}
const syncRenderedImageSize = () => {
  const mainEl = isCurrentDicom.value ? dicomViewportRef.value : diagImgRef.value
  const compareEl = isCompareDicom.value ? compareDicomViewportRef.value : compareImgRef.value
  mainRenderSize.value = {
    width: mainEl?.clientWidth || 0,
    height: mainEl?.clientHeight || 0,
  }
  compareRenderSize.value = {
    width: compareEl?.clientWidth || 0,
    height: compareEl?.clientHeight || 0,
  }
}
const onCompareImgLoad = () => {
  syncRenderedImageSize()
}

/*    */
const diagImgRef = ref(null)      //  <img> 
const annoCanvas = ref(null)      // ?
const labelInputRef = ref(null)   // 
const annotations = ref([])       // ?
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
let drawState = null  // { x, y, w, h } ?
let imgNW = 0         // ?
let imgNH = 0         // ?
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
  if (!anno) return 'N/A'
  const metric = calcAnnoMeasurement(anno)
  if (anno.annoType === 'LINE') {
    return metric.lengthMm != null ? (metric.lengthMm.toFixed(1) + ' mm') : (Math.round(metric.lengthPx) + ' px')
  }
  if (metric.widthMm != null && metric.heightMm != null) {
    return metric.widthMm.toFixed(1) + '×' + metric.heightMm.toFixed(1) + ' mm'
  }
  return Math.round(metric.widthPx) + '×' + Math.round(metric.heightPx) + ' px'
}

const drawMeasurementHint = computed(() => {
  if (!drawState || !imgNW || !imgNH) return ''
  const draftAnno = {
    annoType: annoTool.value === 'line' ? 'LINE' : 'RECTANGLE',
    width: drawState.w / imgNW,
    height: drawState.h / imgNH,
    measuredWidthMm: null,
    measuredHeightMm: null,
  }
  return formatAnnoMeasurement(draftAnno).toString()
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
  ElMessage.info('已切换到选择工具')
}

const activateRectTool = () => {
  annoTool.value = 'rect'
  ElMessage.info('已切换到矩形标注工具')
}
const activateLineTool = () => {
  annoTool.value = 'line'
  ElMessage.info('已切换到测距工具')
}

const toggleAiLayer = () => {
  showAiAnnos.value = !showAiAnnos.value
  ElMessage.info(showAiAnnos.value
    ? ('已显示AI标注（' + aiAnnotationCount.value + '处）')
    : '已隐藏AI标注')
}

const toggleDoctorLayer = () => {
  showDoctorAnnos.value = !showDoctorAnnos.value
  ElMessage.info(showDoctorAnnos.value
    ? ('已显示医生标注（' + doctorAnnotationCount.value + '处）')
    : '已隐藏医生标注')
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
  if (!syncCompareImage()) { ElMessage.info('请先选择主影像'); return }
  compareMode.value = true
  ElMessage.success('已开启对比模式')
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
    ElMessage.error('')
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
    ElMessage.error('')
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
      ElMessage.error('')
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

/*  */
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
    ctx.font = 'bold ' + fontSize + 'px sans-serif'
    const labelText = anno.annoType === 'LINE' ? (anno.label + ' ' + formatAnnoMeasurement(anno)) : anno.label
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

/*  ? */
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
  // ?
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
    label: pendingAnnoLabel.value.trim() || (annoTool.value === 'line' ? '' : ''),
    x: drawState.x / imgNW, y: drawState.y / imgNH,
    width: drawState.w / imgNW, height: drawState.h / imgNH,
    color: '#52c41a'
  })
  try {
    const res = await createAnnotation(dto)
    annotations.value.push(res.data)
    selectedAnnoId.value = res.data.annotationId
    pushAnnoHistory({ type: 'create', after: cloneAnno(res.data), currentId: res.data.annotationId })
  } catch { ElMessage.error('') }
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
  } catch { ElMessage.error('') }
}

const deleteSelectedAnno = () => {
  if (selectedAnnoId.value) handleDeleteAnno(selectedAnnoId.value)
}

/* currentImage ?*/
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
    await nextTick()
    if (isDicomImage(img)) {
      await renderDicomImage(img, dicomViewportRef, { isMain: true })
    }
  }
})

watch(compareImage, async (img) => {
  if (!img) return
  await ensureFullUrl(img)
  await nextTick()
  if (isDicomImage(img)) {
    await renderDicomImage(img, compareDicomViewportRef, { isMain: false })
  }
})

const beforeUpload = (file) => {
  if (file.size > 50 * 1024 * 1024) { ElMessage.error('文件需小于 50MB'); return false }
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
      ElMessage.info('已自动触发 AI 草稿生成')
      await handleGenerate()
    }
  } catch { /* ignore */ }
}

/*    */
const typicalDialogVisible = ref(false)
const typicalLoading = ref(false)

const handleMarkTypical = async () => {
  if (caseInfo.value.isTypical) {
    try {
      await markTypical(selectedCaseId.value, { isTypical: 0, typicalTags: '', typicalRemark: '' })
      caseInfo.value.isTypical = 0
      ElMessage.success('已取消典型病例标记')
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

/*    */
const handlePrint = () => { window.print() }

/*  ? */
const workflowSteps = computed(() => {
  const r = currentReport.value
  const status = r?.reportStatus
  const examTime = caseInfo.value.examTime
  const isSigned = status === 'SIGNED'
  
  return [
    { name: '影像上传', status: images.value.length > 0 ? 'done' : 'pending',
      time: formatTime(examTime), hint: images.value.length > 0 ? '已上传影像' : '待上传影像' },
    { name: 'AI草稿', status: r ? 'done' : (images.value.length > 0 ? 'active' : 'pending'),
      time: formatTime(r?.aiGenerateTime), hint: r ? '已生成 AI 草稿' : '点击生成 AI 草稿' },
    { name: '医生编辑', status: isSigned ? 'done' : (r ? 'active' : 'pending'),
      time: isSigned ? formatTime(r?.signTime) : '', hint: isSigned ? '已完成编辑' : (r ? '编辑中' : '等待生成草稿') },
    { name: '签署', status: isSigned ? 'done' : 'pending',
      time: formatTime(r?.signTime), hint: isSigned ? '已签署' : '待签署' },
    { name: '归档', status: isSigned ? 'done' : 'pending',
      time: '', hint: isSigned ? '已归档' : '签署后归档' },
  ]
})

const followupSummary = computed(() => {
  if (!currentImage.value) return ''
  if (!priorImages.value.length) {
    return '暂无历史影像'
  }
  const latest = priorImages.value[0]
  const latestTime = latest?.shootTime || latest?.createdAt
  const latestText = latestTime ? formatDate(latestTime) : '时间未知'
  return '历史影像 ' + priorImages.value.length + ' 张，最近一次：' + latestText
})

/*    */
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

/*    */
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

/*  AI   */
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
    ElMessage.error('报告润色失败：' + (e?.message || '请稍后重试'))
  } finally { polishing.value = false }
}

const applyPolish = () => {
  if (polishResult.value) {
    draftFindings.value = polishResult.value.polished_findings || draftFindings.value
    draftImpression.value = polishResult.value.polished_impression || draftImpression.value
    polishDialogVisible.value = false
    ElMessage.success('已应用 AI 润色结果')
  }
}

/*    */
const statusLabel = (s) => reportStatusLabel(s, '未知状态')
const statusColor = (s) => ({ NONE: 'orange', AI_DRAFT: 'blue', EDITING: 'blue', SIGNED: 'green' }[s] || 'gray')
const genderLabel = (g) => ({ M: '男', F: '女' }[g] || g || '未知')

const formatDate = (d) => {
  if (!d) return '----'
  return new Date(d).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
const formatTime = (d) => {
  if (!d) return ''
  const dt = new Date(d)
  return String(dt.getHours()).padStart(2,'0') + ':' + String(dt.getMinutes()).padStart(2,'0')
}

/*  ? */
onMounted(() => {
  window.addEventListener('keydown', handleViewerShortcut)
  window.addEventListener('resize', syncRenderedImageSize)
  updateIsMobile()
  window.addEventListener('resize', updateIsMobile)
  const runInitialLoad = async () => {
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
  }
  runWhenIdle(runInitialLoad, { timeout: 1500 })
})
</script>

<style scoped>
/* Workstation */
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

/* ?
   ?
?*/
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
  color: var(--xrag-text-faint);
  font-size: 14px;
}

/* ?*/
.ws-scroll {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  padding: 12px;
  gap: 10px;
  min-height: 0;
}

/* ?*/
.ws-body {
  width: 100%;
  align-items: stretch;
  display: flex;
  flex: 0 0 440px;
  overflow: hidden;
  gap: 12px;
}

/*  ? */
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
.dicom-viewport {
  width: 100%;
  max-width: 100%;
  height: 280px;
  background: #000;
  display: block;
}
.dicom-viewport canvas {
  width: 100% !important;
  height: 100% !important;
  display: block;
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
  color: rgba(255,255,255,0.3); font-size: 12px;
}
.empty-icon { color: rgba(255,255,255,0.2); }
.upload-icon { color: #40a9ff; }
.upload-title { font-size: 13px; color: rgba(255,255,255,0.6); margin-top: 6px; }
.upload-tip { font-size: 11px; color: rgba(255,255,255,0.3); margin-top: 4px; }
.placeholder-icon { color: rgba(0,0,0,0.18); }
.placeholder-text { color: var(--xrag-text-faint); font-size: 14px; margin: 0; }

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

/*   &   */
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

/*  fixed? */
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

.viewer-meta-overlay { position: absolute; top: 6px; left: 6px; display: flex; flex-wrap: wrap; gap: 4px; max-width: 60%; pointer-events: none; padding: 2px 4px; background: rgba(0,0,0,0.25); border-radius: 8px; opacity: 0.95; transition: opacity .15s ease; }
.viewer-meta-chip { padding: 1px 5px; border-radius: 6px; background: rgba(0,0,0,0.35); color: rgba(233,238,245,0.82); font-size: 9px; line-height: 1.2; }
.viewer-stage:hover .viewer-meta-overlay { opacity: 0; }
.compare-pane { min-height: 320px; display: flex; align-items: center; justify-content: center; }
.compare-placeholder { width: 100%; height: 100%; min-height: 320px; display: flex; align-items: center; justify-content: center; color: var(--xrag-text-soft); border: 1px dashed var(--xrag-border); border-radius: 8px; }
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

.fullscreen-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.82);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}
.fullscreen-img {
  max-width: 92vw;
  max-height: 92vh;
  object-fit: contain;
  box-shadow: 0 12px 32px rgba(0,0,0,0.6);
}
.fullscreen-hint {
  position: absolute;
  bottom: 20px;
  color: rgba(255,255,255,0.72);
  font-size: 13px;
  padding: 6px 10px;
  background: rgba(0,0,0,0.5);
  border-radius: 6px;
}

/* ?
   ?
?*/
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















