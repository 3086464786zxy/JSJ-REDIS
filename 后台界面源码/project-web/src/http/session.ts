/** 后端 Redis 会话是最终依据，前端计时用于及时退出页面。 */
type SessionState = { sid: string; lastActivity: number; acknowledgedAt: number; idleTimeoutMs: number }
const STATE_KEY = 'itmk:session'
const ACTIVITY_PREFIX = 'itmk:activity:'
const SYNC_INTERVAL_MS = 60_000
let state: SessionState | null = null
let acknowledgedAt = 0
let lastSyncAt = 0
let syncing = false
let installed = false
let heartbeat: () => Promise<unknown>
let onExpired: () => void

function sessionId(token: string): string {
  try {
    const encoded = token.split('.')[1]
    if (!encoded) return ''
    const payload = JSON.parse(atob(encoded.replace(/-/g, '+').replace(/_/g, '/')))
    return typeof payload.sid === 'string' ? payload.sid : ''
  } catch {
    return ''
  }
}

function persist() {
  if (state) sessionStorage.setItem(STATE_KEY, JSON.stringify(state))
}

function readSharedActivity() {
  if (!state) return
  const shared = Number(localStorage.getItem(ACTIVITY_PREFIX + state.sid))
  if (Number.isFinite(shared) && shared > state.lastActivity && shared <= Date.now()) {
    state.lastActivity = shared
    persist()
  }
}

export function isSessionIdle(): boolean {
  readSharedActivity()
  return !state || Date.now() - state.lastActivity >= state.idleTimeoutMs
}

export function startSession(token: string, idleTimeoutSeconds: number) {
  const sid = sessionId(token)
  if (!sid || !Number.isFinite(idleTimeoutSeconds) || idleTimeoutSeconds <= 0) throw new Error('登录会话信息无效')
  state = { sid, lastActivity: Date.now(), acknowledgedAt: Date.now(), idleTimeoutMs: idleTimeoutSeconds * 1000 }
  acknowledgedAt = state.lastActivity
  lastSyncAt = 0
  persist()
  localStorage.setItem(ACTIVITY_PREFIX + sid, String(state.lastActivity))
}

export function clearSession() {
  if (state) localStorage.removeItem(ACTIVITY_PREFIX + state.sid)
  state = null
  acknowledgedAt = 0
  sessionStorage.removeItem(STATE_KEY)
}

/** 只标记尚未同步的真实交互，自动轮询不能无限续期。 */
export function pendingActivity(): number {
  readSharedActivity()
  return state && !isSessionIdle() && state.lastActivity > acknowledgedAt ? state.lastActivity : 0
}

export function acknowledgeActivity(activityAt: number) {
  if (state) {
    acknowledgedAt = Math.max(acknowledgedAt, activityAt)
    state.acknowledgedAt = acknowledgedAt
    persist()
  }
}

function checkIdle() {
  if (state && isSessionIdle()) onExpired()
}

async function syncActivity() {
  if (!state || syncing || !pendingActivity() || Date.now() - lastSyncAt < SYNC_INTERVAL_MS) return
  syncing = true
  lastSyncAt = Date.now()
  try {
    await heartbeat()
  } catch {
    // 网络异常不重置闲置计时；认证失败由共享 HTTP 层处理。
  } finally {
    syncing = false
  }
}

function recordActivity(event: Event) {
  if (!event.isTrusted || !state || document.visibilityState !== 'visible') return
  // 休眠恢复后的第一次点击不能重新激活已经超时的会话。
  if (isSessionIdle()) { onExpired(); return }
  const now = Date.now()
  if (now - state.lastActivity < 1000) return
  state.lastActivity = now
  persist()
  localStorage.setItem(ACTIVITY_PREFIX + state.sid, String(now))
  void syncActivity()
}

export function installSessionActivity(token: string, sendHeartbeat: () => Promise<unknown>, expire: () => void) {
  heartbeat = sendHeartbeat
  onExpired = expire
  try {
    const saved = JSON.parse(sessionStorage.getItem(STATE_KEY) || 'null') as SessionState | null
    if (saved && saved.sid === sessionId(token) && Number.isFinite(saved.lastActivity)
        && saved.lastActivity <= Date.now() && Number.isFinite(saved.idleTimeoutMs) && saved.idleTimeoutMs > 0) {
      state = saved
      acknowledgedAt = Number.isFinite(saved.acknowledgedAt) ? Math.min(saved.acknowledgedAt, saved.lastActivity) : 0
    }
  } catch {
    sessionStorage.removeItem(STATE_KEY)
  }
  if (token && !state) expire()
  if (installed) return
  installed = true
  for (const event of ['pointerdown', 'pointermove', 'keydown', 'input', 'wheel', 'touchstart']) {
    document.addEventListener(event, recordActivity, { passive: true })
  }
  document.addEventListener('visibilitychange', checkIdle)
  window.addEventListener('focus', checkIdle)
  window.addEventListener('storage', (event) => {
    if (state && event.key === ACTIVITY_PREFIX + state.sid) {
      if (event.newValue === null) onExpired()
      else checkIdle()
    }
  })
  window.setInterval(() => { checkIdle(); void syncActivity() }, 1000)
  checkIdle()
}
