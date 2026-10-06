<script setup>
import { reactive, ref } from 'vue'
import { useAuth } from '../composables/useAuth.js'
import { ApiError } from '../api/client.js'
import Spinner from '../components/Spinner.vue'

const emit = defineEmits(['authed', 'go-login'])
const { register } = useAuth()

// gender is a plain free-text field, deliberately — RegisterRequest.gender
// has no fixed allow-list (architecture-plan.md §5.13), so no <select> here.
const form = reactive({
  name: '',
  email: '',
  password: '',
  confirmPassword: '',
  birthday: '',
  gender: '',
})
const error = ref('')
const submitting = ref(false)

async function onSubmit() {
  error.value = ''
  submitting.value = true
  try {
    await register({ ...form })
    emit('authed')
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : 'Something went wrong. Please try again.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-screen">
    <form class="card" @submit.prevent="onSubmit">
      <h1>Sign up</h1>
      <label>Username <input v-model="form.name" required /></label>
      <label>Password <input v-model="form.password" type="password" required minlength="8" /></label>
      <label>Confirm password <input v-model="form.confirmPassword" type="password" required /></label>
      <label>Email <input v-model="form.email" type="email" required /></label>
      <label>Birthday <input v-model="form.birthday" type="date" required /></label>
      <label>Gender <input v-model="form.gender" required /></label>
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit" :disabled="submitting">
        <Spinner v-if="submitting" size="sm" label="Submitting…" />
        Submit
      </button>
      <p class="switch">Have an account? <a href="#" @click.prevent="emit('go-login')">Login</a></p>
    </form>
  </div>
</template>
