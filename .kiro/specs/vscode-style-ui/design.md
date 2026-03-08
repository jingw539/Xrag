# 设计文档

## 概述

本设计文档描述如何将 VSCode 的视觉风格应用到胸部X光智能辅助诊断系统的前端界面中，重点关注按钮、输入框、卡片、容器等交互元素的样式优化，以及动画和过渡效果的改进。设计将保持现有的布局结构不变，仅更新组件的视觉样式和交互反馈。

## 架构

### 技术栈
- Vue 3 (Composition API)
- Element Plus (UI 组件库)
- Vite (构建工具)
- CSS3 (样式实现)

### 样式架构
采用以下方式实现 VSCode 风格：

1. **全局样式变量**: 创建 CSS 变量文件，定义 VSCode 风格的颜色、尺寸、动画参数
2. **Element Plus 主题覆盖**: 通过 CSS 覆盖 Element Plus 组件的默认样式
3. **自定义样式类**: 为特定组件创建 VSCode 风格的样式类
4. **过渡动画**: 统一使用 CSS transition 实现流畅的交互效果

## 组件和接口

### 1. 全局样式变量 (vscode-theme.css)

创建全局 CSS 变量文件，定义 VSCode 风格的设计令牌：

```css
:root {
  /* VSCode 颜色 */
  --vscode-button-primary-bg: #0e639c;
  --vscode-button-primary-hover: #1177bb;
  --vscode-button-primary-active: #005a9e;
  --vscode-button-secondary-bg: transparent;
  --vscode-button-secondary-border: #3e3e42;
  --vscode-button-secondary-hover-bg: rgba(255, 255, 255, 0.1);
  
  --vscode-input-bg: #3c3c3c;
  --vscode-input-border: #3e3e42;
  --vscode-input-focus-border: #007acc;
  --vscode-input-placeholder: #6e6e6e;
  
  --vscode-card-bg: #252526;
  --vscode-card-border: #3e3e42;
  --vscode-card-hover-border: #007acc;
  
  --vscode-dropdown-bg: #252526;
  --vscode-dropdown-border: #3e3e42;
  --vscode-dropdown-hover-bg: #2a2d2e;
  --vscode-dropdown-selected-bg: #094771;
  
  --vscode-dialog-bg: #252526;
  --vscode-dialog-header-bg: #2d2d30;
  --vscode-dialog-border: #3e3e42;
  --vscode-dialog-overlay: rgba(0, 0, 0, 0.5);
  
  --vscode-table-header-bg: #2d2d30;
  --vscode-table-border: #3e3e42;
  --vscode-table-hover-bg: #2a2d2e;
  --vscode-table-selected-bg: #094771;
  
  --vscode-tag-success-bg: #1e5c1e;
  --vscode-tag-warning-bg: #8b6914;
  --vscode-tag-error-bg: #8b1e1e;
  --vscode-tag-info-bg: #1e5c8b;
  --vscode-tag-text: #cccccc;
  
  --vscode-progress-bg: #3e3e42;
  --vscode-progress-fill: #007acc;
  
  /* VSCode 尺寸 */
  --vscode-border-radius-sm: 2px;
  --vscode-border-radius-md: 4px;
  --vscode-border-radius-lg: 6px;
  
  --vscode-padding-sm: 4px 8px;
  --vscode-padding-md: 6px 16px;
  --vscode-padding-lg: 8px 20px;
  
  --vscode-shadow-sm: 0 1px 3px rgba(0, 0, 0, 0.2);
  --vscode-shadow-md: 0 2px 8px rgba(0, 0, 0, 0.3);
  --vscode-shadow-lg: 0 4px 16px rgba(0, 0, 0, 0.4);
  
  /* VSCode 动画 */
  --vscode-transition-fast: 100ms ease-in-out;
  --vscode-transition-normal: 200ms ease-in-out;
  --vscode-transition-slow: 300ms ease-in-out;
}
```

### 2. 按钮样式覆盖

#### 主按钮 (Primary Button)
```css
.el-button--primary {
  background: var(--vscode-button-primary-bg);
  border: none;
  border-radius: var(--vscode-border-radius-sm);
  padding: var(--vscode-padding-md);
  transition: all var(--vscode-transition-fast);
}

.el-button--primary:hover {
  background: var(--vscode-button-primary-hover);
}

.el-button--primary:active {
  background: var(--vscode-button-primary-active);
}

.el-button--primary.is-disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
```

#### 次要按钮 (Default Button)
```css
.el-button--default {
  background: var(--vscode-button-secondary-bg);
  border: 1px solid var(--vscode-button-secondary-border);
  border-radius: var(--vscode-border-radius-sm);
  padding: var(--vscode-padding-md);
  transition: all var(--vscode-transition-fast);
}

.el-button--default:hover {
  background: var(--vscode-button-secondary-hover-bg);
}
```

#### 小按钮
```css
.el-button--small {
  padding: var(--vscode-padding-sm);
  border-radius: var(--vscode-border-radius-sm);
}
```

### 3. 输入框样式覆盖

```css
.el-input__wrapper {
  background: var(--vscode-input-bg);
  border: 1px solid var(--vscode-input-border);
  border-radius: var(--vscode-border-radius-sm);
  padding: var(--vscode-padding-sm);
  box-shadow: none;
  transition: all var(--vscode-transition-normal);
}

.el-input__wrapper:hover {
  border-color: var(--vscode-input-focus-border);
}

.el-input__wrapper.is-focus {
  border-color: var(--vscode-input-focus-border);
  box-shadow: 0 0 0 1px var(--vscode-input-focus-border);
}

.el-input__inner::placeholder {
  color: var(--vscode-input-placeholder);
}

/* 键盘焦点样式 */
.el-input__wrapper:focus-visible {
  outline: 1px solid var(--vscode-input-focus-border);
  outline-offset: -1px;
}
```

### 4. 下拉菜单和选择器样式

```css
.el-select-dropdown {
  background: var(--vscode-dropdown-bg);
  border: 1px solid var(--vscode-dropdown-border);
  border-radius: var(--vscode-border-radius-md);
  box-shadow: var(--vscode-shadow-md);
}

.el-select-dropdown__item {
  padding: var(--vscode-padding-sm);
  transition: background var(--vscode-transition-fast);
}

.el-select-dropdown__item:hover {
  background: var(--vscode-dropdown-hover-bg);
}

.el-select-dropdown__item.selected {
  background: var(--vscode-dropdown-selected-bg);
}

/* 日期选择器 */
.el-date-picker {
  background: var(--vscode-dropdown-bg);
  border: 1px solid var(--vscode-dropdown-border);
  box-shadow: var(--vscode-shadow-md);
}
```

### 5. 卡片和面板样式

```css
.el-card {
  background: var(--vscode-card-bg);
  border: 1px solid var(--vscode-card-border);
  border-radius: var(--vscode-border-radius-md);
  box-shadow: var(--vscode-shadow-sm);
  transition: all var(--vscode-transition-normal);
}

.el-card__header {
  background: var(--vscode-dialog-header-bg);
  border-bottom: 1px solid var(--vscode-card-border);
  padding: 16px;
}

.el-card__body {
  padding: 16px;
}

/* 可交互卡片 */
.el-card.is-hoverable:hover {
  border-color: var(--vscode-card-hover-border);
  box-shadow: var(--vscode-shadow-md);
}
```

### 6. 对话框样式

```css
.el-dialog {
  background: var(--vscode-dialog-bg);
  border: 1px solid var(--vscode-dialog-border);
  border-radius: var(--vscode-border-radius-lg);
  box-shadow: var(--vscode-shadow-lg);
}

.el-dialog__header {
  background: var(--vscode-dialog-header-bg);
  border-bottom: 1px solid var(--vscode-dialog-border);
  padding: 12px 20px;
  border-radius: var(--vscode-border-radius-lg) var(--vscode-border-radius-lg) 0 0;
}

.el-dialog__body {
  padding: 20px;
}

.el-dialog__footer {
  padding: 12px 20px;
  border-top: 1px solid var(--vscode-dialog-border);
}

/* 遮罩层 */
.el-overlay {
  background: var(--vscode-dialog-overlay);
  transition: opacity var(--vscode-transition-normal);
}

/* 对话框动画 */
.el-dialog {
  animation: dialogFadeIn 150ms ease-in-out;
}

@keyframes dialogFadeIn {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}
```

### 7. 表格样式

```css
.el-table {
  background: transparent;
}

.el-table__header-wrapper {
  background: var(--vscode-table-header-bg);
}

.el-table th {
  background: var(--vscode-table-header-bg);
  border-bottom: 1px solid var(--vscode-table-border);
}

.el-table td {
  border-bottom: 1px solid var(--vscode-table-border);
}

.el-table__row:hover {
  background: var(--vscode-table-hover-bg);
  transition: background var(--vscode-transition-fast);
}

.el-table__row.current-row {
  background: var(--vscode-table-selected-bg);
}

/* 表格单元格内边距 */
.el-table th,
.el-table td {
  padding: 8px 12px;
}
```

### 8. 标签和徽章样式

```css
.el-tag {
  border-radius: var(--vscode-border-radius-sm);
  padding: 2px 6px;
  border: none;
}

.el-tag--success {
  background: var(--vscode-tag-success-bg);
  color: var(--vscode-tag-text);
}

.el-tag--warning {
  background: var(--vscode-tag-warning-bg);
  color: var(--vscode-tag-text);
}

.el-tag--danger {
  background: var(--vscode-tag-error-bg);
  color: var(--vscode-tag-text);
}

.el-tag--info {
  background: var(--vscode-tag-info-bg);
  color: var(--vscode-tag-text);
}

/* 徽章 */
.el-badge__content {
  border-radius: var(--vscode-border-radius-sm);
}
```

### 9. 加载和进度指示器

```css
.el-progress-bar__outer {
  background: var(--vscode-progress-bg);
  border-radius: 1px;
  height: 2px;
}

.el-progress-bar__inner {
  background: var(--vscode-progress-fill);
  border-radius: 1px;
  transition: width var(--vscode-transition-slow);
}

/* 加载旋转器 */
.el-loading-spinner .circular {
  color: var(--vscode-progress-fill);
}

/* 骨架屏 */
.el-skeleton__item {
  background: var(--vscode-table-header-bg);
}

.el-skeleton__item::after {
  background: linear-gradient(
    90deg,
    transparent,
    var(--vscode-card-border),
    transparent
  );
}
```

### 10. 悬停和焦点状态

```css
/* 全局焦点样式 */
*:focus-visible {
  outline: 1px solid var(--vscode-input-focus-border);
  outline-offset: -1px;
}

/* 移除鼠标点击时的焦点轮廓 */
*:focus:not(:focus-visible) {
  outline: none;
}

/* 可交互元素悬停 */
.clickable,
button,
a,
[role="button"] {
  transition: all var(--vscode-transition-fast);
  cursor: pointer;
}

.clickable:hover,
button:hover,
a:hover,
[role="button"]:hover {
  opacity: 0.9;
}
```

## 数据模型

本设计不涉及数据模型的变更，仅修改视觉样式。

## 错误处理

样式应用过程中的错误处理：

1. **CSS 变量回退**: 为所有 CSS 变量提供回退值
   ```css
   background: var(--vscode-card-bg, #252526);
   ```

2. **浏览器兼容性**: 使用 autoprefixer 自动添加浏览器前缀

3. **样式隔离**: 使用 scoped 样式避免全局污染

4. **渐进增强**: 确保在不支持某些 CSS 特性的浏览器中仍能正常显示

## 测试策略

### 视觉回归测试
1. 对比改造前后的页面截图
2. 验证所有交互元素的悬停、焦点、激活状态
3. 测试不同屏幕尺寸下的显示效果

### 交互测试
1. 验证所有按钮的点击反馈
2. 测试输入框的焦点状态和输入体验
3. 检查对话框的打开/关闭动画
4. 验证下拉菜单的展开/收起效果

### 性能测试
1. 使用 Chrome DevTools 测量动画性能
2. 确保 CSS 过渡不会导致页面卡顿
3. 验证首屏渲染时间没有明显增加

### 可访问性测试
1. 使用键盘导航测试所有交互元素
2. 验证焦点指示器的可见性
3. 使用屏幕阅读器测试表单元素

## 实现细节

### 文件结构
```
frontend/src/
├── styles/
│   ├── vscode-theme.css          # VSCode 主题变量
│   ├── vscode-overrides.css      # Element Plus 样式覆盖
│   └── vscode-animations.css     # 动画和过渡效果
├── main.js                        # 导入全局样式
└── views/                         # 各个页面组件
```

### 样式导入顺序
在 `main.js` 中按以下顺序导入样式：

```javascript
import 'element-plus/dist/index.css'
import './styles/vscode-theme.css'
import './styles/vscode-overrides.css'
import './styles/vscode-animations.css'
```

### 组件级样式
对于需要特殊样式的组件，在组件的 `<style scoped>` 中使用 CSS 变量：

```vue
<style scoped>
.custom-card {
  background: var(--vscode-card-bg);
  border: 1px solid var(--vscode-card-border);
  border-radius: var(--vscode-border-radius-md);
  transition: all var(--vscode-transition-normal);
}

.custom-card:hover {
  border-color: var(--vscode-card-hover-border);
}
</style>
```

### 动画性能优化
1. 使用 `transform` 和 `opacity` 进行动画，避免触发重排
2. 为动画元素添加 `will-change` 属性（谨慎使用）
3. 使用 CSS `transition` 而非 JavaScript 动画
4. 限制同时进行的动画数量

### 浏览器兼容性
- 支持 Chrome 90+, Firefox 88+, Safari 14+, Edge 90+
- 使用 PostCSS 和 Autoprefixer 处理浏览器前缀
- 为不支持 CSS 变量的浏览器提供回退方案

## 设计决策

### 为什么选择 CSS 变量？
- 易于维护和更新主题
- 支持运行时动态修改
- 良好的浏览器支持
- 便于未来扩展多主题支持

### 为什么覆盖 Element Plus 样式？
- 保持组件功能不变，仅修改视觉样式
- 避免重写组件逻辑
- 利用 Element Plus 的可访问性特性
- 减少开发和维护成本

### 为什么使用 CSS transition 而非 JavaScript 动画？
- 更好的性能（GPU 加速）
- 更简洁的代码
- 更容易维护
- 符合 VSCode 的简洁风格

### 为什么保持现有布局？
- 减少用户学习成本
- 降低开发风险
- 专注于视觉体验提升
- 避免影响现有功能

## 后续优化

1. **主题切换**: 实现深色/浅色主题切换功能
2. **自定义主题**: 允许用户自定义颜色方案
3. **动画配置**: 提供动画速度和效果的配置选项
4. **无障碍增强**: 进一步优化键盘导航和屏幕阅读器支持
5. **性能监控**: 添加性能监控，持续优化动画性能
