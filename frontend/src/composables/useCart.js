import { reactive, computed } from 'vue'

// Module-level singleton, in-memory only — the cart is ephemeral by design,
// same as accessToken (useAuth.js): a reload starts a fresh cart, no
// persistence layer added for a demo cart.
const lines = reactive([]) // { bookId, name, priceCents, quantity, maxCount }

function addToCart(book) {
  const existing = lines.find((l) => l.bookId === book.bookId)
  if (existing) {
    if (existing.quantity < book.count) existing.quantity++
  } else {
    lines.push({
      bookId: book.bookId,
      name: book.name,
      priceCents: book.priceCents,
      quantity: 1,
      maxCount: book.count,
    })
  }
}

function removeFromCart(bookId) {
  const idx = lines.findIndex((l) => l.bookId === bookId)
  if (idx !== -1) lines.splice(idx, 1)
}

function setQuantity(bookId, quantity) {
  const line = lines.find((l) => l.bookId === bookId)
  if (line) line.quantity = Math.max(1, Math.min(quantity, line.maxCount))
}

function clearCart() {
  lines.splice(0, lines.length)
}

const totalCents = computed(() => lines.reduce((sum, l) => sum + l.priceCents * l.quantity, 0))
const itemCount = computed(() => lines.reduce((sum, l) => sum + l.quantity, 0))

export function useCart() {
  return { lines, addToCart, removeFromCart, setQuantity, clearCart, totalCents, itemCount }
}
