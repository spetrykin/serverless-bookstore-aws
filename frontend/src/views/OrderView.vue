<script setup>
import { ref } from 'vue'
import { useAuth } from '../composables/useAuth.js'
import { useCart } from '../composables/useCart.js'
import * as api from '../api/client.js'
import Spinner from '../components/Spinner.vue'

const emit = defineEmits(['placed', 'back'])
const { authedCall } = useAuth()
const { lines, removeFromCart, setQuantity, clearCart, totalCents } = useCart()

const error = ref('')
const submitting = ref(false)

async function placeOrder() {
  error.value = ''
  submitting.value = true
  try {
    const orderLines = lines.map((l) => ({ bookId: l.bookId, quantity: l.quantity }))
    await authedCall(api.placeOrder, orderLines)
    clearCart()
    emit('placed')
  } catch (err) {
    // 404 (book gone/hidden) and 409 (insufficient stock) both carry a
    // usable message — see openapi.yaml's POST /orders responses.
    error.value = err.message ?? 'Failed to place order.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section>
    <h1>Your order</h1>

    <p v-if="lines.length === 0">
      Your cart is empty. <a href="#" @click.prevent="emit('back')">Back to catalog</a>
    </p>

    <template v-else>
      <table class="table">
        <thead>
          <tr>
            <th>Book</th>
            <th>Price</th>
            <th>Qty</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="line in lines" :key="line.bookId">
            <td>{{ line.name }}</td>
            <td>${{ (line.priceCents / 100).toFixed(2) }}</td>
            <td>
              <input
                type="number"
                min="1"
                :max="line.maxCount"
                :value="line.quantity"
                @change="setQuantity(line.bookId, Number($event.target.value))"
              />
            </td>
            <td><button class="secondary" @click="removeFromCart(line.bookId)">Remove</button></td>
          </tr>
        </tbody>
      </table>

      <p class="total">Total: ${{ (totalCents / 100).toFixed(2) }}</p>
      <p v-if="error" class="error">{{ error }}</p>
      <button :disabled="submitting" @click="placeOrder">
        <Spinner v-if="submitting" size="sm" label="Placing order…" />
        Place order
      </button>
    </template>
  </section>
</template>
