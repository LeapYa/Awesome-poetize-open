/**
 * Session Validation for Admin Frontend
 * Validates the user session by calling the backend /user/token endpoint.
 * Since auth has migrated to HttpOnly cookies, we no longer read tokens from localStorage.
 */
import Vue from 'vue'
import constant from './constant'
import { useMainStore } from '../stores/main'

const SESSION_FRESH_MS = 2 * 60 * 1000
const SESSION_VALIDATE_TIMEOUT_MS = 20 * 1000

const sessionState = Vue.observable({
  status: 'unknown',
  verifiedAt: 0,
  inFlight: null,
  navigationPending: false,
  contentLoadingCount: 0,
})

function clearValidatedUser() {
  const mainStore = useMainStore()
  mainStore.loadCurrentUser({})
  mainStore.loadCurrentAdmin({})
}

function fetchWithTimeout(url, options = {}, timeoutMs = SESSION_VALIDATE_TIMEOUT_MS) {
  const controller = new AbortController()
  const timeoutId = window.setTimeout(() => controller.abort(), timeoutMs)

  return fetch(url, {
    ...options,
    signal: controller.signal,
  }).finally(() => {
    window.clearTimeout(timeoutId)
  })
}

function applyValidatedUser(userData) {
  const mainStore = useMainStore()
  mainStore.loadCurrentUser(userData)
  if (userData && (userData.userType === 0 || userData.userType === 1)) {
    mainStore.loadCurrentAdmin(userData)
  } else {
    mainStore.loadCurrentAdmin({})
  }
}

async function validateSessionWithServer() {
  const response = await fetchWithTimeout(`${constant.baseURL}/user/token`, {
    method: 'POST',
    credentials: 'include',
  })

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }

  const result = await response.json()
  if (!result || result.code !== 200 || !result.data) {
    throw new Error(result?.message || '登录已过期，请重新登录')
  }

  applyValidatedUser(result.data)
  markSessionVerified(Date.now())
  return true
}

export function markSessionVerified(verifiedAt = Date.now()) {
  sessionState.status = 'valid'
  sessionState.verifiedAt = verifiedAt
}

export function resetSessionValidation(options = {}) {
  const { status = 'unknown', clearStore = false } = options
  sessionState.status = status
  sessionState.verifiedAt = 0
  sessionState.inFlight = null
  sessionState.navigationPending = false
  sessionState.contentLoadingCount = 0

  if (clearStore) {
    clearValidatedUser()
  }
}

export function isSessionFresh() {
  return Date.now() - sessionState.verifiedAt < SESSION_FRESH_MS
}

export function getSessionState() {
  return sessionState
}

export function beginSessionValidation() {
  if (sessionState.status !== 'valid') {
    sessionState.status = 'validating'
  }
}

export function setAdminNavigationPending(pending) {
  sessionState.navigationPending = pending
}

export function setAdminContentLoading(loading) {
  if (loading) {
    sessionState.contentLoadingCount += 1
    return
  }
  sessionState.contentLoadingCount = Math.max(0, sessionState.contentLoadingCount - 1)
}

export function hasStoredSessionHint() {
  const mainStore = useMainStore()
  return (
    Boolean(mainStore.currentUser && Object.keys(mainStore.currentUser).length > 0) ||
    Boolean(mainStore.currentAdmin && Object.keys(mainStore.currentAdmin).length > 0)
  )
}

export async function ensureSessionValid(options = {}) {
  const { force = false } = options

  if (!force && isSessionFresh()) {
    sessionState.status = 'valid'
    return true
  }

  if (sessionState.inFlight) {
    return sessionState.inFlight
  }

  beginSessionValidation()

  const validationPromise = validateSessionWithServer()
    .catch(() => {
      resetSessionValidation({ status: 'invalid', clearStore: true })
      return false
    })
    .finally(() => {
      sessionState.inFlight = null
    })

  sessionState.inFlight = validationPromise
  return validationPromise
}
