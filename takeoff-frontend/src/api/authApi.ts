import { apiClient } from './client'
import type {
  AdminHealth,
  ChangePasswordRequest,
  DriverProfile,
  JwtResponse,
  LoginRequest,
  OtpResendResponse,
  OtpVerifyRequest,
  RegistrationResponse,
  ResendOtpRequest,
  SignUpRequest,
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

export const driverApi = {
  getProfile: () => apiClient.get<DriverProfile>('/drivers/profile').then((r) => r.data),
}

export const adminApi = {
  getHealth: () => apiClient.get<AdminHealth>('/admin/health').then((r) => r.data),

  /** Resolves with nothing on success (204). A wrong current password is a 400 with a `currentPassword` field error. */
  changePassword: (payload: ChangePasswordRequest) =>
    apiClient.put('/admin/account/password', payload).then(() => undefined),
}
