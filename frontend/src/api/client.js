// Thin fetch wrapper over the backend, built directly against the schemas in
// openapi.yaml (Stretch §7.1 item 3) — shapes noted in JSDoc, no codegen.
// Requests go to /api/* which vite.config.js proxies to VITE_API_BASE
// server-side, sidestepping the backend's missing CorsConfiguration.

const BASE = '/api'

export class ApiError extends Error {
  constructor(status, code, message) {
    super(message || code || `Request failed with status ${status}`)
    this.status = status
    this.code = code
  }
}

/**
 * @param {string} path
 * @param {{ method?: string, token?: string|null, body?: unknown }} [opts]
 */
async function request(path, { method = 'GET', token, body } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  const text = await res.text()
  const data = text ? JSON.parse(text) : null

  if (!res.ok) {
    // ErrorResponse: { code, message }
    throw new ApiError(res.status, data?.code, data?.message)
  }
  return data
}

// ---- Auth (security: [] — no token needed) --------------------------------

/**
 * @param {{name:string,email:string,password:string,confirmPassword:string,birthday:string,gender:string}} payload
 * @returns {Promise<{userId:string,name:string,accessToken:string,refreshToken:string}>}
 */
export function register(payload) {
  return request('/register', { method: 'POST', body: payload })
}

/**
 * @param {{email:string,password:string}} payload
 * @returns {Promise<{userId:string,accessToken:string,refreshToken:string}>}
 */
export function login(payload) {
  return request('/login', { method: 'POST', body: payload })
}

/**
 * @param {string} refreshToken
 * @returns {Promise<{accessToken:string}>}
 */
export function refresh(refreshToken) {
  return request('/refresh', { method: 'POST', body: { refreshToken } })
}

// ---- Catalog ----------------------------------------------------------------

/**
 * @param {string} token
 * @param {string|null} [cursor]
 * @returns {Promise<{items:Array<{bookId:string,name:string,priceCents:number,count:number,photoUrl:string,visible:boolean}>,nextCursor?:string|null}>}
 */
export function listBooks(token, cursor) {
  const qs = cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''
  return request(`/books${qs}`, { token })
}

// ---- Orders -------------------------------------------------------------------

/**
 * @param {string} token
 * @param {Array<{bookId:string,quantity:number}>} lines
 * @returns {Promise<{orderId:string,lines:Array,totalCents:number,createdAt:string}>}
 */
export function placeOrder(token, lines) {
  return request('/orders', { method: 'POST', token, body: { lines } })
}

/**
 * @param {string} token
 * @param {string|null} [cursor]
 * @returns {Promise<{items:Array,nextCursor?:string|null}>}
 */
export function listOrders(token, cursor) {
  const qs = cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''
  return request(`/orders${qs}`, { token })
}

// ---- Recommendations -----------------------------------------------------------

/**
 * @param {string} token
 * @returns {Promise<{books:Array,source:'personalized'|'fallback'}>}
 */
export function getRecommendations(token) {
  return request('/recommendations', { token })
}
