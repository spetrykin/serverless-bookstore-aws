<script setup>
import { ref, onMounted } from 'vue'
import { useAuth } from '../composables/useAuth.js'
import * as api from '../api/client.js'
import Spinner from '../components/Spinner.vue'

const { authedCall } = useAuth()

const orders = ref([])
const nextCursor = ref(null)
const loading = ref(false)
const error = ref('')

async function load(cursor) {
  loading.value = true
  error.value = ''
  try {
    const page = await authedCall(api.listOrders, cursor ?? null)
    orders.value = cursor ? [...orders.value, ...page.items] : page.items
    nextCursor.value = page.nextCursor ?? null
  } catch (err) {
    error.value = err.message ?? 'Failed to load orders.'
  } finally {
    loading.value = false
  }
}

function bookSummary(order) {
  return order.lines.map((l) => `${l.name} ×${l.quantity}`).join(', ')
}

onMounted(() => load(null))
</script>

<template>
  <section>
    <h1>Your orders</h1>
    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="loading && orders.length === 0" class="loading-block">
      <Spinner label="Loading orders…" />
    </div>
    <p v-else-if="orders.length === 0">No orders yet.</p>

    <table v-else class="table">
      <thead>
        <tr>
          <th>ID</th>
          <th>Books</th>
          <th>Price</th>
          <th>Date</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="order in orders" :key="order.orderId">
          <td>{{ order.orderId }}</td>
          <td>{{ bookSummary(order) }}</td>
          <td>${{ (order.totalCents / 100).toFixed(2) }}</td>
          <td>{{ new Date(order.createdAt).toLocaleString() }}</td>
        </tr>
      </tbody>
    </table>

    <button v-if="nextCursor" class="secondary" :disabled="loading" @click="load(nextCursor)">
      <Spinner v-if="loading && orders.length > 0" size="sm" label="Loading more…" />
      Next
    </button>
  </section>
</template>
