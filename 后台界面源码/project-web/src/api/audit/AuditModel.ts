export type AuditPhase = 'RECEIVED' | 'CHANGED' | 'COMPLETED'
export interface AuditEvent {
  eventId: number
  requestId: string
  phase: AuditPhase
  userId: number | null
  method: string
  path: string
  remoteAddress: string
  httpStatus: number | null
  resultCode: number | null
  durationMs: number | null
  targetId: number | null
  details: string | null
  occurredAt: string
}
export interface AuditPageParm {
  currentPage: number
  pageSize: number
  requestId?: string
  userId?: number
  phase?: AuditPhase
}
