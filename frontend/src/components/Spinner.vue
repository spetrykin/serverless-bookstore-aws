<script setup>
// Minimal CSS-only spinner — no icon library, matches the "no UI kit" call.
// `sm` for inline use next to button labels (label stays screen-reader-only,
// the button's own text already says what's happening), `md` (default) for a
// standalone block replacing not-yet-loaded content (label shown visibly).
defineProps({
  size: { type: String, default: 'md' }, // 'sm' | 'md'
  label: { type: String, default: 'Loading…' },
  showLabel: { type: Boolean, default: undefined }, // defaults to (size === 'md')
})
</script>

<template>
  <span class="spinner" :class="size" role="status">
    <span class="spinner-ring" aria-hidden="true" />
    <span :class="{ 'sr-only': showLabel === false || (showLabel === undefined && size !== 'md') }">
      {{ label }}
    </span>
  </span>
</template>
