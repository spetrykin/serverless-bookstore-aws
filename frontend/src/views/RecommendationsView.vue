<script setup>
import { ref, onMounted } from 'vue'
import { useAuth } from '../composables/useAuth.js'
import * as api from '../api/client.js'
import Spinner from '../components/Spinner.vue'

const { authedCall } = useAuth()

const books = ref([])
const source = ref('')
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await authedCall(api.getRecommendations)
    books.value = res.books
    source.value = res.source
  } catch (err) {
    error.value = err.message ?? 'Failed to load recommendations.'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section>
    <h1>Recommended for you</h1>
    <p v-if="source" class="source-tag">
      {{ source === 'personalized' ? 'Based on your past orders' : 'Popular picks' }}
    </p>
    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="loading && books.length === 0" class="loading-block">
      <Spinner label="Loading recommendations…" />
    </div>
    <p v-else-if="books.length === 0 && !error">Nothing to recommend right now.</p>

    <div v-else class="book-grid">
      <article v-for="book in books" :key="book.bookId" class="book-card">
        <img v-if="book.photoUrl" :src="book.photoUrl" :alt="book.name" />
        <div v-else class="book-photo-placeholder" aria-hidden="true" />
        <h3>{{ book.name }}</h3>
        <p class="price">${{ (book.priceCents / 100).toFixed(2) }}</p>
      </article>
    </div>
  </section>
</template>
