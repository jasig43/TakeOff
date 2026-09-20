import { apiClient } from './client'
import type {
  AdminHealth,
  AdminUser,
  ChangePasswordRequest,
  CreateAccountRequest,
  DriverProfile,
  IssuedCredential,
  JwtResponse,
  LoginRequest,
  OtpResendResponse,
  OtpVerifyRequest,
  PageOf,
  RegistrationResponse,
  ResendOtpRequest,
  Role,
  SignUpRequest,
  UserSummary,
} from './types'

export const authApi = {
  register: (payload: SignUpRequest) =>
    apiClient.post<RegistrationResponse>('/auth/register', payload).then((r) => r.data),

  verifyOtp: (payload: OtpVerifyRequest) =>
    apiClient.post<JwtResponse>('/auth/verify-otp', payload).then((r) => r.data),

  resendOtp: (payload: ResendOtpRequest) =>
    apiClient.post<OtpResendResponse>('/auth/resend-otp', payload).then((r) => r.data),

  login: (payload: LoginRequest) => apiClient.post<JwtResponse>('/auth/login', payload).then((r) => r.data),
}

/** The signed-in person's own account, for any role. */
export const accountApi = {
  /**
   * Resolves with the refreshed account summary (its `mustChangePassword` is now false). A wrong current password is a
   * 400 with a `currentPassword` field error, not a 401, so it never signs the person out.
   */
  changePassword: (payload: ChangePasswordRequest) =>
    apiClient.put<UserSummary>('/account/password', payload).then((r) => r.data),
}

export const driverApi = {
  getProfile: () => apiClient.get<DriverProfile>('/drivers/profile').then((r) => r.data),
}

export interface UserQuery {
  q?: string
  page?: number
  size?: number
}

export const adminApi = {
  getHealth: () => apiClient.get<AdminHealth>('/admin/health').then((r) => r.data),

  listUsers: ({ q, page = 0, size = 10 }: UserQuery) =>
    apiClient.get<PageOf<AdminUser>>('/admin/users', { params: { q: q || undefined, page, size } }).then((r) => r.data),

  createUser: (payload: CreateAccountRequest) =>
    apiClient.post<IssuedCredential>('/admin/users', payload).then((r) => r.data),

  setUserRole: (id: number, role: Role) =>
    apiClient.patch<AdminUser>(`/admin/users/${id}/role`, { role }).then((r) => r.data),

  /** Replaces the person's password with a new temporary one, which is returned once. */
  issueTemporaryPassword: (id: number) =>
    apiClient.post<IssuedCredential>(`/admin/users/${id}/temporary-password`).then((r) => r.data),
}
