<script setup>
import { ref, onMounted } from 'vue'
import { useAuth } from './composables/useAuth.js'
import AppHeader from './components/AppHeader.vue'
import Spinner from './components/Spinner.vue'
import LoginView from './views/LoginView.vue'
import RegisterView from './views/RegisterView.vue'
import CatalogView from './views/CatalogView.vue'
import OrderView from './views/OrderView.vue'
import OrdersListView from './views/OrdersListView.vue'
import RecommendationsView from './views/RecommendationsView.vue'

const { isAuthenticated, bootstrap, logout } = useAuth()

// 'login' | 'register' | 'catalog' | 'order' | 'orders' | 'recommendations'
const view = ref('login')
const bootstrapping = ref(true)

onMounted(async () => {
  const restored = await bootstrap()
  view.value = restored ? 'catalog' : 'login'
  bootstrapping.value = false
})

function onAuthed() {
  view.value = 'catalog'
}

function onLogout() {
  logout()
  view.value = 'login'
}
</script>

<template>
  <div v-if="bootstrapping" class="app-loading">
    <Spinner label="Loading your session…" />
  </div>

  <template v-else-if="!isAuthenticated()">
    <LoginView v-if="view === 'login'" @authed="onAuthed" @go-register="view = 'register'" />
    <RegisterView v-else @authed="onAuthed" @go-login="view = 'login'" />
  </template>

  <template v-else>
    <AppHeader :view="view" @navigate="view = $event" @logout="onLogout" />
    <main class="container">
      <CatalogView v-if="view === 'catalog'" @go-order="view = 'order'" />
      <OrderView v-else-if="view === 'order'" @placed="view = 'orders'" @back="view = 'catalog'" />
      <OrdersListView v-else-if="view === 'orders'" />
      <RecommendationsView v-else-if="view === 'recommendations'" />
    </main>
  </template>
</template>
