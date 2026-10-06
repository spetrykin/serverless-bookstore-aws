<script setup>
import { useAuth } from '../composables/useAuth.js'
import { useCart } from '../composables/useCart.js'

defineProps({ view: String })
const emit = defineEmits(['navigate', 'logout'])

// Destructured (not kept as `auth.xxx`) so the refs auto-unwrap in the
// template — see frontend/README.md if this pattern needs explaining.
const { name, email } = useAuth()
const { itemCount } = useCart()

// Single source of truth for the nav; keys match App.vue's `view` values.
const NAV_ITEMS = [
  { key: 'catalog', label: 'Catalog' },
  { key: 'order', label: 'Cart' },
  { key: 'orders', label: 'Orders' },
  { key: 'recommendations', label: 'Recommendations' },
]
</script>

<template>
  <header class="app-header">
    <span class="greeting">Hello, {{ name ?? email ?? 'there' }}</span>
    <nav>
      <button
        v-for="item in NAV_ITEMS"
        :key="item.key"
        :class="{ active: view === item.key }"
        @click="emit('navigate', item.key)"
      >
        {{ item.label }}<template v-if="item.key === 'order'"> ({{ itemCount }})</template>
      </button>
      <button class="logout" @click="emit('logout')">Logout</button>
    </nav>
  </header>
</template>
