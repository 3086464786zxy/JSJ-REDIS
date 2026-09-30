import { writeFileSync } from 'node:fs'
// Requires an authenticated staging account; never bypass login or captcha.
const base=process.env.LOAD_BASE_URL
const token=process.env.LOAD_TOKEN
if(!base || !token) throw new Error('Set LOAD_BASE_URL and LOAD_TOKEN for a dedicated staging session')
const parsed=new URL(base)
if(!['http:','https:'].includes(parsed.protocol)) throw new Error('HTTP(S) URL required')
const vus=Number(process.env.LOAD_VUS || 10)
const seconds=Number(process.env.LOAD_SECONDS || 30)
const p95Limit=Number(process.env.LOAD_P95_MS || 500)
const errorLimit=Number(process.env.LOAD_ERROR_RATE || 0.01)
if(!Number.isInteger(vus)||vus<1||vus>500||seconds<1||seconds>3600||p95Limit<=0||errorLimit<0||errorLimit>1) throw new Error('Invalid load limits')
const paths=['/api/sysUser/getUserInfo','/api/sysMenu/getMenuList','/api/sysUser/list?currentPage=1&pageSize=10']
const latencies=[]
const statuses={}
let failures=0
const started=performance.now(),deadline=started+seconds*1000
const headers={Authorization:`Bearer ${token.replace(/^Bearer /,'')}`,'X-Session-Activity':'1'}
await Promise.all(Array.from({length:vus},async(_,user)=>{
  let iteration=user
  while(performance.now()<deadline) {
    const before=performance.now()
    try {
      const response=await fetch(new URL(paths[iteration++%paths.length],base),{headers,signal:AbortSignal.timeout(10000)})
      statuses[response.status]=(statuses[response.status]||0)+1
      const body=await response.json()
      if(!response.ok||body.code!==200) failures++
    } catch { failures++; statuses.network=(statuses.network||0)+1 }
    latencies.push(performance.now()-before)
  }
}))
latencies.sort((a,b)=>a-b)
const elapsed=performance.now()-started
const percentile=p=>latencies[Math.max(0,Math.ceil(latencies.length*p)-1)]||0
const report={timestamp:new Date().toISOString(),virtualUsers:vus,durationMs:Math.round(elapsed),requests:latencies.length,
  requestsPerSecond:Number((latencies.length*1000/elapsed).toFixed(2)),failures,errorRate:failures/latencies.length,
  p50Ms:Number(percentile(.5).toFixed(2)),p95Ms:Number(percentile(.95).toFixed(2)),p99Ms:Number(percentile(.99).toFixed(2)),statuses,
  thresholds:{p95Ms:p95Limit,errorRate:errorLimit}}
report.passed=report.p95Ms<p95Limit&&report.errorRate<=errorLimit
if(process.env.LOAD_REPORT) writeFileSync(process.env.LOAD_REPORT,JSON.stringify(report,null,2)+'\n',{mode:0o600})
console.log(JSON.stringify(report,null,2))
if(!report.passed) process.exitCode=1
