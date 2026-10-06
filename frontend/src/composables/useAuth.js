import { ref } from 'vue'
import * as api from '../api/client.js'

// Module-level singleton state — no Pinia for an app this small (agreed plan).
// accessToken lives in memory only (lost on page reload, by design — demo
// tradeoff, see README.md). refreshToken persists in sessionStorage so a
// reload can silently re-establish a session via bootstrap() below.
const SESSION_KEY = 'bookstore_refresh_token'

const accessToken = ref(null)
const userId = ref(null)
const role = ref(null) // decoded from the access token's `role` claim
const name = ref(null) // only ever populated by /register's response
const email = ref(null) // best-effort display fallback after a plain /login
// (LoginResponse carries no name/email, and there is no GET /me endpoint —
// see README.md "Known gaps" for why the header can't show a true username
// after login.)

function decodeJwt(jwt) {
  try {
    const payload = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    return JSON.parse(atob(payload))
  } catch {
    return {}
  }
}

function setSession(accessTok, refreshTok, uId) {
  accessToken.value = accessTok
  userId.value = uId
  role.value = decodeJwt(accessTok).role ?? null
  if (refreshTok) sessionStorage.setItem(SESSION_KEY, refreshTok)
}

function clearSession() {
  accessToken.value = null
  userId.value = null
  role.value = null
  name.value = null
  email.value = null
  sessionStorage.removeItem(SESSION_KEY)
}

async function doRegister(payload) {
  const res = await api.register(payload)
  name.value = res.name
  setSession(res.accessToken, res.refreshToken, res.userId)
  return res
}

async function doLogin(payload) {
  const res = await api.login(payload)
  email.value = payload.email
  setSession(res.accessToken, res.refreshToken, res.userId)
  return res
}

function logout() {
  clearSession()
}

/**
 * Silent refresh on app load: if a refresh token survived in sessionStorage
 * (same tab/session), exchange it for a fresh access token so a page reload
 * doesn't force a re-login. Returns whether a session was restored.
 */
async function bootstrap() {
  const rt = sessionStorage.getItem(SESSION_KEY)
  if (!rt) return false
  try {
    const res = await api.refresh(rt)
    const decoded = decodeJwt(res.accessToken)
    accessToken.value = res.accessToken
    role.value = decoded.role ?? null
    userId.value = decoded.sub ?? null
    return true
  } catch {
    clearSession()
    return false
  }
}

/**
 * Calls an api/client.js function that takes (token, ...args), injecting the
 * current access token. On a 401 (expired access token), performs exactly
 * one silent /refresh + retry before giving up — matches the backend's
 * no-rotation refresh design (architecture-plan.md §5.14). Any other error,
 * or a failed retry, propagates to the caller.
 */
async function authedCall(fn, ...args) {
  try {
    return await fn(accessToken.value, ...args)
  } catch (err) {
    if (!(err instanceof api.ApiError) || err.status !== 401) throw err
    const restored = await bootstrap()
    if (!restored) throw err
    try {
      return await fn(accessToken.value, ...args)
    } catch (retryErr) {
      if (retryErr instanceof api.ApiError && retryErr.status === 401) clearSession()
      throw retryErr
    }
  }
}

export function useAuth() {
  return {
    accessToken,
    userId,
    role,
    name,
    email,
    isAuthenticated: () => accessToken.value !== null,
    register: doRegister,
    login: doLogin,
    logout,
    bootstrap,
    authedCall,
  }
}
