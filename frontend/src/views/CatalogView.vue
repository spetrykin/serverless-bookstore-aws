<script setup>
import { ref, onMounted } from 'vue'
import { useAuth } from '../composables/useAuth.js'
import { useCart } from '../composables/useCart.js'
import * as api from '../api/client.js'
import Spinner from '../components/Spinner.vue'

const { authedCall } = useAuth()
const { addToCart, lines } = useCart()

const books = ref([])
const nextCursor = ref(null)
const loading = ref(false)
const error = ref('')

async function load(cursor) {
  loading.value = true
  error.value = ''
  try {
    const page = await authedCall(api.listBooks, cursor ?? null)
    books.value = page.items
    nextCursor.value = page.nextCursor ?? null
  } catch (err) {
    error.value = err.message ?? 'Failed to load books.'
  } finally {
    loading.value = false
  }
}

function quantityInCart(bookId) {
  return lines.find((l) => l.bookId === bookId)?.quantity ?? 0
}

onMounted(() => load(null))
</script>

<template>
  <section>
    <h1>Books</h1>
    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="loading && books.length === 0" class="loading-block">
      <Spinner label="Loading books…" />
    </div>
    <p v-else-if="books.length === 0">No books to show.</p>

    <div v-else class="book-grid">
      <article v-for="book in books" :key="book.bookId" class="book-card">
        <img v-if="book.photoUrl" :src="book.photoUrl" :alt="book.name" />
        <div v-else class="book-photo-placeholder" aria-hidden="true" />
        <h3>{{ book.name }}</h3>
        <p class="price">${{ (book.priceCents / 100).toFixed(2) }}</p>
        <p v-if="quantityInCart(book.bookId)" class="in-cart">{{ quantityInCart(book.bookId) }} in cart</p>
        <button v-if="book.count > 0" @click="addToCart(book)">+</button>
        <span v-else class="absent">ABSENT</span>
      </article>
    </div>

    <button v-if="nextCursor" class="secondary" :disabled="loading" @click="load(nextCursor)">
      <Spinner v-if="loading && books.length > 0" size="sm" label="Loading more…" />
      Next
    </button>
  </section>
</template>
