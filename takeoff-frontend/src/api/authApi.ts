import { apiClient } from './client'
import type {
  AdminHealth,
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
}
