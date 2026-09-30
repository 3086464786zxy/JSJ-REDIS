import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'

// Only for disposable CI/test databases. Captcha is seeded by the test harness,
// without adding any bypass to the application's production login API.
assert.equal(process.env.VALIDATION_DATABASE,'jsj_reliability_tests')
const base=new URL(process.env.LOAD_BASE_URL)
const management=new URL(process.env.VALIDATION_MANAGEMENT_URL)
for (const url of [base,management]) assert.equal(url.hostname,'127.0.0.1')
for(let attempt=0;;attempt++) {
  try {
    const response=await fetch(new URL('/actuator/health/readiness',management),{signal:AbortSignal.timeout(2000)})
    assert.equal(response.status,200)
    assert.equal((await response.json()).status,'UP')
    break
  } catch(error) {
    if(attempt>=99) throw error
    await new Promise(resolve=>setTimeout(resolve,250))
  }
}
const metrics=await fetch(new URL('/actuator/prometheus',management))
assert.equal(metrics.status,200)
assert.match(await metrics.text(),/itmk_audit_incomplete/)
const protectedMetrics=await fetch(new URL('/actuator/prometheus',base))
assert.equal(protectedMetrics.status,401)
const login=await fetch(new URL('/api/sysUser/login',base),{
  method:'POST',headers:{'Content-Type':'application/json','X-Requested-With':'XMLHttpRequest'},
  body:JSON.stringify({username:process.env.VALIDATION_USERNAME,password:process.env.VALIDATION_PASSWORD,
    captchaId:process.env.VALIDATION_CAPTCHA_ID,code:process.env.VALIDATION_CAPTCHA_CODE})
})
assert.equal(login.status,200)
const session=await login.json()
assert.equal(session.code,200)
assert.ok(session.data.token)
const load=spawnSync(process.execPath,['ops/load/http-load.mjs'],{
  env:{...process.env,LOAD_TOKEN:session.data.token},encoding:'utf8'
})
process.stdout.write(load.stdout||'')
assert.equal(load.status,0,'Authenticated load thresholds failed')
const audit=await fetch(new URL('/api/audit/list?currentPage=1&pageSize=10',base),{
  headers:{Authorization:`Bearer ${session.data.token}`}
})
assert.equal(audit.status,200)
assert.equal((await audit.json()).code,200)
console.log('PASS: readiness, private metrics, real login, authenticated load and audit query')
