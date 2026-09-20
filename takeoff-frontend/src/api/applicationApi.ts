import { apiClient } from './client'
import type {
  AdminSummary,
  Application,
  ApplicationDetail,
  ApplicationStatus,
  ApplicationSummaryRow,
  DecisionRequest,
  DocumentType,
  IdentityRequest,
  NotificationInbox,
  PageOf,
  PersonalRequest,
  VehicleRequest,
} from './types'

/** Everything a driver does with their own application. */
export const applicationApi = {
  get: () => apiClient.get<Application>('/drivers/application').then((r) => r.data),

  savePersonal: (body: PersonalRequest) =>
    apiClient.put<Application>('/drivers/application/personal', body).then((r) => r.data),

  saveIdentity: (body: IdentityRequest) =>
    apiClient.put<Application>('/drivers/application/identity', body).then((r) => r.data),

  saveVehicle: (body: VehicleRequest) =>
    apiClient.put<Application>('/drivers/application/vehicle', body).then((r) => r.data),

  uploadDocument: (type: DocumentType, file: File) => {
    const form = new FormData()
    form.append('file', file)
    // Let the browser set the multipart boundary; the default JSON header must not be forced here.
    return apiClient
      .post<Application>(`/drivers/application/documents/${type}`, form, { headers: { 'Content-Type': undefined } })
      .then((r) => r.data)
  },

  deleteDocument: (type: DocumentType) =>
    apiClient.delete<Application>(`/drivers/application/documents/${type}`).then((r) => r.data),

  /** Documents need the bearer token, so they are fetched as a blob rather than linked to directly. */
  documentBlob: (type: DocumentType) =>
    apiClient.get<Blob>(`/drivers/application/documents/${type}`, { responseType: 'blob' }).then((r) => r.data),

  submit: () => apiClient.post<Application>('/drivers/application/submit').then((r) => r.data),
}

export const notificationApi = {
  inbox: () => apiClient.get<NotificationInbox>('/drivers/notifications').then((r) => r.data),
  markRead: (id: number) => apiClient.post<NotificationInbox>(`/drivers/notifications/${id}/read`).then((r) => r.data),
  markAllRead: () => apiClient.post<NotificationInbox>('/drivers/notifications/read-all').then((r) => r.data),
}

export interface ApplicationQuery {
  status?: ApplicationStatus
  q?: string
  page?: number
  size?: number
}

/** The administrator's review workflow. */
export const reviewApi = {
  summary: () => apiClient.get<AdminSummary>('/admin/summary').then((r) => r.data),

  list: ({ status, q, page = 0, size = 10 }: ApplicationQuery) =>
    apiClient
      .get<PageOf<ApplicationSummaryRow>>('/admin/applications', { params: { status, q: q || undefined, page, size } })
      .then((r) => r.data),

  get: (id: number) => apiClient.get<ApplicationDetail>(`/admin/applications/${id}`).then((r) => r.data),

  documentBlob: (id: number, type: DocumentType) =>
    apiClient.get<Blob>(`/admin/applications/${id}/documents/${type}`, { responseType: 'blob' }).then((r) => r.data),

  decide: (id: number, body: DecisionRequest) =>
    apiClient.patch<ApplicationDetail>(`/admin/applications/${id}/status`, body).then((r) => r.data),
}
