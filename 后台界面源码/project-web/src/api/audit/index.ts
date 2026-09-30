import http from '@/http'
import type { AuditPageParm } from './AuditModel'
export const getListApi = (parm: AuditPageParm) => http.get('/api/audit/list', parm)
