<script setup>
import { ref } from 'vue'
import { useAuth } from '../composables/useAuth.js'
import { ApiError } from '../api/client.js'
import Spinner from '../components/Spinner.vue'

const emit = defineEmits(['authed', 'go-register'])
const { login } = useAuth()

const email = ref('')
const password = ref('')
const error = ref('')
const submitting = ref(false)

async function onSubmit() {
  error.value = ''
  submitting.value = true
  try {
    await login({ email: email.value, password: password.value })
    emit('authed')
  } catch (err) {
    // 401 (bad credentials) and 403 (blocked) both carry a usable message —
    // see openapi.yaml's /login responses.
    error.value = err instanceof ApiError ? err.message : 'Something went wrong. Please try again.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-screen">
    <form class="card" @submit.prevent="onSubmit">
      <h1>Login</h1>
      <label>
        Email
        <input v-model="email" type="email" required autocomplete="email" />
      </label>
      <label>
        Password
        <input v-model="password" type="password" required autocomplete="current-password" />
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit" :disabled="submitting">
        <Spinner v-if="submitting" size="sm" label="Logging in…" />
        LOGIN
      </button>
      <p class="switch">No account? <a href="#" @click.prevent="emit('go-register')">Register</a></p>
    </form>
  </div>
</template>
