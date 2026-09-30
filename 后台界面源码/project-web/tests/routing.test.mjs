import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import ts from 'typescript'

async function harness({ offline = false, initialized = false } = {}) {
  let guard, userLoads = 0, menuLoads = 0
  const user = { getToken: 'valid', initialized, getCodeList: [],
    async getUserInfo() { userLoads++; if (offline) throw new Error('offline'); this.initialized = true } }
  const menu = { initialized, getMenu: [{}], async getMenuList() { menuLoads++; this.initialized = true } }
  const router = { beforeEach(callback) { guard = callback } }
  const context = vm.createContext({ console })
  const synthetic = (exports) => new vm.SyntheticModule(Object.keys(exports), function () {
    for (const [name, value] of Object.entries(exports)) this.setExport(name, value)
  }, { context })
  const modules = {
    './router': synthetic({ default: router }),
    './store/user': synthetic({ useUserStore: () => user }),
    './store/menu': synthetic({ useMenuStore: () => menu }),
  }
  const code = ts.transpileModule(await readFile(new URL('../src/permission.ts', import.meta.url), 'utf8'), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
  }).outputText
  const module = new vm.SourceTextModule(code, { context })
  await module.link((name) => modules[name]); await module.evaluate()
  return { user, navigate: async () => { let next; await guard({ path: '/dashboard' }, {}, (value) => { next = value }); return next },
    loads: () => [userLoads, menuLoads] }
}

test('没有业务权限的账户只初始化一次，不会陷入路由循环', async () => {
  const h = await harness()
  assert.equal((await h.navigate()).replace, true)
  assert.equal(await h.navigate(), undefined)
  assert.deepEqual(h.loads(), [1, 1])
})

test('已加载的空菜单和空权限不会重复查询', async () => {
  const h = await harness({ initialized: true })
  assert.equal(await h.navigate(), undefined)
  assert.deepEqual(h.loads(), [0, 0])
})

test('获取菜单时网络故障取消跳转并保留已有会话', async () => {
  const h = await harness({ offline: true })
  assert.equal(await h.navigate(), false)
  assert.equal(h.user.getToken, 'valid')
})

test('三级动态路由保留父子关系，目录使用 Center，子节点不重复注册', async () => {
  const registrations = []
  const Layout = {}, Center = {}, view = () => {}
  const context = vm.createContext({ Layout, Center, modules: { '../../views/page.vue': view } })
  const source = await readFile(new URL('../src/store/menu/index.ts', import.meta.url), 'utf8')
  const code = ts.transpileModule(source.slice(source.indexOf('export function generateRoute')), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
  }).outputText
  const module = new vm.SourceTextModule(code, { context })
  await module.link(() => { throw new Error('unexpected import') }); await module.evaluate()
  const routes = module.namespace.generateRoute([{ path: '/system', component: 'Layout', children: [
    { path: 'group', component: 'views/group.vue', children: [{ path: 'page', component: 'views/page.vue' }] },
  ] }], { addRoute: (route) => registrations.push(route) })
  assert.equal(registrations.length, 1)
  assert.equal(routes[0].component, Layout)
  assert.equal(routes[0].children[0].component, Center)
  assert.equal(routes[0].children[0].children[0].component, view)
})
