/** Wire types shared with the Spring Boot backend. Field names must match the backend DTOs exactly. */

export type Role = 'APPLICANT_DRIVER' | 'LOGISTICS_ADMIN'

export interface UserSummary {
  id: number
  fullName: string
  email: string
  phoneNumber: string
  role: Role
  phoneVerified: boolean
  /** True while the password is a temporary one an administrator issued: the person must choose their own first. */
  mustChangePassword?: boolean
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

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

/** An account as an administrator sees it (no secrets). */
export interface AdminUser {
  id: number
  fullName: string
  email: string
  phoneNumber: string
  role: Role
  phoneVerified: boolean
  enabled: boolean
  mustChangePassword: boolean
  temporaryPasswordExpiresAt: string | null
  createdAt: string
}

export interface CreateAccountRequest {
  fullName: string
  email: string
  phoneNumber: string
  role: Role
}

/** A freshly issued temporary password. The server shows it once; it is never returned again. */
export interface IssuedCredential {
  user: AdminUser
  temporaryPassword: string
  temporaryPasswordExpiresAt: string
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

// ---------------------------------------------------------------- Driver application

export type ApplicationStatus = 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED'
export type DocumentType = 'DRIVERS_LICENCE' | 'VEHICLE_REGISTRATION' | 'INSURANCE'
export type VehicleType = 'MOTORCYCLE' | 'CAR' | 'VAN' | 'PICKUP' | 'TRUCK'

export interface PersonalDetails {
  dateOfBirth: string | null
  addressLine: string | null
  city: string | null
  emergencyContactName: string | null
  emergencyContactPhone: string | null
}

export interface IdentityDetails {
  nationalId: string | null
  licenceNumber: string | null
  licenceClass: string | null
  licenceExpiry: string | null
}

export interface VehicleDetails {
  vehicleType: VehicleType | null
  plateNumber: string | null
  make: string | null
  model: string | null
}

export interface DocumentInfo {
  type: DocumentType
  filename: string
  contentType: string
  sizeBytes: number
  uploadedAt: string
}

export interface ApplicationProgress {
  personal: boolean
  identity: boolean
  vehicle: boolean
  documents: boolean
  readyToSubmit: boolean
}

export interface Application {
  id: number
  referenceId: string | null
  status: ApplicationStatus
  editable: boolean
  personal: PersonalDetails
  identity: IdentityDetails
  vehicle: VehicleDetails
  documents: DocumentInfo[]
  progress: ApplicationProgress
  submittedAt: string | null
  decidedAt: string | null
  decisionNote: string | null
}

export interface PersonalRequest {
  dateOfBirth: string
  addressLine: string
  city: string
  emergencyContactName: string
  emergencyContactPhone: string
}

export interface IdentityRequest {
  nationalId: string
  licenceNumber: string
  licenceClass: string
  licenceExpiry: string
}

export interface VehicleRequest {
  vehicleType: VehicleType
  plateNumber: string
  make: string
  model: string
}

export interface NotificationItem {
  id: number
  type: string
  title: string
  message: string
  read: boolean
  createdAt: string
}

export interface NotificationInbox {
  items: NotificationItem[]
  unreadCount: number
}

// ---------------------------------------------------------------- Admin review

export interface ApplicationSummaryRow {
  id: number
  referenceId: string | null
  status: ApplicationStatus
  driverName: string
  driverEmail: string
  driverPhone: string
  plateNumber: string | null
  submittedAt: string | null
  decidedAt: string | null
}

export interface PageOf<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface DriverInfo {
  id: number
  fullName: string
  email: string
  phoneNumber: string
  phoneVerified: boolean
  registeredAt: string | null
}

export interface ApplicationDetail {
  application: Application
  driver: DriverInfo
}

export interface AdminSummary {
  pendingReview: number
  approved: number
  rejected: number
  totalSubmitted: number
}

export interface DecisionRequest {
  status: 'APPROVED' | 'REJECTED'
  note?: string
}