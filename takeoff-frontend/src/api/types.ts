/** Wire types shared with the Spring Boot backend. Field names must match the backend DTOs exactly. */

export type Role = 'APPLICANT_DRIVER' | 'LOGISTICS_ADMIN'

export interface UserSummary {
  id: number
  fullName: string
  email: string
  phoneNumber: string
  role: Role
  phoneVerified: boolean
}

export interface SignUpRequest {
  fullName: string
  email: string
  phoneNumber: string
  password: string
  termsAccepted: boolean
}

export interface RegistrationResponse {
  email: string
  maskedPhone: string
  otpExpiresInSeconds: number
  /** False when the OTP event could not be queued; the user can use "Resend code". */
  otpDispatched: boolean
  message: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface OtpVerifyRequest {
  /** Email address or E.164 phone number of the account being verified. */
  identifier: string
  otp: string
}

export interface ResendOtpRequest {
  identifier: string
}

export interface OtpResendResponse {
  otpExpiresInSeconds: number
  otpDispatched: boolean
  message: string
}

export interface JwtResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresInSeconds: number
  user: UserSummary
}

export interface DriverProfile {
  id: number
  fullName: string
  email: string
  phoneNumber: string
  phoneVerified: boolean
  role: Role
  createdAt: string
}

export interface AdminHealth {
  status: string
  message: string
  role: Role
  timestamp: string
}

export interface ApiFieldError {
  field: string
  message: string
}

export interface ApiErrorBody {
  timestamp: string
  status: number
  error: string
  code: string
  message: string
  path: string
  fieldErrors?: ApiFieldError[]
}
