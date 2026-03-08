<template>
  <div class="workstation">
    <!-- ══ 左侧病例列表面板 ══ -->
    <aside class="case-panel">
      <div class="panel-header">
        <span class="panel-title">待处理病例</span>
        <div style="display:flex;align-items:center;gap:6px">
          <el-badge :value="caseTotal" :max="99" class="count-badge" type="danger" />
          <el-tooltip v-if="userStore.isAdmin" content="批量导入" placement="right">
            <el-upload :show-file-list="false" :before-upload="handleImport" accept=".xlsx,.xls,.csv"
              style="display:inline-block">
              <button class="add-btn"><el-icon><Upload /></el-icon></button>
            </el-upload>
          </el-tooltip>
          <el-tooltip content="新建病例" placement="right">
            <button class="add-btn" @click="createDialogVisible = true"><el-icon><Plus /></el-icon></button>
          </el-tooltip>
        </div>
      </div>

      <div class="panel-search">
        <el-input v-model="searchKeyword" placeholder="搜索病例号 / 患者ID"
          prefix-icon="Search" clearable size="small" @input="onSearch" />
      </div>

      <div class="panel-filters">
        <button v-for="f in STATUS_FILTERS" :key="f.value"
          :class="['filter-btn', activeFilter === f.value && 'active-' + f.color]"
          @click="setFilter(f.value)">
          {{ f.label }}
        </button>
      </div>

      <div v-if="batchGenerating" class="batch-progress-bar">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>批量生成中 {{ batchProgress.current }}/{{ batchProgress.total }}</span>
        <el-progress :percentage="Math.round(batchProgress.current / batchProgress.total * 100)"
          :stroke-width="6" style="flex:1;margin:0 8px" />
        <el-button size="small" type="danger" text @click="batchCancel = true">取消</el-button>
      </div>
      <div v-else-if="noneCount > 0 && (activeFilter === 'NONE' || !activeFilter)" class="batch-bar">
        <el-button size="small" type="primary" @click="handleBatchGenerate">
          <el-icon><VideoPlay /></el-icon> 批量生成AI报告（{{ noneCount }}例）
        </el-button>
      </div>

      <div class="case-list" v-loading="listLoading" element-loading-background="rgba(255,255,255,0.8)">
        <div v-for="c in caseList" :key="c.caseId"
          :class="['case-card', selectedCaseId === c.caseId && 'selected']"
          @click="selectCase(c)">
          <div class="card-top">
            <span class="exam-no">{{ c.examNo }}</span>
            <span :class="['status-badge', 'badge-' + statusColor(c.reportStatus)]">
              {{ statusLabel(c.reportStatus) }}
            </span>
          </div>
          <div class="card-info">
            {{ genderLabel(c.gender) }} · {{ c.age }}岁 · {{ c.department || '—' }}
          </div>
          <div class="card-meta">
            <template v-if="!c.reportStatus || c.reportStatus === 'NONE'">
              <span class="meta-tag meta-tag-body">{{ c.bodyPart || '胸部' }}</span>
            </template>
            <template v-else-if="c.reportStatus === 'AI_DRAFT'">
              <span class="meta-tag meta-tag-ai">
                AI {{ c.modelConfidence ? Math.round(c.modelConfidence * 100) + '%' : '草稿' }}
              </span>
            </template>
            <template v-else-if="c.reportStatus === 'EDITING'">
              <span class="meta-tag meta-tag-edit">
                {{ c.lastEditTime ? '编辑于 ' + formatTime(c.lastEditTime) : '编辑中' }}
              </span>
            </template>
            <template v-else-if="c.reportStatus === 'SIGNED'">
              <span v-if="c.qualityGrade" :class="['meta-grade', 'grade-' + c.qualityGrade]">{{ c.qualityGrade }}</span>
              <span class="meta-text">{{ c.signTime ? formatTime(c.signTime) : '已签发' }}</span>
            </template>
          </div>
          <div class="card-time">{{ formatTime(c.examTime) }}</div>
        </div>
        <el-empty v-if="!listLoading && caseList.length === 0"
          description="暂无病例" :image-size="50"
          style="padding:30px 0;color:#999" />
        <div v-if="hasMore" class="load-more" @click="loadMore">加载更多</div>
      </div>
    </aside>

    <!-- ══ 右侧工作区 ══ -->
    <div class="workspace" v-if="selectedCaseId">
      <!-- 工作区顶部信息栏 -->
      <div class="ws-header">
        <div class="ws-title-block">
          <span class="ws-exam-no">{{ caseInfo.examNo }}</span>
          <span :class="['ws-status-tag', 'tag-' + statusColor(caseInfo.reportStatus)]">
            {{ statusLabel(caseInfo.reportStatus) }}
          </span>
          <span class="ws-patient-info">
            {{ genderLabel(caseInfo.gender) }} · {{ caseInfo.age }}岁 ·
            {{ caseInfo.department }} · {{ formatDate(caseInfo.examTime) }}
          </span>
          <el-tag v-if="pendingCaseAlerts" type="danger" size="small"
            style="cursor:pointer;margin-left:4px" @click="reportTab = 'eval'">
            <el-icon><Warning /></el-icon> {{ pendingCaseAlerts }} 危急值
          </el-tag>
        </div>
        <div class="ws-actions">
          <el-button size="small" plain @click="handleRegenerate" :loading="generating"
            :disabled="!currentReport">
            <el-icon><Refresh /></el-icon> 重新生成AI报告
          </el-button>
          <el-button size="small" plain @click="handleMarkTypical">
            <el-icon><Star /></el-icon>
            {{ caseInfo.isTypical ? '取消典型标记' : '标记典型病例' }}
          </el-button>
          <el-button size="small" plain @click="handlePrint">
            <el-icon><Printer /></el-icon> 打印预览
          </el-button>
          <el-button size="small" plain type="danger" v-if="userStore.isAdmin" @click="handleDeleteCase">
            <el-icon><Delete /></el-icon> 删除病例
          </el-button>
        </div>
      </div>

      <!-- 主内容区 -->
      <div class="ws-scroll">
      <div class="ws-body">
        <!-- 左：DICOM 影像查看器 -->
        <div class="viewer-panel">
          <div class="viewer-toolbar">
            <span class="viewer-filename">{{ currentImage ? currentImage.fileName : '—' }}</span>
            <div class="viewer-tools">
             <button class="tool-btn" title="放大" @click="zoom(0.2)"><el-icon><ZoomIn /></el-icon></button>
             <button class="tool-btn" title="缩小" @click="zoom(-0.2)"><el-icon><ZoomOut /></el-icon></button>
             <button class="tool-btn" title="顺时针旋转" @click="rotate(90)"><el-icon><RefreshRight /></el-icon></button>
             <button class="tool-btn" title="逆时针旋转" @click="rotate(-90)"><el-icon><RefreshLeft /></el-icon></button>
             <button class="tool-btn" title="重置" @click="resetViewer"><el-icon><FullScreen /></el-icon></button>
             <span class="tool-sep-v"></span>
              <button :class="['tool-btn', annoTool === 'select' && 'tool-active']" title="选择标注" @click="activateSelectTool"><el-icon><Pointer /></el-icon></button>
              <button :class="['tool-btn', annoTool === 'rect' && 'tool-active']" title="矩形标注病灶" @click="activateRectTool"><el-icon><Crop /></el-icon></button>
              <span class="tool-sep-v"></span>
              <button :class="['tool-btn', 'layer-toggle', showAiAnnos && 'tool-active']" title="AI标注层" @click="toggleAiLayer">AI</button>
              <button :class="['tool-btn', 'layer-toggle', showDoctorAnnos && 'tool-active']" title="医生标注层" @click="toggleDoctorLayer">医</button>
              <button class="tool-btn" :class="selectedAnnoId && 'tool-danger'" title="删除选中标注" :disabled="!selectedAnnoId" @click="deleteSelectedAnno"><el-icon><Delete /></el-icon></button>
            </div>
          </div>
          <div class="viewer-canvas" ref="viewerRef">
            <div v-if="currentImage" class="image-wrapper"
              :style="{ transform: `scale(${viewerScale}) rotate(${viewerRotate}deg)` }">
              <img :src="currentImage.fullUrl" class="dicom-img" ref="diagImgRef" alt="X光影像"
                @load="onImgLoad" @error="onImgError" draggable="false" />
              <!-- 标注画布覆盖层 -->
              <canvas ref="annoCanvas" class="anno-overlay"
                :class="{ 'anno-draw-mode': annoTool === 'rect' }"
                @mousedown="onAnnoMouseDown" @mousemove="onAnnoMouseMove"
                @mouseup="onAnnoMouseUp" @mouseleave="onAnnoMouseLeave"
                @click="onAnnoClick" />
              <div v-if="currentReport && currentReport.modelConfidence"
                class="ai-marker">
                AI · {{ Math.round(currentReport.modelConfidence * 100) }}%
              </div>
            </div>
            <div v-else class="viewer-empty">
              <el-icon :size="48" style="color:rgba(255,255,255,0.2)"><Picture /></el-icon>
              <p>请选择影像或上传影像</p>
            </div>
          </div>
          <!-- 影像缩略图条 -->
          <div class="thumb-strip" v-if="images.length > 0">
            <div v-for="img in images" :key="img.imageId"
              :class="['thumb-item', currentImage?.imageId === img.imageId && 'thumb-active']"
              @click="currentImage = img">
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
                拖拽或点击上传影像
              </div>
              <div style="font-size:11px;color:rgba(255,255,255,0.3);margin-top:4px">
                支持 JPG / PNG / DICOM，≤50MB
              </div>
            </el-upload>
          </div>

          <!-- 病灶标注列表 -->
          <div class="anno-list-panel" v-if="visibleAnnotations.length > 0">
            <div class="anno-list-header">
              <el-icon style="color:#faad14;font-size:13px"><Flag /></el-icon>
              <span>病灶标注</span>
              <span class="anno-count">{{ annotations.length }} 处</span>
              <span class="anno-hint">虚线=AI · 实线=医生</span>
            </div>
            <div class="anno-list-body">
              <div v-for="anno in visibleAnnotations" :key="anno.annotationId"
                :class="['anno-list-item', selectedAnnoId === anno.annotationId && 'anno-item-selected']"
                @click="selectedAnnoId = anno.annotationId; redrawAnnotations()">
                <span class="anno-color-dot" :style="{ background: anno.color }"></span>
                <span :class="['anno-src-tag', anno.source === 'AI' ? 'tag-ai' : 'tag-dr']">
                  {{ anno.source === 'AI' ? 'AI' : '医' }}
                </span>
                <span class="anno-lbl">{{ anno.label || '—' }}</span>
                <span v-if="anno.confidence != null" class="anno-conf">{{ Math.round(anno.confidence * 100) }}%</span>
                <button v-if="anno.source === 'DOCTOR'" class="anno-del-btn"
                  @click.stop="handleDeleteAnno(anno.annotationId)">×</button>
              </div>
            </div>
          </div>
        </div>

        <!-- 全局标注名输入弹框（fixed定位，跟随鼠标） -->
        <teleport to="body">
          <div v-if="showLabelInput" class="anno-label-popup"
            :style="{ left: labelPopupPos.x + 'px', top: labelPopupPos.y + 'px' }">
            <div class="anno-popup-title">输入病灶描述</div>
            <input ref="labelInputRef" v-model="pendingAnnoLabel" class="anno-popup-input"
              placeholder="如：右下肺实变、胸腔积液…"
              @keyup.enter="confirmAnnoLabel" @keyup.esc="cancelAnnoLabel" />
            <div class="anno-popup-btns">
              <button class="anno-popup-ok" @click="confirmAnnoLabel">确定</button>
              <button class="anno-popup-cancel" @click="cancelAnnoLabel">取消</button>
            </div>
          </div>
        </teleport>

        <!-- 右：报告面板 -->
        <div class="report-panel">

          <!-- ═══ 已签发：不显示标签页，直接展示报告内容+评测结果 ═══ -->
          <div v-if="currentReport && currentReport.reportStatus === 'SIGNED'" class="signed-report-view">
            <div class="signed-banner">
              <div class="signed-banner-left">
                <div class="signed-icon-circle"><el-icon :size="18"><Check /></el-icon></div>
                <div>
                  <div class="signed-title">报告已签发</div>
                  <div class="signed-meta">
                    签发医生：{{ currentReport.doctorName || '—' }} · {{ formatDate(currentReport.signTime) }}
                  </div>
                </div>
              </div>
              <div style="display:flex;align-items:center;gap:8px;margin-left:auto">
                <div v-if="evalResult" class="signed-grade-badge" :style="{ background: gradeColor(evalResult.qualityGrade) }">
                  {{ evalResult.qualityGrade || '?' }}
                </div>
                <el-button v-if="evalResult && (!evalResult.qualityGrade || evalResult.qualityGrade === 'C' || evalResult.qualityGrade === 'D')"
                  size="small" type="warning" plain @click="handleRevertToEdit" :loading="reverting">
                  <el-icon><Edit /></el-icon> 返回修改
                </el-button>
              </div>
            </div>

            <div class="signed-content">
              <div class="signed-section">
                <div class="signed-section-label"><el-icon><Document /></el-icon> 影像所见</div>
                <div class="signed-text">{{ currentReport.finalFindings || '—' }}</div>
              </div>
              <div class="signed-section">
                <div class="signed-section-label"><el-icon><Document /></el-icon> 影像印象</div>
                <div class="signed-text">{{ currentReport.finalImpression || '—' }}</div>
              </div>

              <div v-if="currentReport.aiFindings && currentReport.aiFindings !== currentReport.finalFindings"
                class="signed-ai-compare">
                <div class="compare-header" @click="showAiCompare = !showAiCompare">
                  <el-icon><Cpu /></el-icon>
                  <span>AI原始草稿对比</span>
                  <span class="compare-diff-hint">医生已修改</span>
                  <el-icon style="margin-left:auto"><ArrowDown v-if="!showAiCompare" /><ArrowUp v-else /></el-icon>
                </div>
                <div v-if="showAiCompare" class="compare-body">
                  <div class="compare-field">
                    <div class="compare-label">AI影像所见：</div>
                    <div class="compare-text">{{ currentReport.aiFindings }}</div>
                  </div>
                  <div class="compare-field">
                    <div class="compare-label">AI影像印象：</div>
                    <div class="compare-text">{{ currentReport.aiImpression }}</div>
                  </div>
                </div>
              </div>
            </div>

            <div class="signed-eval-section" v-loading="evalLoading">
              <div class="signed-eval-header">
                <el-icon style="color:#722ed1"><DataAnalysis /></el-icon>
                <span>CheXbert 质量评测</span>
              </div>
              <template v-if="evalResult">
                <div class="signed-eval-metrics">
                  <div class="metric-card" v-for="m in [
                    { label: 'F1 Score', value: pct(evalResult.f1Score), color: gradeColor(evalResult.qualityGrade) },
                    { label: '精确率', value: pct(evalResult.precisionScore), color: '#1890ff' },
                    { label: '召回率', value: pct(evalResult.recallScore), color: '#13c2c2' },
                    { label: 'BLEU-4', value: pct(evalResult.bleu4Score), color: '#722ed1' },
                    { label: 'ROUGE-L', value: pct(evalResult.rougeLScore), color: '#eb2f96' }
                  ]" :key="m.label">
                    <div class="metric-value" :style="{ color: m.color }">{{ m.value }}</div>
                    <div class="metric-label">{{ m.label }}</div>
                  </div>
                </div>

                <!-- 14类标签概率可视化 -->
                <div v-if="parsedLabelProbs.length > 0" class="ai-label-chart" style="margin:10px 0">
                  <div class="label-chart-title">CheXpert 14类病理标签概率</div>
                  <div class="label-bars">
                    <div v-for="p in parsedLabelProbs" :key="p.label" class="label-bar-row">
                      <span class="label-bar-name" :title="p.label">{{ p.labelCn }}</span>
                      <div class="label-bar-track">
                        <div class="label-bar-fill" :style="{ width: Math.round(p.prob * 100) + '%', background: labelBarColor(p.prob) }"></div>
                      </div>
                      <span class="label-bar-pct" :style="{ color: labelBarColor(p.prob) }">{{ Math.round(p.prob * 100) }}%</span>
                    </div>
                  </div>
                </div>

                <div class="signed-eval-advice" :class="'advice-' + (evalResult.qualityGrade || 'D')">
                  {{ gradeAdvice(evalResult.qualityGrade) }}
                </div>

                <div v-if="evalResult.missingLabels && evalResult.missingLabels.length" class="signed-label-row">
                  <span class="label-row-title danger">AI漏诊风险：</span>
                  <el-tag v-for="l in evalResult.missingLabels" :key="l" type="danger" size="small" style="margin:2px">
                    {{ LABEL_CN[l] || l }}
                  </el-tag>
                </div>
                <div v-if="evalResult.extraLabels && evalResult.extraLabels.length" class="signed-label-row">
                  <span class="label-row-title warning">过度诊断：</span>
                  <el-tag v-for="l in evalResult.extraLabels" :key="l" type="warning" size="small" style="margin:2px">
                    {{ LABEL_CN[l] || l }}
                  </el-tag>
                </div>
              </template>
              <div v-else style="text-align:center;color:#bbb;font-size:12px;padding:16px 0">暂无评测结果</div>

              <template v-if="caseAlerts.length">
                <el-divider style="margin:10px 0" />
                <div style="font-size:12px;font-weight:600;color:#f56c6c;margin-bottom:6px">
                  <el-icon><Warning /></el-icon> 危急值预警
                </div>
                <div v-for="a in caseAlerts" :key="a.alertId" class="alert-item-row"
                  :class="a.alertStatus === 'PENDING' ? 'alert-pending' : 'alert-resolved'">
                  <el-icon :style="{ color: a.alertStatus === 'PENDING' ? '#f56c6c' : '#67c23a' }">
                    <WarningFilled v-if="a.alertStatus === 'PENDING'" /><CircleCheckFilled v-else />
                  </el-icon>
                  <span class="alert-label">{{ a.labelType }}</span>
                  <span class="alert-prob">{{ Math.round(a.labelProb * 100) }}%</span>
                  <span class="alert-note">{{ a.alertStatus === 'PENDING' ? '待处理' : '已处理' }}</span>
                  <el-button v-if="a.alertStatus === 'PENDING'" size="small" type="danger" plain
                    style="margin-left:auto;font-size:11px"
                    @click="handleRespondAlert(a.alertId)">
                    标记已处理
                  </el-button>
                </div>
              </template>
            </div>
          </div>

          <!-- ═══ 非签发状态：标签页模式 ═══ -->
          <el-tabs v-else v-model="reportTab" class="report-tabs">
            <el-tab-pane label="报告编辑" name="edit">
              <div v-if="!currentReport" class="no-report">
                <el-empty description="尚未生成报告">
                  <el-button type="primary" :loading="generating"
                    :disabled="!currentImage" @click="handleGenerate">
                    <el-icon><MagicStick /></el-icon> 生成AI报告
                  </el-button>
                </el-empty>
              </div>
              
              <!-- AI草稿/编辑中：统一可编辑视图，无需额外步骤 -->
              <div v-else class="report-form">
                <div class="status-notice"
                  :class="currentReport.reportStatus === 'AI_DRAFT' ? 'status-ai-draft' : 'status-editing'">
                  <el-icon style="margin-right:4px">
                    <Cpu v-if="currentReport.reportStatus === 'AI_DRAFT'" />
                    <Edit v-else />
                  </el-icon>
                  <span :style="{ color: currentReport.reportStatus === 'AI_DRAFT' ? '#1890ff' : '#fa8c16', fontWeight: 600 }">
                    {{ currentReport.reportStatus === 'AI_DRAFT' ? 'AI草稿' : '编辑中' }}
                  </span>
                  <span style="color:#999;margin-left:8px;font-size:11px">
                    {{ currentReport.reportStatus === 'AI_DRAFT' ? '可直接修改内容后签发' : '医生编辑中' }}
                  </span>
                  <span style="margin-left:auto;font-size:11px;color:#8c8c8c">
                    <el-icon style="vertical-align:-2px"><User /></el-icon>
                    {{ userStore.userInfo?.realName || '当前医生' }} 负责
                  </span>
                </div>

                <div class="field-block">
                  <div class="field-label">
                    影像所见
                    <span class="ai-label"><el-icon><Cpu /></el-icon> AI生成</span>
                  </div>
                  <el-input v-model="draftFindings" type="textarea" :rows="6"
                    placeholder="影像所见描述..." resize="none" />
                </div>
                <div class="field-block" style="margin-top:12px">
                  <div class="field-label">
                    影像印象
                    <span class="ai-label"><el-icon><Cpu /></el-icon> AI生成</span>
                  </div>
                  <el-input v-model="draftImpression" type="textarea" :rows="4"
                    placeholder="诊断印象..." resize="none" />
                </div>

                <div class="edit-toolbar" style="margin-top:10px;display:flex;align-items:center;gap:8px;flex-wrap:wrap">
                  <el-button size="small" type="primary" plain :loading="polishing" @click="handlePolish">
                    <el-icon><MagicStick /></el-icon> AI 润色
                  </el-button>
                  <el-button size="small" :loading="termLoading" @click="handleTermNormalize">
                    <el-icon><Edit /></el-icon> 术语纠正
                  </el-button>
                  <span v-if="termLastCount > 0" style="font-size:11px;color:#52c41a">
                    已替换 {{ termLastCount }} 处术语
                  </span>
                </div>

                <!-- AI审核建议（有评测结果且质量C/D时在编辑区展示） -->
                <template v-if="evalResult && (!evalResult.qualityGrade || evalResult.qualityGrade === 'C' || evalResult.qualityGrade === 'D')">
                  <el-divider style="margin:10px 0" />
                  <el-button size="small" type="warning" plain style="width:100%"
                    :loading="aiAdviceLoading" @click="handleGetAiAdvice">
                    <el-icon><MagicStick /></el-icon>
                    {{ aiAdvice ? '重新获取AI审核建议' : '获取AI审核建议' }}
                  </el-button>

                  <div v-if="aiAdvice" class="ai-advice-panel" style="margin-top:8px">
                    <div class="ai-advice-header">
                      <el-icon style="color:#722ed1"><Cpu /></el-icon>
                      <span>AI审核建议</span>
                      <span v-if="aiAdvice.priority === 'high'" class="advice-priority-high">⚠ 高优先级</span>
                      <span v-else-if="aiAdvice.priority === 'medium'" class="advice-priority-mid">注意</span>
                    </div>
                    <div class="ai-advice-assessment">{{ aiAdvice.overall_assessment }}</div>
                    <div v-if="aiAdvice.key_issues?.length" class="ai-advice-block">
                      <div class="ai-advice-label">主要问题</div>
                      <ul class="ai-advice-list">
                        <li v-for="(issue, i) in aiAdvice.key_issues" :key="i">{{ issue }}</li>
                      </ul>
                    </div>
                    <div v-if="aiAdvice.check_points?.length" class="ai-advice-block">
                      <div class="ai-advice-label">建议核查</div>
                      <ul class="ai-advice-list">
                        <li v-for="(pt, i) in aiAdvice.check_points" :key="i">{{ pt }}</li>
                      </ul>
                    </div>
                    <div v-if="aiAdvice.suggested_findings" class="ai-advice-block">
                      <div class="ai-advice-label">参考影像所见</div>
                      <div class="ai-advice-text">{{ aiAdvice.suggested_findings }}</div>
                      <el-button size="small" link type="primary"
                        @click="applyAdviceFindings">应用此内容</el-button>
                    </div>
                    <div v-if="aiAdvice.suggested_impression" class="ai-advice-block">
                      <div class="ai-advice-label">参考影像印象</div>
                      <div class="ai-advice-text">{{ aiAdvice.suggested_impression }}</div>
                      <el-button size="small" link type="primary"
                        @click="applyAdviceImpression">应用此内容</el-button>
                    </div>
                  </div>
                </template>

                <div v-if="currentReport.modelConfidence" class="confidence-bar">
                  <span class="conf-label">AI生成置信度：</span>
                  <el-progress
                    :percentage="Math.round(currentReport.modelConfidence * 100)"
                    :color="confColor(currentReport.modelConfidence)"
                    :stroke-width="8" style="flex:1" />
                  <span class="conf-value">{{ Math.round(currentReport.modelConfidence * 100) }}%</span>
                </div>
              </div>
            </el-tab-pane>

            <el-tab-pane label="历史版本" name="history">
              <div class="history-list" v-loading="historyLoading">
                <div v-for="h in editHistory" :key="h.historyId" class="history-item">
                  <div class="history-header">
                    <span class="history-version">v{{ h.version || '1' }}</span>
                    <span class="history-editor">{{ h.editorName || '—' }}</span>
                    <span class="history-time">{{ formatDate(h.editTime) }}</span>
                    <el-button size="small" link type="primary"
                      v-if="currentReport && currentReport.reportStatus !== 'SIGNED'"
                      @click="handleRestoreHistory(h)"
                      style="margin-left:auto;font-size:11px">恢复此版本</el-button>
                  </div>
                  <div class="history-content">
                    <div class="history-field">
                      <span class="field-label">影像所见：</span>
                      <span class="field-text">{{ h.findings || '—' }}</span>
                    </div>
                    <div class="history-field">
                      <span class="field-label">影像印象：</span>
                      <span class="field-text">{{ h.impression || '—' }}</span>
                    </div>
                  </div>
                </div>
                <el-empty v-if="!historyLoading && editHistory.length === 0"
                  description="暂无历史版本" :image-size="40" />
              </div>
            </el-tab-pane>

            <el-tab-pane name="eval">
              <template #label>
                AI评测
                <el-badge v-if="pendingCaseAlerts" :value="pendingCaseAlerts" type="danger" class="tab-badge" />
              </template>
              <div v-loading="evalLoading" style="padding:8px">
                <template v-if="evalResult">
                  <div class="eval-summary">
                    <div class="eval-grade" :style="{ color: gradeColor(evalResult.qualityGrade) }">{{ evalResult.qualityGrade || '—' }}</div>
                    <el-descriptions :column="2" border size="small" style="flex:1">
                      <el-descriptions-item label="F1">{{ pct(evalResult.f1Score) }}</el-descriptions-item>
                      <el-descriptions-item label="精确率">{{ pct(evalResult.precisionScore) }}</el-descriptions-item>
                      <el-descriptions-item label="召回率">{{ pct(evalResult.recallScore) }}</el-descriptions-item>
                      <el-descriptions-item label="BLEU-4">{{ pct(evalResult.bleu4Score) }}</el-descriptions-item>
                    </el-descriptions>
                  </div>
                  <div v-if="evalResult.missingLabels && evalResult.missingLabels.length" style="margin-top:8px">
                    <div style="font-size:11px;color:#999;margin-bottom:4px">AI漏诊标签</div>
                    <el-tag v-for="l in evalResult.missingLabels" :key="l" type="danger" size="small" style="margin:2px">{{ l }}</el-tag>
                  </div>
                  <div v-if="evalResult.extraLabels && evalResult.extraLabels.length" style="margin-top:6px">
                    <div style="font-size:11px;color:#999;margin-bottom:4px">AI误诊标签</div>
                    <el-tag v-for="l in evalResult.extraLabels" :key="l" type="warning" size="small" style="margin:2px">{{ l }}</el-tag>
                  </div>
                </template>
                <el-empty v-else description="暂无评测结果" :image-size="50" />
                <template v-if="caseAlerts.length">
                  <el-divider style="margin:10px 0">危急值预警</el-divider>
                  <div v-for="a in caseAlerts" :key="a.alertId" class="alert-item-row"
                    :class="a.alertStatus === 'PENDING' ? 'alert-pending' : 'alert-resolved'">
                    <el-icon :style="{ color: a.alertStatus === 'PENDING' ? '#f56c6c' : '#67c23a' }">
                      <WarningFilled v-if="a.alertStatus === 'PENDING'" /><CircleCheckFilled v-else />
                    </el-icon>
                    <span class="alert-label">{{ a.labelType }}</span>
                    <span class="alert-prob">{{ Math.round(a.labelProb * 100) }}%</span>
                    <span class="alert-note">{{ a.alertStatus === 'PENDING' ? '待处理' : '已处理' }}</span>
                    <el-button v-if="a.alertStatus === 'PENDING'" size="small" type="danger" plain
                      style="margin-left:auto;font-size:11px"
                      @click="handleRespondAlert(a.alertId)">
                      标记已处理
                    </el-button>
                  </div>
                </template>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>

      <!-- 相似病例检索 -->
      <div class="similar-section" v-if="similarCases.length > 0">
        <div class="section-title">
          <el-icon style="color:#1890ff"><Search /></el-icon>
          相似病例检索 <span class="section-sub">Top-{{ similarCases.length }} 相似匹配</span>
        </div>
        <div class="similar-cards">
          <div v-for="(sc, i) in similarCases" :key="sc.caseId" class="similar-card"
            style="cursor:pointer" @click="selectCaseById(sc.caseId)">
            <div class="similar-img-placeholder">
              <img v-if="sc.thumbnailUrl" :src="sc.thumbnailUrl"
                style="width:100%;height:100%;object-fit:cover;border-radius:4px" />
              <el-icon v-else :size="24" style="color:rgba(255,255,255,0.2)"><Picture /></el-icon>
            </div>
            <div class="similar-info">
              <span :class="['sim-score', i === 0 ? 'score-high' : i === 1 ? 'score-mid' : 'score-low']">
                相似度 {{ Math.round((sc.similarityScore || 0) * 100) }}%
              </span>
              <div class="sim-exam">{{ sc.examNo || ('病例 ' + sc.caseId) }}</div>
              <div class="sim-findings">{{ sc.findings || sc.impression || '—' }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 处理进度 -->
      <div class="progress-section">
        <div class="section-title">处理进度</div>
        <div class="workflow-steps">
          <div v-for="(step, i) in workflowSteps" :key="i"
            :class="['step-item', step.status]">
            <div class="step-circle">
              <el-icon v-if="step.status === 'done'"><Check /></el-icon>
              <span v-else>{{ i + 1 }}</span>
            </div>
            <div class="step-info">
              <div class="step-name">{{ step.name }}</div>
              <div class="step-time">{{ step.time || step.hint }}</div>
            </div>
            <div v-if="i < workflowSteps.length - 1" class="step-line"></div>
          </div>
        </div>
      </div>

      <!-- AI 智能分析面板 -->
      <div class="ai-analysis-section" v-if="currentReport && (evalResult || evalPolling)">
        <div class="section-title">
          <el-icon style="color:#722ed1"><Cpu /></el-icon>
          AI 智能分析
          <span v-if="evalPolling && !evalResult" style="font-size:11px;color:#999;margin-left:8px">
            <el-icon class="is-loading"><Loading /></el-icon> 分析中...
          </span>
        </div>

        <template v-if="evalResult">
          <!-- 风险评估卡 -->
          <div class="ai-risk-card" :style="{ background: riskLevel.bg, borderColor: riskLevel.color }">
            <div class="risk-header">
              <div class="risk-badge" :style="{ background: riskLevel.color }">{{ riskLevel.text }}</div>
              <div class="risk-grade">
                <span class="risk-grade-letter" :style="{ color: gradeColor(evalResult.qualityGrade) }">{{ evalResult.qualityGrade }}</span>
                <span class="risk-grade-label">质量等级</span>
              </div>
              <div class="risk-metrics">
                <span>F1 <b>{{ pct(evalResult.f1Score) }}</b></span>
                <span>精确率 <b>{{ pct(evalResult.precisionScore) }}</b></span>
                <span>召回率 <b>{{ pct(evalResult.recallScore) }}</b></span>
                <span>BLEU-4 <b>{{ pct(evalResult.bleu4Score) }}</b></span>
                <span>ROUGE-L <b>{{ pct(evalResult.rougeLScore) }}</b></span>
              </div>
            </div>
            <div v-if="topFindings.length > 0" class="risk-findings">
              <span class="risk-findings-label">AI 检出：</span>
              <el-tag v-for="f in topFindings.slice(0, 4)" :key="f.label"
                :color="labelBarColor(f.prob)" effect="dark" size="small"
                style="margin:2px;border:none">
                {{ f.labelCn }} {{ Math.round(f.prob * 100) }}%
              </el-tag>
            </div>
          </div>

          <!-- 14类标签概率分布 -->
          <div class="ai-label-chart" v-if="parsedLabelProbs.length > 0">
            <div class="label-chart-title">CheXpert 14类病理标签概率</div>
            <div class="label-bars">
              <div v-for="p in parsedLabelProbs" :key="p.label" class="label-bar-row">
                <span class="label-bar-name" :title="p.label">{{ p.labelCn }}</span>
                <div class="label-bar-track">
                  <div class="label-bar-fill" :style="{ width: Math.round(p.prob * 100) + '%', background: labelBarColor(p.prob) }"></div>
                </div>
                <span class="label-bar-pct" :style="{ color: labelBarColor(p.prob) }">{{ Math.round(p.prob * 100) }}%</span>
              </div>
            </div>
          </div>

          <!-- 漏诊/误诊标签 -->
          <div v-if="(evalResult.missingLabels && evalResult.missingLabels.length) || (evalResult.extraLabels && evalResult.extraLabels.length)"
            class="ai-label-alerts">
            <div v-if="evalResult.missingLabels && evalResult.missingLabels.length" class="label-alert-row">
              <span class="label-alert-icon" style="color:#f5222d">⚠</span>
              <span class="label-alert-title">漏诊风险：</span>
              <el-tag v-for="l in evalResult.missingLabels" :key="l" type="danger" size="small" style="margin:2px">
                {{ LABEL_CN[l] || l }}
              </el-tag>
            </div>
            <div v-if="evalResult.extraLabels && evalResult.extraLabels.length" class="label-alert-row">
              <span class="label-alert-icon" style="color:#fa8c16">⚡</span>
              <span class="label-alert-title">过度诊断：</span>
              <el-tag v-for="l in evalResult.extraLabels" :key="l" type="warning" size="small" style="margin:2px">
                {{ LABEL_CN[l] || l }}
              </el-tag>
            </div>
          </div>
        </template>
      </div>

      </div><!-- /ws-scroll -->

      <!-- 底部操作栏 -->
      <div class="ws-footer">
        <div class="footer-nav">
          <el-button size="small" plain :disabled="!prevCaseId" @click="navigateCase('prev')">
            ← 上一份
          </el-button>
          <el-button size="small" plain :disabled="!nextCaseId" @click="navigateCase('next')">
            下一份 →
          </el-button>
        </div>

        <!-- 内联决策摘要：置信度 + 评测等级 + 危急值 -->
        <div v-if="currentReport && currentReport.reportStatus !== 'SIGNED'" class="footer-eval-bar">
          <span v-if="currentReport.modelConfidence" class="feval-chip"
            :class="currentReport.modelConfidence >= 0.85 ? 'feval-high' : 'feval-mid'">
            AI {{ Math.round(currentReport.modelConfidence * 100) }}%
          </span>
          <span v-if="evalResult" class="feval-chip feval-grade"
            :style="{ background: gradeColor(evalResult.qualityGrade), color: '#fff' }">
            {{ evalResult.qualityGrade }}级
          </span>
          <span v-else-if="evalPolling" class="feval-chip feval-pending">
            <el-icon class="is-loading" style="vertical-align:-2px"><Loading /></el-icon> 评测中
          </span>
          <span v-if="pendingCaseAlerts" class="feval-chip feval-alert">
            <el-icon style="vertical-align:-2px"><Warning /></el-icon> {{ pendingCaseAlerts }} 危急值
          </span>
          <span v-if="evalResult && !evalResult.missingLabels?.length && !pendingCaseAlerts"
            class="feval-chip feval-safe">✓ 无漏诊风险</span>
        </div>

        <div class="footer-actions" v-if="currentReport">
          <el-button size="default" plain @click="handleSaveDraft"
            :loading="saving" :disabled="currentReport.reportStatus === 'SIGNED'">
            <el-icon><FolderOpened /></el-icon> 保存草稿
          </el-button>
          <el-button size="default" type="success" @click="handleSign"
            :loading="signing"
            v-if="currentReport.reportStatus !== 'SIGNED'">
            <el-icon><Check /></el-icon> 签发报告
            <el-tag v-if="evalResult" size="small"
              :style="{ background: gradeColor(evalResult.qualityGrade), borderColor: 'transparent', color:'#fff', marginLeft:'6px', padding:'0 5px' }">
              {{ evalResult.qualityGrade }}级
            </el-tag>
          </el-button>
        </div>
        <div class="footer-actions" v-else-if="currentImage">
          <el-button size="default" type="primary" :loading="generating" @click="handleGenerate">
            <el-icon><MagicStick /></el-icon> 生成AI报告
          </el-button>
        </div>
      </div>
    </div>

    <!-- 未选择病例时的占位 -->
    <div class="workspace workspace-empty" v-else>
      <el-icon :size="64" style="color:rgba(0,0,0,0.18)"><Monitor /></el-icon>
      <p style="color:#999;font-size:14px;margin:0">从左侧选择一个病例开始工作</p>
    </div>

    <!-- 新建病例弹框 -->
    <el-dialog v-model="createDialogVisible" title="新建病例" width="480px" align-center @open="resetCreateForm">
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="90px">
        <el-form-item label="检查号" prop="examNo">
          <el-input v-model="createForm.examNo" placeholder="如：CX-2024-0001" />
        </el-form-item>
        <el-form-item label="患者匿名ID" prop="patientAnonId">
          <el-input v-model="createForm.patientAnonId" placeholder="患者匿名标识" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="createForm.gender">
            <el-radio label="M">男</el-radio>
            <el-radio label="F">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="年龄" prop="age">
          <el-input-number v-model="createForm.age" :min="0" :max="150" style="width:100%" />
        </el-form-item>
        <el-form-item label="检查部位" prop="bodyPart">
          <el-input v-model="createForm.bodyPart" placeholder="如：胸部" />
        </el-form-item>
        <el-form-item label="科室">
          <el-input v-model="createForm.department" placeholder="如：影像科" />
        </el-form-item>
        <el-form-item label="检查时间" prop="examTime">
          <el-date-picker v-model="createForm.examTime" type="datetime"
            placeholder="选择检查时间" style="width:100%"
            format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateCase" :loading="creating">创建</el-button>
      </template>
    </el-dialog>

    <!-- 典型病例标记弹框 -->
    <el-dialog v-model="typicalDialogVisible" title="标记典型病例" width="420px" align-center>
      <el-form :model="typicalForm" label-width="80px">
        <el-form-item label="分类标签">
          <el-input v-model="typicalForm.typicalTags" placeholder="如: 肺炎,结节（逗号分隔）" />
        </el-form-item>
        <el-form-item label="备注说明">
          <el-input v-model="typicalForm.typicalRemark" type="textarea" :rows="3" placeholder="典型特征说明..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typicalDialogVisible = false">取消</el-button>
        <el-button type="warning" @click="confirmMarkTypical" :loading="typicalLoading">确认标记</el-button>
      </template>
    </el-dialog>

    <!-- 术语标准化弹窗 -->
    <el-dialog v-model="termDialogVisible" title="AI 术语标准化" width="520px" align-center>
      <div v-if="termDialogList.length === 0" style="text-align:center;color:#999;padding:20px 0">
        未发现需要纠正的术语
      </div>
      <el-table v-else :data="termDialogList" size="small" style="width:100%"
        @selection-change="onTermSelectionChange">
        <el-table-column type="selection" width="40" />
        <el-table-column type="index" label="#" width="40" />
        <el-table-column label="原始术语" min-width="140">
          <template #default="{ row }">
            <span style="color:#f56c6c;text-decoration:line-through">{{ row.originalTerm }}</span>
          </template>
        </el-table-column>
        <el-table-column label="标准术语" min-width="140">
          <template #default="{ row }">
            <span style="color:#52c41a;font-weight:600">{{ row.suggestedTerm }}</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="termDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="termSelectedItems.length === 0"
          @click="confirmTermReplace">
          确认替换（{{ termSelectedItems.length }} 项）
        </el-button>
      </template>
    </el-dialog>

    <!-- AI 润色弹窗 -->
    <el-dialog v-model="polishDialogVisible" title="AI 报告润色" width="640px" align-center>
      <template v-if="polishResult">
        <div style="margin-bottom:12px">
          <el-alert :title="polishResult.changes_summary || 'AI 已完成报告润色'"
            type="success" show-icon :closable="false" />
        </div>
        <div class="polish-compare">
          <div class="polish-col">
            <div class="polish-col-title" style="color:#f56c6c">原始草稿</div>
            <div class="polish-field-label">影像所见</div>
            <div class="polish-text">{{ draftFindings }}</div>
            <div class="polish-field-label" style="margin-top:8px">影像印象</div>
            <div class="polish-text">{{ draftImpression }}</div>
          </div>
          <div class="polish-arrow">→</div>
          <div class="polish-col">
            <div class="polish-col-title" style="color:#52c41a">AI 润色后</div>
            <div class="polish-field-label">影像所见</div>
            <div class="polish-text polish-text-new">{{ polishResult.polished_findings }}</div>
            <div class="polish-field-label" style="margin-top:8px">影像印象</div>
            <div class="polish-text polish-text-new">{{ polishResult.polished_impression }}</div>
          </div>
        </div>
        <div v-if="polishResult.suggestions && polishResult.suggestions.length" style="margin-top:12px">
          <div style="font-size:12px;font-weight:600;color:#722ed1;margin-bottom:6px">AI 建议</div>
          <div v-for="(s, i) in polishResult.suggestions" :key="i"
            style="font-size:12px;color:#666;padding:2px 0">
            {{ i + 1 }}. {{ s }}
          </div>
        </div>
      </template>
      <template #footer>
        <el-button @click="polishDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="applyPolish">
          <el-icon><Check /></el-icon> 采纳润色结果
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listCases, getCaseById, markTypical, createCase, deleteCase, importCases } from '@/api/case'
import { listImages, uploadImage, deleteImage } from '@/api/image'
import { generateReport, regenerateReport, saveDraft, signReport, listReports, getEditHistory, polishReport, revertReport, getAiAdvice } from '@/api/report'
import { searchRetrieval, listRetrievalByCaseId } from '@/api/retrieval'
import { triggerEval, getEvalByReportId } from '@/api/eval'
import { analyzeTerms, getTermsByReportId, acceptCorrection, dismissCorrection } from '@/api/term'
import { getAlertsByCaseId, respondAlert } from '@/api/alert'
import { listAnnotations, createAnnotation, deleteAnnotation, generateAiAnnotations } from '@/api/annotation'

const route = useRoute()
const userStore = useUserStore()

/* ─────────────── 常量 ─────────────── */
const STATUS_FILTERS = [
  { label: '待生成', value: 'NONE', color: 'orange' },
  { label: 'AI草稿', value: 'AI_DRAFT', color: 'blue' },
  { label: '编辑中', value: 'EDITING', color: 'blue' },
  { label: '已签发', value: 'SIGNED', color: 'green' },
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
    // 已签发只看当前医生自己签发的病例（追责隔离），编辑中为共享工作队列
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
    // 同步当前选中病例的状态到工作区顶部
    if (selectedCaseId.value) {
      const updated = caseList.value.find(c => c.caseId === selectedCaseId.value)
      if (updated) caseInfo.value = { ...caseInfo.value, ...updated }
    }
  } finally {
    listLoading.value = false
  }
}

const loadMore = () => { currentPage.value++; fetchCases(true) }

/* ─────────────── 选中病例 ─────────────── */
const selectedCaseId = ref(null)
const caseInfo = ref({})
const images = ref([])
const currentImage = ref(null)
const currentReport = ref(null)
const editHistory = ref([])
const historyLoading = ref(false)
const similarCases = ref([])
const evalResult = ref(null)
const evalLoading = ref(false)
const evalPolling = ref(false)
let pollTimer = null
const showAiCompare = ref(false)
const aiAdvice = ref(null)
const aiAdviceLoading = ref(false)
const reverting = ref(false)
const termCorrections = ref([])
const termLoading = ref(false)
const caseAlerts = ref([])

const selectCaseById = async (caseId) => {
  const found = caseList.value.find(c => String(c.caseId) === String(caseId))
  if (found) {
    await selectCase(found)
  } else {
    try {
      const res = await getCaseById(caseId)
      if (res.data) await selectCase(res.data)
    } catch (_) { ElMessage.warning('相似病例数据加载失败') }
  }
}

const selectCase = async (c) => {
  if (selectedCaseId.value === c.caseId) return
  clearInterval(pollTimer)
  evalPolling.value = false
  currentReport.value = null
  draftFindings.value = ''
  draftImpression.value = ''
  editHistory.value = []
  similarCases.value = []
  evalResult.value = null
  aiAdvice.value = null
  termCorrections.value = []
  annotations.value = []
  selectedAnnoId.value = null
  selectedCaseId.value = c.caseId
  caseInfo.value = c
  reportTab.value = 'edit'
  await Promise.all([loadCaseDetail(), loadImages(), loadCaseAlerts()])
  await loadReport()
}

const loadCaseDetail = async () => {
  try {
    const res = await getCaseById(selectedCaseId.value)
    caseInfo.value = res.data || caseInfo.value
  } catch (_) {}
}

const loadImages = async () => {
  try {
    const res = await listImages(selectedCaseId.value)
    images.value = res.data || []
    currentImage.value = images.value[0] || null
  } catch (_) { images.value = [] }
}

const loadReport = async () => {
  try {
    const res = await listReports({ caseId: selectedCaseId.value, page: 1, pageSize: 1 })
    const records = res.data?.list || res.data?.records || []
    if (records.length > 0) {
      currentReport.value = records[0]
      draftFindings.value = currentReport.value.finalFindings || currentReport.value.aiFindings || ''
      draftImpression.value = currentReport.value.finalImpression || currentReport.value.aiImpression || ''
      await Promise.all([loadHistory(currentReport.value.reportId), loadSimilarCases(), loadEvalResult(currentReport.value.reportId), loadTermCorrections(currentReport.value.reportId)])
    } else {
      currentReport.value = null
      draftFindings.value = ''
      draftImpression.value = ''
      similarCases.value = []
      editHistory.value = []
      evalResult.value = null
      termCorrections.value = []
      // 数据自愈：case_info.report_status 非 NONE 但 report_info 无记录，本地纠正为 NONE
      if (caseInfo.value && caseInfo.value.reportStatus && caseInfo.value.reportStatus !== 'NONE') {
        caseInfo.value = { ...caseInfo.value, reportStatus: 'NONE' }
      }
    }
  } catch (_) { currentReport.value = null }
}

const loadHistory = async (reportId) => {
  historyLoading.value = true
  try {
    const res = await getEditHistory(reportId)
    editHistory.value = res.data || []
  } catch (_) { editHistory.value = [] } finally { historyLoading.value = false }
}

const loadSimilarCases = async () => {
  try {
    const retriRes = await listRetrievalByCaseId(selectedCaseId.value)
    const logs = retriRes.data || []
    const latestLog = logs[logs.length - 1]
    similarCases.value = (latestLog?.similarCases || []).slice(0, 3)
  } catch (_) { similarCases.value = [] }
}

const loadEvalResult = async (reportId) => {
  evalLoading.value = true
  try {
    const res = await getEvalByReportId(reportId)
    const list = res.data || []
    evalResult.value = list.length ? list[0] : null
  } catch (_) { evalResult.value = null }
  finally { evalLoading.value = false }
}

const loadTermCorrections = async (reportId) => {
  termLoading.value = true
  try {
    const res = await getTermsByReportId(reportId)
    termCorrections.value = res.data || []
  } catch (_) { termCorrections.value = [] }
  finally { termLoading.value = false }
}

const loadCaseAlerts = async () => {
  try {
    const res = await getAlertsByCaseId(selectedCaseId.value)
    caseAlerts.value = res.data || []
  } catch (_) { caseAlerts.value = [] }
}

const handleRespondAlert = async (alertId) => {
  try {
    await respondAlert(alertId, { action: 'ACKNOWLEDGED', note: '医生已确认处理' })
    ElMessage.success('危急值已标记为处理')
    await loadCaseAlerts()
  } catch (_) {
    ElMessage.error('操作失败，请重试')
  }
}

/* ─────────────── 术语标准化 ─────────────── */
const termDialogVisible = ref(false)
const termDialogList = ref([])
const termSelectedItems = ref([])
const termLastCount = ref(0)

const onTermSelectionChange = (rows) => { termSelectedItems.value = rows }

const handleTermNormalize = async () => {
  if (!currentReport.value) return
  termLoading.value = true
  try {
    const payload = { findings: draftFindings.value || '', impression: draftImpression.value || '' }
    const res = await analyzeTerms(currentReport.value.reportId, payload)
    const list = res.data || []
    // 筛选出待处理的纠正项（isAccepted === 0 或 null）
    termDialogList.value = list.filter(t => !t.isAccepted || t.isAccepted === 0)
    termCorrections.value = list
    if (termDialogList.value.length === 0) {
      ElMessage.success('未发现需要纠正的术语')
    } else {
      termSelectedItems.value = [...termDialogList.value]
      termDialogVisible.value = true
    }
  } catch (_) {
    // 请求拦截器已展示错误
  } finally { termLoading.value = false }
}

const confirmTermReplace = async () => {
  let count = 0
  for (const item of termSelectedItems.value) {
    const orig = item.originalTerm
    const corr = item.suggestedTerm
    if (orig && corr) {
      const beforeF = draftFindings.value
      const beforeI = draftImpression.value
      draftFindings.value = (draftFindings.value || '').split(orig).join(corr)
      draftImpression.value = (draftImpression.value || '').split(orig).join(corr)
      if (draftFindings.value !== beforeF || draftImpression.value !== beforeI) count++
    }
    try { await acceptCorrection(item.correctionId) } catch (_) {}
  }
  termLastCount.value = count
  termDialogVisible.value = false
  ElMessage.success(`已替换 ${count} 处术语`)
}

const handleRestoreHistory = async (h) => {
  try {
    await ElMessageBox.confirm(
      `将报告内容恢复到「${formatDate(h.editTime)}」的版本？当前未保存的修改将丢失。`,
      '恢复历史版本', { confirmButtonText: '确认恢复', cancelButtonText: '取消', type: 'warning' }
    )
    if (h.findingsAfter != null) draftFindings.value = h.findingsAfter
    if (h.impressionAfter != null) draftImpression.value = h.impressionAfter
    ElMessage.success('已恢复到所选版本，记得保存草稿')
    reportTab.value = 'edit'
  } catch (_) {}
}

const handleDismissCorrection = async (item) => {
  await dismissCorrection(item.correctionId)
  item.isAccepted = -1
}

const handleDeleteCase = async () => {
  try {
    await ElMessageBox.confirm(`确认删除病例「${caseInfo.value.examNo}」？删除后不可恢复`, '删除病例', { type: 'warning' })
    await deleteCase(selectedCaseId.value)
    ElMessage.success('病例已删除')
    selectedCaseId.value = null
    caseInfo.value = {}
    images.value = []
    currentImage.value = null
    currentReport.value = null
    caseAlerts.value = []
    await fetchCases()
  } catch (_) {}
}

const handleImport = async (file) => {
  try {
    const res = await importCases(file)
    ElMessage.success(`导入完成：成功 ${res.data?.successCount ?? 0} 条`)
    await fetchCases()
  } catch (e) {
    ElMessage.error('导入失败：' + (e?.message || '请检查文件格式'))
  }
  return false
}

const handleDeleteImage = async (img) => {
  try {
    await ElMessageBox.confirm('确认删除该影像？', '提示', { type: 'warning' })
  } catch (_) { return }
  try {
    await deleteImage(img.imageId)
    images.value = images.value.filter(i => i.imageId !== img.imageId)
    if (currentImage.value?.imageId === img.imageId) {
      currentImage.value = images.value[0] || null
    }
    ElMessage.success('影像已删除')
  } catch (_) {}
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
      '批量生成AI报告', { confirmButtonText: '开始生成', cancelButtonText: '取消', type: 'info' }
    )
  } catch (_) { return }

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
    currentReport.value = res.data
    draftFindings.value = res.data.finalFindings || res.data.aiFindings || ''
    draftImpression.value = res.data.finalImpression || res.data.aiImpression || ''
    
    ElMessage.success('AI报告生成完成')
    fetchCases()
    evalResult.value = null
    startEvalPoll(res.data.reportId)
    analyzeTerms(res.data.reportId, {
      findings: draftFindings.value,
      impression: draftImpression.value
    }).then(r => { termCorrections.value = r.data || [] }).catch(() => {})
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
    currentReport.value = res.data
    draftFindings.value = res.data.finalFindings || res.data.aiFindings || ''
    draftImpression.value = res.data.finalImpression || res.data.aiImpression || ''
    
    ElMessage.success('报告已重新生成')
    fetchCases()
    evalResult.value = null
    startEvalPoll(res.data.reportId)
    analyzeTerms(res.data.reportId, {
      findings: draftFindings.value,
      impression: draftImpression.value
    }).then(r => { termCorrections.value = r.data || [] }).catch(() => {})
  } catch (_) {
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
    ElMessage.success('草稿已保存')
    fetchCases()
  } catch (_) {
    // 请求拦截器已展示错误
  } finally { saving.value = false }
}

const handleSign = async () => {
  if (!currentReport.value?.reportId) {
    ElMessage.error('当前病例尚未生成有效报告，无法签发')
    return
  }
  try {
    await ElMessageBox.confirm('确认签发该报告？签发后不可撤销', '签发报告', {
      confirmButtonText: '确认签发', cancelButtonText: '取消',
      confirmButtonClass: 'el-button--success', type: 'warning'
    })
  } catch (_) { return }

  signing.value = true
  try {
    const reportId = currentReport.value.reportId
    const aiFindings = currentReport.value.aiFindings || ''
    const aiImpression = currentReport.value.aiImpression || ''

    if (currentReport.value.reportStatus === 'EDITING' || currentReport.value.reportStatus === 'AI_DRAFT') {
      // 签发前自动保存最新编辑内容
      await saveDraft(reportId, {
        finalFindings: draftFindings.value,
        finalImpression: draftImpression.value
      })
    }

    await signReport(reportId)
    ElMessage.success('报告已成功签发')
    await loadReport()
    fetchCases()

    // 仅在医生修改过内容时触发重新评测，否则复用生成时的基线评测
    const finalFindings = currentReport.value?.finalFindings || draftFindings.value
    const contentChanged = finalFindings !== aiFindings
    if (contentChanged) {
      evalResult.value = null
      triggerEval(reportId).then(() => startEvalPoll(reportId)).catch(() => {})
    }
  } catch (err) {
    ElMessage.error('签发失败：' + (err?.message || '请检查网络或重新登录'))
    await loadReport()
  } finally { signing.value = false }
}


const handleRevertToEdit = async () => {
  if (!currentReport.value?.reportId) return
  try {
    await ElMessageBox.confirm(
      '撤回后报告将进入编辑状态，需重新签发。确认撤回？',
      '撤回已签发报告', { confirmButtonText: '确认撤回', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (_) { return }
  reverting.value = true
  // 保存已应用的AI建议内容（可能与原报告不同）
  const advisedFindings = draftFindings.value
  const advisedImpression = draftImpression.value
  try {
    await revertReport(currentReport.value.reportId)
    ElMessage.success('报告已撤回，可重新编辑')
    await loadReport()
    // 若医生已通过AI建议修改了草稿内容，恢复（优先保留建议内容）
    if (advisedFindings && advisedFindings !== currentReport.value.finalFindings) {
      draftFindings.value = advisedFindings
    }
    if (advisedImpression && advisedImpression !== currentReport.value.finalImpression) {
      draftImpression.value = advisedImpression
    }
    reportTab.value = 'edit'
    fetchCases()
  } catch (e) {
    ElMessage.error('撤回失败：' + (e?.message || '请检查网络或重新登录'))
  } finally { reverting.value = false }
}

const handleGetAiAdvice = async () => {
  if (!currentReport.value?.reportId) return
  aiAdviceLoading.value = true
  try {
    const res = await getAiAdvice(currentReport.value.reportId)
    const data = res.data
    // 若返回空对象（AI服务未配置或解析失败），视为错误
    if (!data || !data.overall_assessment) {
      ElMessage.warning('AI建议服务暂不可用，请确认DeepSeek API已配置')
      return
    }
    aiAdvice.value = data
  } catch (_) {
    ElMessage.error('获取AI建议失败，请确认DeepSeek API密钥已配置并网络可达')
  } finally { aiAdviceLoading.value = false }
}

const applyAdviceFindings = () => {
  if (!aiAdvice.value?.suggested_findings) return
  draftFindings.value = aiAdvice.value.suggested_findings
  ElMessage.success('参考内容已应用，请返回修改后签发')
}

const applyAdviceImpression = () => {
  if (!aiAdvice.value?.suggested_impression) return
  draftImpression.value = aiAdvice.value.suggested_impression
  ElMessage.success('参考内容已应用，请返回修改后签发')
}

/* ─────────────── 影像操作 ─────────────── */
const viewerRef = ref(null)
const viewerScale = ref(1)
const viewerRotate = ref(0)
const zoom = (d) => { viewerScale.value = Math.max(0.3, Math.min(4, viewerScale.value + d)) }
const rotate = (d) => { viewerRotate.value += d }
const resetViewer = () => { viewerScale.value = 1; viewerRotate.value = 0 }
const onImgError = (e) => { e.target.src = '' }

/* ─────────────── 病灶标注 ─────────────── */
const diagImgRef = ref(null)      // 影像 <img> 元素
const annoCanvas = ref(null)      // 画布覆盖层
const labelInputRef = ref(null)   // 标注名输入框
const annotations = ref([])       // 当前影像的所有标注
const annoTool = ref('select')    // 'select' | 'rect'
const showAiAnnos = ref(true)
const showDoctorAnnos = ref(true)
const selectedAnnoId = ref(null)
const showLabelInput = ref(false)
const pendingAnnoLabel = ref('')
const labelPopupPos = ref({ x: 0, y: 0 })
let drawState = null  // { x, y, w, h } 正在绘制的临时矩形
let imgNW = 0         // 影像自然像素宽
let imgNH = 0         // 影像自然像素高

const visibleAnnotations = computed(() =>
  annotations.value.filter(a =>
    (a.source === 'AI' && showAiAnnos.value) ||
    (a.source === 'DOCTOR' && showDoctorAnnos.value)
  )
)

const aiAnnotationCount = computed(() => annotations.value.filter(a => a.source === 'AI').length)
const doctorAnnotationCount = computed(() => annotations.value.filter(a => a.source === 'DOCTOR').length)

const activateSelectTool = () => {
  annoTool.value = 'select'
  ElMessage.info('已切换到选择模式：点击标注可选中，再点垃圾桶可删除医生标注')
}

const activateRectTool = () => {
  annoTool.value = 'rect'
  ElMessage.info('已进入矩形标注模式：请在影像上按住左键拖拽框选病灶区域')
}

const toggleAiLayer = () => {
  showAiAnnos.value = !showAiAnnos.value
  ElMessage.info(showAiAnnos.value
    ? `已显示AI标注层（${aiAnnotationCount.value}处）`
    : '已隐藏AI标注层')
}

const toggleDoctorLayer = () => {
  showDoctorAnnos.value = !showDoctorAnnos.value
  ElMessage.info(showDoctorAnnos.value
    ? `已显示医生标注层（${doctorAnnotationCount.value}处）`
    : '已隐藏医生标注层')
}

const loadAnnotations = async (imageId) => {
  try {
    const res = await listAnnotations(imageId)
    annotations.value = res.data || []
    nextTick(redrawAnnotations)
  } catch (_) { annotations.value = [] }
}

const onImgLoad = () => {
  const img = diagImgRef.value
  if (!img) return
  imgNW = img.naturalWidth
  imgNH = img.naturalHeight
  const canvas = annoCanvas.value
  if (canvas) { canvas.width = imgNW; canvas.height = imgNH }
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
  ctx.strokeRect(px, py, pw, ph)
  ctx.globalAlpha = anno.source === 'AI' ? 0.08 : 0.12
  ctx.fillStyle = color
  ctx.fillRect(px, py, pw, ph)
  ctx.globalAlpha = 1
  ctx.setLineDash([])
  if (anno.label) {
    const fontSize = Math.max(12, Math.round(imgNW * 0.014))
    ctx.font = `bold ${fontSize}px sans-serif`
    const textW = ctx.measureText(anno.label).width
    const padX = 6, padY = 4, boxH = fontSize + padY * 2
    ctx.fillStyle = color
    ctx.fillRect(px, py - boxH, textW + padX * 2, boxH)
    ctx.fillStyle = '#fff'
    ctx.fillText(anno.label, px + padX, py - padY)
  }
  if (selected) {
    const hs = Math.max(6, Math.round(imgNW * 0.008))
    ctx.fillStyle = color
    ctx.setLineDash([])
    [[px, py], [px + pw, py], [px, py + ph], [px + pw, py + ph]].forEach(([cx, cy]) => {
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
  ctx.strokeRect(r.x, r.y, r.w, r.h)
  ctx.globalAlpha = 0.07
  ctx.fillStyle = '#ff4d4f'
  ctx.fillRect(r.x, r.y, r.w, r.h)
  ctx.restore()
}

/* 鼠标坐标 → 画布像素坐标 */
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
  if (e.button !== 0 || annoTool.value !== 'rect') return
  _drawOrigin = toCanvasCoords(e)
  drawState = { x: _drawOrigin.x, y: _drawOrigin.y, w: 0, h: 0 }
}
const onAnnoMouseMove = (e) => {
  if (!_drawOrigin || annoTool.value !== 'rect') return
  const pt = toCanvasCoords(e)
  drawState = {
    x: Math.min(_drawOrigin.x, pt.x), y: Math.min(_drawOrigin.y, pt.y),
    w: Math.abs(pt.x - _drawOrigin.x), h: Math.abs(pt.y - _drawOrigin.y)
  }
  redrawAnnotations()
}
const onAnnoMouseUp = (e) => {
  if (!_drawOrigin || annoTool.value !== 'rect') return
  const finalState = drawState
  _drawOrigin = null
  if (!finalState || finalState.w < 8 || finalState.h < 8) {
    drawState = null; redrawAnnotations(); return
  }
  // 显示标注名输入弹框（位置跟随屏幕鼠标）
  labelPopupPos.value = { x: e.clientX, y: e.clientY + 12 }
  showLabelInput.value = true
  pendingAnnoLabel.value = ''
  nextTick(() => labelInputRef.value?.focus())
}
const onAnnoMouseLeave = () => {
  if (_drawOrigin) { _drawOrigin = null; drawState = null; redrawAnnotations() }
}
const onAnnoClick = (e) => {
  if (annoTool.value !== 'select') return
  const pt = toCanvasCoords(e)
  const hit = visibleAnnotations.value.find(a => {
    const ax = a.x * imgNW, ay = a.y * imgNH
    return pt.x >= ax && pt.x <= ax + a.width * imgNW && pt.y >= ay && pt.y <= ay + a.height * imgNH
  })
  selectedAnnoId.value = hit ? hit.annotationId : null
  redrawAnnotations()
}

const confirmAnnoLabel = async () => {
  if (!drawState || !currentImage.value) { cancelAnnoLabel(); return }
  const dto = {
    imageId: currentImage.value.imageId,
    reportId: currentReport.value?.reportId || null,
    annoType: 'RECTANGLE',
    label: pendingAnnoLabel.value.trim() || '病灶',
    x: drawState.x / imgNW, y: drawState.y / imgNH,
    width: drawState.w / imgNW, height: drawState.h / imgNH,
    color: '#52c41a'
  }
  try {
    const res = await createAnnotation(dto)
    annotations.value.push(res.data)
    selectedAnnoId.value = res.data.annotationId
  } catch (_) { ElMessage.error('标注保存失败') }
  cancelAnnoLabel()
}

const cancelAnnoLabel = () => {
  showLabelInput.value = false
  pendingAnnoLabel.value = ''
  drawState = null
  redrawAnnotations()
}

const handleDeleteAnno = async (annotationId) => {
  try {
    await deleteAnnotation(annotationId)
    annotations.value = annotations.value.filter(a => a.annotationId !== annotationId)
    if (selectedAnnoId.value === annotationId) selectedAnnoId.value = null
    redrawAnnotations()
  } catch (_) { ElMessage.error('删除标注失败') }
}

const deleteSelectedAnno = () => {
  if (selectedAnnoId.value) handleDeleteAnno(selectedAnnoId.value)
}

/* evalResult 就绪后自动生成 AI 标注区域 */
watch(evalResult, async (newVal, oldVal) => {
  if (!newVal || !currentImage.value) return
  if (oldVal?.evalId === newVal.evalId) return
  if (!newVal.aiLabels) return
  await generateAiAnnotations(currentImage.value.imageId, newVal.reportId, newVal.aiLabels)
    .catch(() => {})
  await loadAnnotations(currentImage.value.imageId)
})

/* currentImage 切换时加载标注 */
watch(currentImage, (img) => {
  annotations.value = []
  selectedAnnoId.value = null
  drawState = null
  imgNW = 0; imgNH = 0
  if (img) loadAnnotations(img.imageId)
})

const beforeUpload = (file) => {
  if (file.size > 50 * 1024 * 1024) { ElMessage.error('文件大小不能超过50MB'); return false }
  return true
}
const handleUpload = async ({ file }) => {
  try {
    const res = await uploadImage(file, selectedCaseId.value, 'PA')
    images.value.push(res.data)
    currentImage.value = res.data
    ElMessage.success('影像上传成功')
    searchRetrieval(selectedCaseId.value, res.data.imageId).catch(() => {})
    if (!currentReport.value) {
      ElMessage.info('检测到新影像，正在自动生成AI报告…')
      await handleGenerate()
    }
  } catch (_) {}
}

/* ─────────────── 典型病例标记 ─────────────── */
const typicalDialogVisible = ref(false)
const typicalForm = ref({ typicalTags: '', typicalRemark: '' })
const typicalLoading = ref(false)

const handleMarkTypical = async () => {
  if (caseInfo.value.isTypical) {
    try {
      await markTypical(selectedCaseId.value, { isTypical: 0, typicalTags: '', typicalRemark: '' })
      caseInfo.value.isTypical = 0
      ElMessage.success('已取消典型标记')
      fetchCases()
    } catch (_) {}
  } else {
    typicalForm.value = { typicalTags: '', typicalRemark: '' }
    typicalDialogVisible.value = true
  }
}

const confirmMarkTypical = async () => {
  typicalLoading.value = true
  try {
    await markTypical(selectedCaseId.value, { isTypical: 1, ...typicalForm.value })
    caseInfo.value.isTypical = 1
    typicalDialogVisible.value = false
    ElMessage.success('已标记为典型病例')
    fetchCases()
  } finally { typicalLoading.value = false }
}

/* ─────────────── 打印 ─────────────── */
const handlePrint = () => { window.print() }

/* ─────────────── DICOM 元信息 ─────────────── */
const dicomMeta = computed(() => {
  if (!currentImage.value) return {}
  const img = currentImage.value
  return {
    '文件名': img.fileName || '—',
    '拍摄体位': img.viewPosition || '正位(PA)',
    '文件大小': img.fileSize ? (img.fileSize / 1024 / 1024).toFixed(2) + ' MB' : '—',
    '上传时间': formatDate(img.createdAt),
    '影像ID': img.imageId,
  }
})

/* ─────────────── 工作流步骤 ─────────────── */
const workflowSteps = computed(() => {
  const r = currentReport.value
  const status = r?.reportStatus
  const examTime = caseInfo.value.examTime
  const isSigned = status === 'SIGNED'
  
  return [
    { name: '影像上传', status: images.value.length > 0 ? 'done' : 'pending',
      time: formatTime(examTime), hint: '待操作' },
    { name: 'AI分析', status: r ? 'done' : (images.value.length > 0 ? 'active' : 'pending'),
      time: formatTime(r?.aiGenerateTime), hint: r ? '已完成' : '待生成' },
    { name: '医生审阅', status: isSigned ? 'done' : (r ? 'active' : 'pending'),
      time: isSigned ? formatTime(r?.signTime) : '', hint: isSigned ? '已完成' : (r ? '进行中' : '待操作') },
    { name: '签发报告', status: isSigned ? 'done' : 'pending',
      time: formatTime(r?.signTime), hint: isSigned ? '已完成' : '待操作' },
    { name: '评测存档', status: isSigned ? 'done' : 'pending',
      time: '', hint: isSigned ? '已完成' : '待操作' },
  ]
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
const createFormRef = ref(null)
const createForm = ref({
  examNo: '', patientAnonId: '', gender: 'M', age: null,
  bodyPart: '胸部', department: '', examTime: ''
})
const resetCreateForm = () => {
  createForm.value = { examNo:'', patientAnonId:'', gender:'M', age:null, bodyPart:'胸部', department:'', examTime:'' }
  createFormRef.value?.clearValidate()
}

const createRules = {
  examNo:       [{ required: true, message: '请输入检查号', trigger: 'blur' }],
  patientAnonId:[{ required: true, message: '请输入患者匿名ID', trigger: 'blur' }],
  gender:       [{ required: true, message: '请选择性别', trigger: 'change' }],
  age:          [{ required: true, message: '请输入年龄', trigger: 'blur' }],
  bodyPart:     [{ required: true, message: '请输入检查部位', trigger: 'blur' }],
  examTime:     [{ required: true, message: '请选择检查时间', trigger: 'change' }],
}
const handleCreateCase = async () => {
  await createFormRef.value.validate()
  creating.value = true
  try {
    const res = await createCase(createForm.value)
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
        try { const dr = await getCaseById(newId); if (dr.data) await selectCase(dr.data) } catch (_) {}
      }
    }
  } finally { creating.value = false }
}

/* ─────────────── 评测 / 术语 计算属性 ─────────────── */
const evalLabels = computed(() => evalResult.value || {})
const pendingTermCount = computed(() => termCorrections.value.filter(t => !t.isAccepted || t.isAccepted === 0).length)
const pendingCaseAlerts = computed(() => caseAlerts.value.filter(a => a.alertStatus === 'PENDING').length)
const gradeColor = (g) => ({ A: '#52c41a', B: '#73d13d', C: '#faad14', D: '#ff7875', F: '#f5222d' }[g] || '#909399')
const gradeAdvice = (g) => ({
  A: '质量优秀 — 可直接签发',
  B: '质量良好 — 快速审核后签发',
  C: '质量一般 — 请仔细审核再签发',
  D: '质量较差 — 建议重写或重新生成',
}[g] || '评测完成')
const pct = (v) => v != null ? (Number(v) * 100).toFixed(1) + '%' : '—'

onUnmounted(() => { clearInterval(pollTimer) })

const startEvalPoll = (reportId) => {
  evalPolling.value = true
  let attempts = 0
  clearInterval(pollTimer)
  pollTimer = setInterval(async () => {
    if (currentReport.value?.reportId !== reportId) {
      clearInterval(pollTimer)
      evalPolling.value = false
      return
    }
    attempts++
    await loadEvalResult(reportId)
    if (evalResult.value || attempts >= 12) {
      clearInterval(pollTimer)
      evalPolling.value = false
      if (evalResult.value && currentImage.value) {
        loadAnnotations(currentImage.value.imageId)
      }
    }
  }, 3000)
}

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
    ElMessage.error('润色失败：' + (e?.message || '请确认AI服务可用'))
  } finally { polishing.value = false }
}

const applyPolish = () => {
  if (polishResult.value) {
    draftFindings.value = polishResult.value.polished_findings || draftFindings.value
    draftImpression.value = polishResult.value.polished_impression || draftImpression.value
    polishDialogVisible.value = false
    ElMessage.success('AI润色内容已应用')
  }
}

/* ─────────────── AI 鉴别诊断解析 ─────────────── */
const LABEL_CN = {
  'Atelectasis': '肺不张', 'Cardiomegaly': '心脏肥大', 'Consolidation': '实变',
  'Edema': '水肿', 'Enlarged Cardiomediastinum': '纵隔增宽', 'Fracture': '骨折',
  'Lung Lesion': '肺部病变', 'Lung Opacity': '肺部阴影', 'No Finding': '未见异常',
  'Pleural Effusion': '胸腔积液', 'Pleural Other': '其他胸膜异常', 'Pneumonia': '肺炎',
  'Pneumothorax': '气胸', 'Support Devices': '辅助器械'
}

const parsedLabelProbs = computed(() => {
  if (!evalResult.value?.aiLabels) return []
  try {
    const obj = typeof evalResult.value.aiLabels === 'string'
      ? JSON.parse(evalResult.value.aiLabels) : evalResult.value.aiLabels
    return Object.entries(obj)
      .map(([k, v]) => ({ label: k, labelCn: LABEL_CN[k] || k, prob: Number(v) }))
      .sort((a, b) => b.prob - a.prob)
  } catch (_) { return [] }
})

const topFindings = computed(() => parsedLabelProbs.value.filter(p => p.prob >= 0.3 && p.label !== 'No Finding'))

const riskLevel = computed(() => {
  const max = topFindings.value.length > 0 ? topFindings.value[0].prob : 0
  if (max >= 0.8) return { text: '高风险', color: '#f5222d', bg: 'rgba(245,34,45,0.08)' }
  if (max >= 0.5) return { text: '中风险', color: '#fa8c16', bg: 'rgba(250,140,22,0.08)' }
  if (max >= 0.3) return { text: '低风险', color: '#52c41a', bg: 'rgba(82,196,26,0.08)' }
  return { text: '正常', color: '#1890ff', bg: 'rgba(24,144,255,0.08)' }
})

const labelBarColor = (prob) => {
  if (prob >= 0.7) return '#f5222d'
  if (prob >= 0.4) return '#fa8c16'
  return '#1890ff'
}

/* ─────────────── 置信度颜色 ─────────────── */
const confColor = (v) => {
  if (v >= 0.8) return '#52c41a'
  if (v >= 0.6) return '#faad14'
  return '#ff4d4f'
}

/* ─────────────── 工具方法 ─────────────── */
const statusLabel = (s) => ({ NONE: '待生成', AI_DRAFT: 'AI草稿', EDITING: '编辑中', SIGNED: '已签发' }[s] || s || '—')
const statusColor = (s) => ({ NONE: 'orange', AI_DRAFT: 'blue', EDITING: 'blue', SIGNED: 'green' }[s] || 'gray')
const genderLabel = (g) => ({ M: '男', F: '女' }[g] || g || '—')

const formatDate = (d) => {
  if (!d) return '—'
  return new Date(d).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
const formatTime = (d) => {
  if (!d) return ''
  const dt = new Date(d)
  return `${String(dt.getHours()).padStart(2,'0')}:${String(dt.getMinutes()).padStart(2,'0')}`
}

/* ─────────────── 初始化 ─────────────── */
onMounted(async () => {
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
      } catch (_) {}
    }
  }
})
</script>

<style scoped>
/* ═══════════════════════════════════════════
   整体布局
═══════════════════════════════════════════ */
.workstation {
  display: flex;
  height: 100%;
  overflow: hidden;
}

/* ═══════════════════════════════════════════
   左侧病例列表面板
═══════════════════════════════════════════ */
.case-panel {
  width: 240px;
  flex-shrink: 0;
  background: #fff;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #e8e8e8;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 10px;
  flex-shrink: 0;
}
.panel-title { font-size: 14px; font-weight: 600; color: #1a1a1a; }
.count-badge :deep(.el-badge__content) { font-size: 10px; height: 16px; line-height: 16px; padding: 0 5px; }

.panel-search { padding: 0 12px 10px; flex-shrink: 0; }
.panel-search :deep(.el-input__wrapper) {
  background: #f5f5f5;
  border: 1px solid #e8e8e8;
  box-shadow: none;
}
.panel-search :deep(.el-input__inner) { color: #333; font-size: 12px; }
.panel-search :deep(.el-input__inner::placeholder) { color: #bbb; }

.add-btn {
  width: 22px; height: 22px;
  border-radius: 4px;
  background: rgba(64,169,255,0.12);
  border: 1px solid rgba(64,169,255,0.25);
  color: #40a9ff;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; font-size: 13px;
  transition: all .15s; flex-shrink: 0;
}
.add-btn:hover { background: rgba(64,169,255,0.25); }

.panel-filters {
  display: flex;
  gap: 6px;
  padding: 0 12px 10px;
  flex-shrink: 0;
}
.filter-btn {
  flex: 1;
  padding: 3px 0;
  border: 1px solid #d9d9d9;
  background: transparent;
  border-radius: 4px;
  font-size: 11px;
  color: #595959;
  cursor: pointer;
  transition: all .2s;
}
.filter-btn:hover { color: #1890ff; border-color: #1890ff; }
.active-orange { background: rgba(250,140,22,0.2) !important; border-color: #fa8c16 !important; color: #fa8c16 !important; }
.active-blue   { background: rgba(24,144,255,0.2) !important; border-color: #1890ff !important; color: #1890ff !important; }
.active-green  { background: rgba(82,196,26,0.2)  !important; border-color: #52c41a !important; color: #52c41a  !important; }

.batch-bar {
  padding: 6px 12px;
  flex-shrink: 0;
}
.batch-bar .el-button { width: 100%; }
.batch-progress-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: #e6f7ff;
  border-bottom: 1px solid #91d5ff;
  font-size: 12px;
  color: #1890ff;
  flex-shrink: 0;
}
.case-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 8px;
}
.case-list::-webkit-scrollbar { width: 4px; }
.case-list::-webkit-scrollbar-track { background: transparent; }
.case-list::-webkit-scrollbar-thumb { background: rgba(0,0,0,0.12); border-radius: 2px; }

.case-card {
  padding: 10px 10px;
  border-radius: 6px;
  margin-bottom: 4px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all .15s;
}
.case-card:hover { background: #f5f7fa; border-color: #d9d9d9; }
.case-card.selected { background: rgba(64,169,255,0.12); border-color: #40a9ff; }

.card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.exam-no { font-size: 13px; font-weight: 600; color: #262626; }

.status-badge {
  font-size: 10px; padding: 1px 6px; border-radius: 3px; font-weight: 500;
}
.badge-orange { background: rgba(250,140,22,0.2); color: #fa8c16; }
.badge-blue   { background: rgba(24,144,255,0.2); color: #69b1ff; }
.badge-green  { background: rgba(82,196,26,0.2);  color: #73d13d; }
.badge-gray   { background: #f0f0f0; color: #999; }

.card-info { font-size: 11px; color: #8c8c8c; margin-bottom: 3px; }
.card-meta { font-size: 11px; color: #666; margin-bottom: 3px; display: flex; align-items: center; gap: 6px; }
.meta-text { color: #8c8c8c; }
.meta-tag { display: inline-block; padding: 1px 6px; border-radius: 3px; font-size: 10px; line-height: 16px; }
.meta-tag-body { background: #f0f5ff; color: #597ef7; }
.meta-tag-ai { background: #e6f7ff; color: #1890ff; font-weight: 600; }
.meta-tag-edit { background: #fff7e6; color: #fa8c16; }
.meta-grade { display: inline-block; width: 18px; height: 18px; border-radius: 4px; text-align: center; line-height: 18px; font-size: 10px; font-weight: 700; color: #fff; }
.grade-A { background: #52c41a; }
.grade-B { background: #73d13d; }
.grade-C { background: #faad14; }
.grade-D { background: #ff7875; }
.grade-F { background: #f5222d; }
.card-time { font-size: 10px; color: #bbb; }

.load-more {
  text-align: center;
  padding: 8px;
  font-size: 12px;
  color: #40a9ff;
  cursor: pointer;
}

/* ═══════════════════════════════════════════
   右侧工作区
═══════════════════════════════════════════ */
.workspace {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #f0f2f5;
}
.workspace-empty {
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: rgba(0,0,0,0.25);
  font-size: 14px;
}

/* 工作区顶部 */
.ws-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  flex-shrink: 0;
  gap: 12px;
}
.ws-title-block { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.ws-exam-no { font-size: 16px; font-weight: 700; color: #1a1a1a; }
.ws-status-tag {
  font-size: 11px; padding: 2px 8px; border-radius: 4px; font-weight: 500;
}
.tag-orange { background: #fff7e6; border: 1px solid #ffd591; color: #fa8c16; }
.tag-blue   { background: #e6f4ff; border: 1px solid #91caff; color: #1677ff; }
.tag-green  { background: #f6ffed; border: 1px solid #b7eb8f; color: #52c41a; }
.ws-patient-info { font-size: 12px; color: #666; }
.ws-actions { display: flex; gap: 6px; flex-shrink: 0; }

/* 滚动内容区 */
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
.ws-scroll::-webkit-scrollbar-track { background: #f0f2f5; }
.ws-scroll::-webkit-scrollbar-thumb { background: #d0d0d0; border-radius: 3px; }

/* 主内容分栏 */
.ws-body {
  display: flex;
  flex: 0 0 440px;
  overflow: hidden;
  gap: 12px;
}

/* ─── 影像查看器 ─── */
.viewer-panel {
  width: 420px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #111820;
  border-radius: 8px;
  overflow: hidden;
}
.viewer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: rgba(255,255,255,0.04);
  border-bottom: 1px solid rgba(255,255,255,0.06);
  flex-shrink: 0;
}
.viewer-filename { font-size: 11px; color: rgba(255,255,255,0.45); }
.viewer-tools { display: flex; gap: 4px; }
.tool-btn {
  width: 26px; height: 26px;
  background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 4px;
  color: rgba(255,255,255,0.7);
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px;
  transition: all .15s;
}
.tool-btn:hover { background: rgba(255,255,255,0.12); color: #fff; }
.tool-btn:disabled { opacity: 0.35; cursor: not-allowed; pointer-events: none; }

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
  color: rgba(255,255,255,0.3); font-size: 12px;
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

/* ─── 标注列表面板 ─── */
.anno-list-panel {
  flex-shrink: 0;
  border-top: 1px solid rgba(255,255,255,0.07);
  background: rgba(0,0,0,0.3);
  max-height: 140px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.anno-list-header {
  display: flex; align-items: center; gap: 6px;
  padding: 5px 10px;
  font-size: 11px; font-weight: 600; color: rgba(255,255,255,0.7);
  border-bottom: 1px solid rgba(255,255,255,0.06);
  flex-shrink: 0;
}
.anno-count { color: #40a9ff; margin-left: 2px; }
.anno-hint { font-size: 10px; font-weight: 400; color: rgba(255,255,255,0.35); margin-left: auto; }
.anno-list-body { overflow-y: auto; padding: 2px 0; }
.anno-list-item {
  display: flex; align-items: center; gap: 5px;
  padding: 4px 10px; cursor: pointer; font-size: 11px;
  color: rgba(255,255,255,0.75);
  transition: background 0.15s;
}
.anno-list-item:hover { background: rgba(255,255,255,0.06); }
.anno-item-selected { background: rgba(24,144,255,0.18) !important; }
.anno-color-dot {
  width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0;
}
.anno-src-tag {
  font-size: 9px; font-weight: 700; padding: 1px 4px; border-radius: 3px; flex-shrink: 0;
}
.tag-ai { background: rgba(24,144,255,0.25); color: #69c0ff; }
.tag-dr { background: rgba(82,196,26,0.25); color: #95de64; }
.anno-lbl { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.anno-conf { font-size: 10px; color: rgba(255,255,255,0.4); flex-shrink: 0; }
.anno-del-btn {
  background: none; border: none; color: rgba(255,255,255,0.3); cursor: pointer;
  font-size: 14px; line-height: 1; padding: 0 2px;
  flex-shrink: 0; transition: color 0.15s;
}
.anno-del-btn:hover { color: #ff7875; }

/* ─── 标注名输入弹框（fixed定位） ─── */
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

/* ─── 报告编辑器 ─── */
.report-panel {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.report-tabs { height: 100%; display: flex; flex-direction: column; }
.report-tabs :deep(.el-tabs__header) { margin: 0; padding: 0 16px; border-bottom: 1px solid #f0f0f0; flex-shrink: 0; }
.report-tabs :deep(.el-tabs__content) { flex: 1; overflow-y: auto; }
.report-tabs :deep(.el-tab-pane) { height: 100%; }

.no-report {
  display: flex; align-items: center; justify-content: center;
  min-height: 200px;
  padding: 24px 16px;
}
.report-form { padding: 16px; }

/* ─── 状态提示 ─── */
.status-notice {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 6px;
  margin-bottom: 12px;
  font-size: 12px;
}
.status-ai-draft {
  background: #e6f4ff;
  border: 1px solid #91caff;
}
.status-editing {
  background: #fff7e6;
  border: 1px solid #ffd591;
}
.status-signed {
  background: #f6ffed;
  border: 1px solid #b7eb8f;
}

/* ─── Footer 内联评测摘要 ─── */
.footer-eval-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  flex-wrap: wrap;
}
.feval-chip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 500;
}
.feval-high   { background: #f6ffed; color: #389e0d; border: 1px solid #b7eb8f; }
.feval-mid    { background: #fff7e6; color: #d46b08; border: 1px solid #ffd591; }
.feval-grade  { border: none; }
.feval-pending{ background: #f0f5ff; color: #2f54eb; border: 1px solid #adc6ff; }
.feval-alert  { background: #fff1f0; color: #cf1322; border: 1px solid #ffa39e; }
.feval-safe   { background: #f6ffed; color: #389e0d; border: 1px solid #b7eb8f; }

/* ─── 最终报告标签 ─── */
.final-label {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  font-weight: 400;
  color: #52c41a;
  background: #f6ffed;
  padding: 1px 6px;
  border-radius: 3px;
}

/* ─── AI质量评测快览条 ─── */
.eval-quick-bar {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  margin-top: 10px; padding: 8px 12px;
  background: #f6f8ff; border: 1px solid #d6e4ff; border-radius: 6px;
  font-size: 12px;
}
.eval-quick-bar-relaxed { margin-top: 0; min-height: 42px; }
.eval-grade-badge {
  width: 26px; height: 26px; border-radius: 5px;
  color: #fff; font-size: 13px; font-weight: 700;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.eval-scores-mini { display: flex; gap: 10px; color: #666; }
.eval-scores-mini b { color: #333; }
.eval-advice-text { font-size: 12px; font-weight: 500; }
.advice-A { color: #389e0d; }
.advice-B { color: #52c41a; }
.advice-C { color: #d46b08; }
.advice-D, .advice-F { color: #cf1322; }
.field-block {}
.field-label {
  display: flex; align-items: center; gap: 6px;
  font-size: 13px; font-weight: 600; color: #333;
  margin-bottom: 6px;
}
.ai-label {
  display: flex; align-items: center; gap: 3px;
  font-size: 11px; font-weight: 400; color: #1890ff;
  background: #e6f4ff; padding: 1px 6px; border-radius: 3px;
}
.confidence-bar {
  display: flex; align-items: center; gap: 10px;
  margin-top: 14px; padding: 10px 12px;
  background: #f9fafb; border-radius: 6px;
}
.conf-label { font-size: 12px; color: #666; white-space: nowrap; }
.conf-value { font-size: 13px; font-weight: 700; color: #52c41a; white-space: nowrap; }

.history-list { padding: 12px 16px; }
.history-item {
  padding: 10px 12px;
  background: #fafafa;
  border-radius: 6px;
  border: 1px solid #f0f0f0;
  margin-bottom: 8px;
}
.history-meta { display: flex; justify-content: space-between; margin-bottom: 4px; }
.history-editor { font-size: 12px; font-weight: 600; color: #333; }
.history-time { font-size: 11px; color: #999; }
.history-note { font-size: 11px; color: #666; margin-bottom: 6px; }
.diff-row { display: flex; gap: 6px; margin-bottom: 3px; }
.diff-label { font-size: 11px; color: #999; white-space: nowrap; }
.diff-text { font-size: 11px; color: #444; line-height: 1.5; }

.dicom-meta { padding: 12px 16px; }
.meta-row {
  display: flex; padding: 8px 0;
  border-bottom: 1px solid #f5f5f5;
  font-size: 12px;
}
.meta-key { width: 90px; color: #999; flex-shrink: 0; }
.meta-val { color: #333; }

/* ═══════════════════════════════════════════
   相似病例
═══════════════════════════════════════════ */
.similar-section {
  flex-shrink: 0;
}
.section-title {
  display: flex; align-items: center; gap: 6px;
  font-size: 13px; font-weight: 600; color: #333;
  margin-bottom: 8px;
}
.section-sub { font-size: 11px; font-weight: 400; color: #999; }

.similar-cards { display: flex; gap: 10px; }
.similar-card {
  flex: 1; background: #fff; border-radius: 6px;
  border: 1px solid #e8e8e8; overflow: hidden;
  display: flex; flex-direction: column;
  transition: border-color .15s, box-shadow .15s;
}
.similar-card:hover {
  border-color: #1890ff;
  box-shadow: 0 2px 8px rgba(24,144,255,.18);
}
.similar-img-placeholder {
  height: 70px; background: #1a2030;
  display: flex; align-items: center; justify-content: center;
}
.similar-info { padding: 8px 10px; }
.sim-score { font-size: 11px; font-weight: 700; }
.score-high { color: #52c41a; }
.score-mid  { color: #fa8c16; }
.score-low  { color: #1890ff; }
.sim-exam { font-size: 11px; color: #666; margin: 2px 0; }
.sim-findings { font-size: 10px; color: #999; line-height: 1.4; }

/* ═══════════════════════════════════════════
   工作流进度
═══════════════════════════════════════════ */
.progress-section {
  padding: 8px 12px;
  background: #fff;
  margin: 0;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  flex-shrink: 0;
}
.workflow-steps {
  display: flex;
  align-items: center;
  padding: 2px 0;
}
.step-item {
  display: flex; align-items: center; flex: 1; position: relative;
}
.step-circle {
  width: 24px; height: 24px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 600; flex-shrink: 0;
  background: #e8e8e8; color: #999; border: 2px solid #e8e8e8;
}
.step-item.done .step-circle { background: #52c41a; border-color: #52c41a; color: #fff; }
.step-item.active .step-circle { background: #1890ff; border-color: #1890ff; color: #fff; }

.step-info { padding: 0 6px; flex-shrink: 0; }
.step-name { font-size: 10px; font-weight: 600; color: #333; white-space: nowrap; }
.step-time { font-size: 9px; color: #999; white-space: nowrap; }
.step-item.done .step-name, .step-item.active .step-name { color: #1a1a1a; }

.step-line {
  flex: 1; height: 2px;
  background: #e8e8e8;
  margin: 0 4px;
}
.step-item.done + .step-item .step-line { background: #52c41a; }

/* ═══════════════════════════════════════════
   AI 智能分析面板
═══════════════════════════════════════════ */
.ai-analysis-section {
  margin-top: 12px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 14px;
}
.ai-risk-card {
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 12px 14px;
  margin-top: 10px;
}
.risk-header { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.risk-badge {
  padding: 3px 10px; border-radius: 4px;
  color: #fff; font-size: 12px; font-weight: 700; white-space: nowrap;
}
.risk-grade { display: flex; flex-direction: column; align-items: center; }
.risk-grade-letter { font-size: 24px; font-weight: 800; line-height: 1; }
.risk-grade-label { font-size: 10px; color: #999; }
.risk-metrics {
  display: flex; gap: 10px; flex-wrap: wrap; font-size: 11px; color: #666; margin-left: auto;
}
.risk-metrics b { color: #333; }
.risk-findings { margin-top: 8px; display: flex; align-items: center; flex-wrap: wrap; }
.risk-findings-label { font-size: 11px; color: #999; margin-right: 4px; }

.ai-label-chart {
  margin-top: 12px;
  background: #fafafa;
  border-radius: 6px;
  padding: 10px 12px;
}
.label-chart-title {
  font-size: 11px; font-weight: 600; color: #666; margin-bottom: 8px;
}
.label-bars { display: flex; flex-direction: column; gap: 4px; }
.label-bar-row { display: flex; align-items: center; gap: 6px; }
.label-bar-name { width: 70px; font-size: 10px; color: #666; text-align: right; flex-shrink: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.label-bar-track { flex: 1; height: 10px; background: #e8e8e8; border-radius: 5px; overflow: hidden; }
.label-bar-fill { height: 100%; border-radius: 5px; transition: width 0.6s ease; }
.label-bar-pct { width: 32px; font-size: 10px; font-weight: 600; text-align: right; flex-shrink: 0; }

.ai-label-alerts {
  margin-top: 10px;
  padding: 8px 10px;
  background: #fffbe6;
  border-radius: 6px;
  border: 1px solid #ffe58f;
}
.label-alert-row { display: flex; align-items: center; flex-wrap: wrap; gap: 4px; margin-bottom: 4px; }
.label-alert-row:last-child { margin-bottom: 0; }
.label-alert-icon { font-size: 14px; }
.label-alert-title { font-size: 11px; font-weight: 600; color: #333; }

/* ─── AI润色弹窗 ─── */
.polish-compare { display: flex; gap: 12px; align-items: stretch; }
.polish-col { flex: 1; background: #fafafa; border-radius: 6px; padding: 10px; }
.polish-col-title { font-size: 12px; font-weight: 700; margin-bottom: 8px; }
.polish-field-label { font-size: 10px; color: #999; margin-bottom: 3px; }
.polish-text { font-size: 12px; color: #333; line-height: 1.6; white-space: pre-wrap; }
.polish-text-new { color: #52c41a; }
.polish-arrow { display: flex; align-items: center; font-size: 20px; color: #bbb; flex-shrink: 0; }

/* ═══════════════════════════════════════════
   底部操作栏
═══════════════════════════════════════════ */
.ws-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: #fff;
  border-top: 1px solid #e8e8e8;
  flex-shrink: 0;
  margin-top: 10px;
}
.footer-nav { display: flex; gap: 8px; }
.footer-actions { display: flex; gap: 8px; }

/* ═══════════════════════════════════════════
   Tab 徽标
═══════════════════════════════════════════ */
.tab-badge { margin-left: 4px; vertical-align: middle; }
:deep(.tab-badge .el-badge__content) { transform: scale(0.85); }

/* ═══════════════════════════════════════════
   AI 评测
═══════════════════════════════════════════ */
.eval-summary {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 12px;
}
.eval-grade {
  font-size: 42px;
  font-weight: 700;
  line-height: 1;
  text-align: center;
  min-width: 48px;
}
.label-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.label-chip {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 11px;
  border: 1px solid transparent;
}
.label-positive { background: #fff1f0; border-color: #ffa39e; color: #cf1322; }
.label-negative { background: #f6ffed; border-color: #b7eb8f; color: #389e0d; }
.label-name { font-weight: 500; }
.label-prob { opacity: 0.75; }

/* ═══════════════════════════════════════════
   术语建议（内嵌报告编辑）
═══════════════════════════════════════════ */
.term-suggestions-panel {
  margin-top: 12px;
  border: 1px solid #ffe7ba;
  border-radius: 6px;
  background: #fffbe6;
  padding: 8px 10px;
}
.term-suggestions-panel-relaxed {
  margin-top: 0;
}
.term-panel-header {
  display: flex;
  align-items: center;
  margin-bottom: 6px;
}
.term-panel-title {
  font-size: 12px;
  font-weight: 600;
  color: #d48806;
  margin-left: 4px;
}
.term-inline-item {
  padding: 5px 0;
  border-bottom: 1px dashed #ffd591;
}
.term-inline-item:last-child { border-bottom: none; }
.term-inline-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 3px;
  font-size: 12px;
}
.term-orig { color: #cf1322; text-decoration: line-through; }
.term-corr { color: #389e0d; font-weight: 600; }
.term-inline-actions { display: flex; gap: 6px; margin-top: 4px; }

/* ═══════════════════════════════════════════
   缩略图删除按钮
═══════════════════════════════════════════ */
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

/* ═══ 已签发报告视图 ═══ */
.signed-report-view { display: flex; flex-direction: column; height: 100%; overflow-y: auto; padding: 0; }
.signed-report-view::-webkit-scrollbar { width: 5px; }
.signed-report-view::-webkit-scrollbar-thumb { background: #d0d0d0; border-radius: 3px; }

.signed-banner {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 18px; background: linear-gradient(135deg, #f6ffed 0%, #e8fce8 100%);
  border-bottom: 2px solid #b7eb8f; flex-shrink: 0;
}
.signed-banner-left { display: flex; align-items: center; gap: 12px; }
.signed-icon-circle {
  width: 36px; height: 36px; border-radius: 50%; background: #52c41a;
  display: flex; align-items: center; justify-content: center; color: #fff;
}
.signed-title { font-size: 15px; font-weight: 700; color: #1a1a1a; }
.signed-meta { font-size: 11px; color: #8c8c8c; margin-top: 2px; }
.signed-grade-badge {
  width: 40px; height: 40px; border-radius: 8px; color: #fff; font-size: 22px; font-weight: 800;
  display: flex; align-items: center; justify-content: center;
}

.signed-content { padding: 16px 18px; flex-shrink: 0; }
.signed-section { margin-bottom: 14px; }
.signed-section-label {
  font-size: 12px; font-weight: 600; color: #666; margin-bottom: 6px;
  display: flex; align-items: center; gap: 4px;
}
.signed-text {
  font-size: 13px; line-height: 1.7; color: #1a1a1a; padding: 10px 14px;
  background: #fafafa; border: 1px solid #f0f0f0; border-radius: 6px; white-space: pre-wrap;
}

.signed-ai-compare {
  margin-top: 8px; border: 1px dashed #d9d9d9; border-radius: 6px; overflow: hidden;
}
.compare-header {
  display: flex; align-items: center; gap: 6px; padding: 8px 14px;
  background: #fafafa; cursor: pointer; font-size: 12px; color: #8c8c8c;
}
.compare-header:hover { background: #f0f0f0; }
.compare-diff-hint {
  font-size: 10px; padding: 1px 6px; background: #fff7e6; color: #d48806;
  border-radius: 3px; border: 1px solid #ffe58f;
}
.compare-body { padding: 10px 14px; }
.compare-field { margin-bottom: 8px; }
.compare-label { font-size: 11px; color: #999; margin-bottom: 3px; }
.compare-text {
  font-size: 12px; color: #8c8c8c; line-height: 1.6; padding: 6px 10px;
  background: #f5f5f5; border-radius: 4px; font-style: italic; white-space: pre-wrap;
}

.signed-eval-section {
  padding: 14px 18px; border-top: 1px solid #f0f0f0; flex-shrink: 0;
}
.signed-eval-header {
  display: flex; align-items: center; gap: 6px;
  font-size: 13px; font-weight: 600; color: #333; margin-bottom: 12px;
}
.signed-eval-metrics {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-bottom: 10px;
}
.metric-card {
  text-align: center; padding: 10px 4px; background: #fafafa;
  border: 1px solid #f0f0f0; border-radius: 6px;
}
.metric-value { font-size: 18px; font-weight: 700; line-height: 1; }
.metric-label { font-size: 10px; color: #999; margin-top: 4px; }
.signed-eval-advice {
  font-size: 12px; padding: 6px 12px; border-radius: 5px; margin-bottom: 8px;
}
.signed-eval-advice.advice-A { background: #f6ffed; color: #389e0d; }
.signed-eval-advice.advice-B { background: #e6f4ff; color: #0958d9; }
.signed-eval-advice.advice-C { background: #fffbe6; color: #ad6800; }
.signed-eval-advice.advice-D { background: #fff1f0; color: #cf1322; }
/* ─── AI 审核建议面板 ─── */
.ai-advice-panel {
  background: #faf7ff;
  border: 1px solid #d3adf7;
  border-radius: 8px;
  padding: 12px 14px;
  margin-top: 10px;
  font-size: 12px;
}
.ai-advice-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: #531dab;
  margin-bottom: 8px;
}
.advice-priority-high {
  margin-left: auto;
  background: #fff1f0;
  color: #cf1322;
  border: 1px solid #ffa39e;
  border-radius: 10px;
  padding: 1px 8px;
  font-size: 11px;
  font-weight: 500;
}
.advice-priority-mid {
  margin-left: auto;
  background: #fffbe6;
  color: #ad6800;
  border: 1px solid #ffe58f;
  border-radius: 10px;
  padding: 1px 8px;
  font-size: 11px;
  font-weight: 500;
}
.ai-advice-assessment {
  color: #444;
  margin-bottom: 8px;
  line-height: 1.6;
}
.ai-advice-block { margin-bottom: 8px; }
.ai-advice-label {
  font-weight: 600;
  color: #722ed1;
  margin-bottom: 4px;
}
.ai-advice-list {
  margin: 0 0 4px 14px;
  padding: 0;
  color: #555;
  line-height: 1.7;
}
.ai-advice-text {
  background: #fff;
  border: 1px solid #e8e0f5;
  border-radius: 5px;
  padding: 8px 10px;
  color: #333;
  line-height: 1.7;
  margin-bottom: 4px;
  white-space: pre-wrap;
}

.signed-label-row {
  display: flex; align-items: center; flex-wrap: wrap; gap: 2px;
  margin-bottom: 4px; font-size: 12px;
}
.label-row-title { font-weight: 600; margin-right: 4px; }
.label-row-title.danger { color: #f56c6c; }
.label-row-title.warning { color: #e6a23c; }

/* ─── 危急值预警行 ─── */
.alert-item-row {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 8px; border-radius: 5px; margin-bottom: 5px;
  font-size: 12px;
}
.alert-pending  { background: #fff1f0; border: 1px solid #ffccc7; }
.alert-resolved { background: #f6ffed; border: 1px solid #b7eb8f; }
.alert-label { font-weight: 600; flex: 1; }
.alert-prob  { color: #666; }
.alert-note  { color: #999; font-size: 11px; }
</style>
