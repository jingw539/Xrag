<template>
  <div class="virtual-list" :style="{ height: `${height}px` }" @scroll="handleScroll">
    <div class="virtual-spacer" :style="{ height: `${totalHeight}px` }">
      <div
        v-for="(item, i) in visibleItems"
        :key="getKey(item, startIndex + i)"
        class="virtual-item"
        :style="{ transform: `translateY(${(startIndex + i) * itemHeight}px)` }"
      >
        <slot :item="item" :index="startIndex + i" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watchEffect } from 'vue'

const props = defineProps({
  items: { type: Array, default: () => [] },
  itemHeight: { type: Number, required: true },
  height: { type: Number, required: true },
  overscan: { type: Number, default: 6 },
  keyField: { type: String, default: '' }
})

const scrollTop = ref(0)
const totalHeight = computed(() => props.items.length * props.itemHeight)

const startIndex = computed(() => (
  Math.max(0, Math.floor(scrollTop.value / props.itemHeight) - props.overscan)
))
const endIndex = computed(() => {
  const visibleCount = Math.ceil(props.height / props.itemHeight) + props.overscan * 2
  return Math.min(props.items.length, startIndex.value + visibleCount)
})
const visibleItems = computed(() => props.items.slice(startIndex.value, endIndex.value))

const handleScroll = (event) => {
  scrollTop.value = event.target.scrollTop
}

const getKey = (item, index) => {
  if (props.keyField && item && item[props.keyField] != null) return item[props.keyField]
  return index
}

watchEffect(() => {
  if (scrollTop.value > totalHeight.value) scrollTop.value = 0
})
</script>

<style scoped>
.virtual-list {
  overflow-y: auto;
  position: relative;
}

.virtual-spacer {
  position: relative;
  width: 100%;
}

.virtual-item {
  position: absolute;
  left: 0;
  right: 0;
  will-change: transform;
}
</style>
