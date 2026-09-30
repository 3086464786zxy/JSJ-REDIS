import http from 'k6/http'
import { check, sleep } from 'k6'
if(!__ENV.LOAD_BASE_URL || !__ENV.LOAD_TOKEN) throw new Error('LOAD_BASE_URL and LOAD_TOKEN are required')
export const options={
  stages:[{duration:'30s',target:10},{duration:'2m',target:30},{duration:'30s',target:0}],
  thresholds:{http_req_failed:['rate<0.01'],http_req_duration:['p(95)<500'],checks:['rate>0.99'],dropped_iterations:['count==0']},
}
export default function() {
  const headers={Authorization:`Bearer ${__ENV.LOAD_TOKEN.replace(/^Bearer /,'')}`,'X-Session-Activity':'1'}
  for(const path of ['/api/sysUser/getUserInfo','/api/sysMenu/getMenuList','/api/sysUser/list?currentPage=1&pageSize=10']) {
    const response=http.get(`${__ENV.LOAD_BASE_URL}${path}`,{headers})
    check(response,{'HTTP and business success':r=>r.status===200&&r.json('code')===200})
  }
  sleep(1)
}
