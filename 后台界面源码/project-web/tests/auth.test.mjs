import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import vm from 'node:vm'
import ts from 'typescript'
import axios, { AxiosError } from 'axios'

const flush = () => new Promise((resolve) => setImmediate(resolve))
const tokenFor = (sid, signature = 'old') => 'header.' + Buffer.from(JSON.stringify({ sid })).toString('base64url') + '.' + signature
function storage(seed = []) {
  const values = new Map(seed)
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: (key) => values.delete(key),
    clear: () => values.clear(),
    values,
  }
}

async function harness(server = async () => ({ code: 200, data: {} }), saved = []) {
  let now = 1_000_000
  const events = new Map()
  const timers = []
  const redirects = []
  const messages = []
  const requests = []
  const sessionStorage = storage(saved)
  const localStorage = storage()
  const store = {
    token: tokenFor('session'),
    get getToken() { return this.token },
    setToken(value) { this.token = value },
    $reset() { this.token = '' },
  }
  const document = {
    visibilityState: 'visible',
    addEventListener: (type, callback) => events.set(type, callback),
  }
  const window = {
    addEventListener: (type, callback) => events.set(type, callback),
    setInterval: (callback) => { timers.push(callback); return timers.length },
    location: { replace: (url) => redirects.push(url) },
  }
  class Clock extends Date { static now() { return now } }
  const context = vm.createContext({ document, window, sessionStorage, localStorage, Date: Clock,
    atob, navigator: { locks: { request: async (_name, callback) => callback() } }, console })
  const adapter = async (config) => {
    requests.push(config)
    const data = await server(config, requests)
    if (data instanceof Error) throw data
    return { data, status: 200, statusText: 'OK', headers: {}, config }
  }
  const axiosMock = {
    create: (config) => axios.create({ ...config, adapter }),
    post: (url, data, config) => axios.create({ adapter }).post(url, data, config),
    isAxiosError: axios.isAxiosError,
  }
  const synthetic = (exports) => new vm.SyntheticModule(Object.keys(exports), function () {
    for (const [name, value] of Object.entries(exports)) this.setExport(name, value)
  }, { context })
  const modules = {
    axios: synthetic({ default: axiosMock }),
    'element-plus': synthetic({ ElMessage: {
      error: (message) => messages.push(message), warning: (message) => messages.push(message),
    } }),
    '@/store/user': synthetic({ useUserStore: () => store }),
  }
  async function source(path) {
    const code = ts.transpileModule(await readFile(new URL(path, import.meta.url), 'utf8'), {
      compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
    }).outputText
    const module = new vm.SourceTextModule(code, { context })
    await module.link((specifier) => modules[specifier])
    await module.evaluate()
    return module.namespace
  }
  const session = await source('../src/http/session.ts')
  modules['./session'] = synthetic(Object.fromEntries(Object.keys(session).map((key) => [key, session[key]])))
  const http = await source('../src/http/index.ts')
  session.installSessionActivity(saved.length ? store.token : '',
    () => http.default.post('/api/session/activity'), () => http.endSession('idle'))
  if (!saved.length) session.startSession(store.token, 1800)
  return { session, http, store, requests, redirects, messages, sessionStorage, localStorage, events,
    advance: (ms) => { now += ms },
    activity: () => events.get('pointerdown')({ isTrusted: true }),
    tick: () => { for (const callback of timers) callback() },
    hide: () => { document.visibilityState = 'hidden' },
  }
}

test('无操作达到 30 分钟，清空会话并跳转登录', async () => {
  const h = await harness()
  h.advance(1_800_000)
  h.tick()
  await flush()
  assert.equal(h.store.token, '')
  assert.deepEqual(h.redirects, ['/login'])
  assert.equal(h.requests.filter((r) => r.url === '/api/refresh').length, 0)
})

test('真实交互持续续期，不存在最长登录时间', async () => {
  const h = await harness()
  for (let i = 0; i < 800; i++) {
    h.advance(20 * 60_000)
    h.activity()
    await flush()
    assert.equal(h.session.isSessionIdle(), false)
  }
  assert.equal(h.redirects.length, 0)
  assert.ok(h.store.token)
})

test('后台轮询和隐藏页面不会延长闲置计时', async () => {
  const h = await harness()
  h.advance(900_000)
  await h.http.default.get('/api/poll')
  assert.equal(h.requests[0].headers.get('X-Session-Activity'), undefined)
  h.hide()
  h.activity()
  h.advance(900_000)
  h.tick()
  await flush()
  assert.deepEqual(h.redirects, ['/login'])
})

test('休眠超过闲置时限后的第一次点击不能恢复会话', async () => {
  const h = await harness()
  h.advance(1_800_001)
  h.activity()
  await flush()
  assert.equal(h.store.token, '')
  assert.equal(h.session.pendingActivity(), 0)
})

test('最后一次操作在节流窗口结束后仍同步一次，之后不再空转续期', async () => {
  const h = await harness()
  h.advance(1000)
  h.activity()
  await flush()
  h.advance(1000)
  h.activity()
  await flush()
  assert.equal(h.requests.length, 1)
  h.advance(60_000)
  h.tick()
  await flush()
  assert.equal(h.requests.length, 2)
  h.advance(60_000)
  h.tick()
  await flush()
  assert.equal(h.requests.length, 2)
})

test('页面重载保留未确认的操作，重载本身不算操作', async () => {
  const saved = [['itmk:session', JSON.stringify({
    sid: 'session', lastActivity: 999_000, acknowledgedAt: 998_000, idleTimeoutMs: 1_800_000,
  })]]
  const h = await harness(undefined, saved)
  assert.equal(h.session.pendingActivity(), 999_000)
  h.tick()
  await flush()
  assert.equal(h.session.pendingActivity(), 0)
  h.advance(1_799_000)
  h.tick()
  await flush()
  assert.equal(h.store.token, '')
})

test('同会话其他标签页的真实操作同步闲置时间', async () => {
  const h = await harness()
  h.advance(1_700_000)
  h.localStorage.setItem('itmk:activity:session', String(2_700_000))
  h.advance(200_000)
  assert.equal(h.session.isSessionIdle(), false)
  h.localStorage.removeItem('itmk:activity:session')
  h.events.get('storage')({ key: 'itmk:activity:session', newValue: null })
  await flush()
  assert.equal(h.store.token, '')
})

test('并发业务 600 响应只刷新一次，所有请求用新 Token 重试', async () => {
  const h = await harness(async (request) => {
    if (request.url === '/api/refresh') {
      await flush()
      return { code: 200, data: { accessToken: tokenFor('session', 'new') } }
    }
    if (request.headers.get('Authorization') === 'Bearer ' + tokenFor('session')) return { code: 600 }
    return { code: 200, data: { value: request.url } }
  })
  const results = await Promise.all([h.http.default.get('/api/a'), h.http.default.get('/api/b')])
  assert.deepEqual(results.map((r) => r.data.value), ['/api/a', '/api/b'])
  assert.equal(h.requests.filter((r) => r.url === '/api/refresh').length, 1)
  assert.equal(h.store.token, tokenFor('session', 'new'))
})

test('HTTP 401 自动刷新；重试仍失败时退出且不会无限刷新', async () => {
  const h = await harness(async (request) => {
    if (request.url === '/api/refresh') return { code: 200, data: { accessToken: tokenFor('session', 'new') } }
    if (request.url === '/api/sysUser/loginOut') return { code: 200 }
    throw new AxiosError('expired', 'ERR_BAD_REQUEST', request, null, { status: 401, config: request })
  })
  await assert.rejects(h.http.default.get('/api/protected'))
  await flush()
  assert.equal(h.requests.filter((r) => r.url === '/api/refresh').length, 1)
  assert.deepEqual(h.redirects, ['/login'])
})

test('RefreshToken 失效时清空 Pinia，刷新失败请求不会重发', async () => {
  const h = await harness(async (request) => request.url === '/api/refresh' ? { code: 401 } : { code: 600 })
  await assert.rejects(h.http.default.get('/api/protected'))
  await flush()
  assert.equal(h.store.token, '')
  assert.equal(h.requests.filter((r) => r.url === '/api/protected').length, 1)
})

test('刷新接口网络故障保留会话，网络恢复后可以继续刷新', async () => {
  let offline = true
  const h = await harness(async (request) => {
    if (request.url === '/api/refresh') {
      if (offline) throw new AxiosError('offline', 'ERR_NETWORK', request)
      return { code: 200, data: { accessToken: tokenFor('session', 'new') } }
    }
    return request.headers.get('Authorization') === 'Bearer ' + tokenFor('session') ? { code: 600 } : { code: 200 }
  })
  await assert.rejects(h.http.default.get('/api/protected'))
  assert.equal(h.store.token, tokenFor('session'))
  assert.equal(h.redirects.length, 0)
  offline = false
  assert.equal((await h.http.default.get('/api/protected')).code, 200)
})

test('无操作已超时时，不发送刷新请求', async () => {
  const h = await harness()
  h.advance(1_800_000)
  await assert.rejects(h.http.default.get('/api/protected'))
  await flush()
  assert.equal(h.requests.filter((r) => r.url === '/api/refresh').length, 0)
})

