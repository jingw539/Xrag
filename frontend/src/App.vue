<template>
  <el-config-provider :locale="zhCn">
    <div id="app">
      <router-view />
    </div>
  </el-config-provider>
</template>

<script setup>
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
</script>

<style>
:root {
  --xrag-bg: #0d1420;
  --xrag-bg-soft: #111a26;
  --xrag-panel: #0f1923;
  --xrag-panel-2: #131f2c;
  --xrag-border: rgba(111, 134, 166, 0.18);
  --xrag-border-strong: rgba(111, 134, 166, 0.28);
  --xrag-text: #d0dcf0;
  --xrag-text-soft: rgba(208, 220, 240, 0.72);
  --xrag-text-faint: rgba(208, 220, 240, 0.46);
  --xrag-primary: #4a9eff;
  --xrag-shadow: 0 10px 30px rgba(0, 0, 0, 0.24);
}

html, body {
  background: #0d1420;
  margin: 0;
  padding: 0;
}

#app {
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  height: 100vh;
  margin: 0;
  color: #d0dcf0;
}

* {
  box-sizing: border-box;
}

/* 全局：弹窗、下拉、日期选择器等去白底 */
.el-overlay {
  background-color: rgba(0, 0, 0, 0.5) !important;
}
.el-dialog {
  background: #0d1420 !important;
  border: 1px solid rgba(111, 134, 166, 0.2) !important;
}
.el-dialog__header,
.el-dialog__body,
.el-dialog__footer {
  background: #0d1420 !important;
  border-color: rgba(111, 134, 166, 0.16) !important;
  color: #d0dcf0 !important;
}
.el-dialog__title,
.el-dialog__headerbtn .el-dialog__close {
  color: #d0dcf0 !important;
}
.el-select__dropdown,
.el-picker-panel,
.el-popper {
  background: #0f1923 !important;
  border: 1px solid rgba(111, 134, 166, 0.2) !important;
}
.el-select-dropdown__item,
.el-picker-panel__body,
.el-date-table th,
.el-date-table td {
  background: #0f1923 !important;
  color: #d0dcf0 !important;
}
.el-select-dropdown__item:hover,
.el-date-table td.available:hover {
  background: rgba(111, 134, 166, 0.2) !important;
}
.el-picker-panel__icon-btn,
.el-date-picker__header-label {
  color: #d0dcf0 !important;
}
.el-descriptions__body,
.el-descriptions__table {
  background: #0f1923 !important;
}
.el-descriptions__label,
.el-descriptions__content {
  background: #0f1923 !important;
  color: #d0dcf0 !important;
  border-color: rgba(111, 134, 166, 0.16) !important;
}

/* XRAG final global polish */
.el-dropdown__popper,
.el-dropdown-menu,
.el-select-dropdown,
.el-picker__popper,
.el-tooltip__popper,
.el-popover.el-popper {
  background: var(--xrag-panel) !important;
  border: 1px solid var(--xrag-border) !important;
  box-shadow: var(--xrag-shadow) !important;
}

.el-dropdown-menu__item,
.el-select-dropdown__item,
.el-date-picker__header-label,
.el-picker-panel__icon-btn,
.el-time-panel__btn,
.el-tooltip__popper,
.el-popover.el-popper {
  color: var(--xrag-text) !important;
}

.el-dropdown-menu__item:hover,
.el-select-dropdown__item.hover,
.el-select-dropdown__item:hover,
.el-time-panel__btn:hover {
  background: rgba(74, 158, 255, 0.12) !important;
  color: #f4f8ff !important;
}

.el-button.is-disabled,
.el-button.is-disabled:hover,
.el-button[disabled],
.el-button[disabled]:hover {
  background: rgba(111, 134, 166, 0.10) !important;
  border-color: rgba(111, 134, 166, 0.18) !important;
  color: rgba(208, 220, 240, 0.42) !important;
}

.el-switch.is-checked .el-switch__core {
  background: linear-gradient(90deg, #4a9eff, #6fb4ff) !important;
  border-color: #4a9eff !important;
}

.el-switch .el-switch__core {
  background: rgba(111, 134, 166, 0.28) !important;
  border-color: rgba(111, 134, 166, 0.36) !important;
}

.el-scrollbar__bar.is-vertical .el-scrollbar__thumb,
.el-scrollbar__bar.is-horizontal .el-scrollbar__thumb {
  background: rgba(111, 134, 166, 0.42) !important;
}

</style>

