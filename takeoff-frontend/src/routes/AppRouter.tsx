import { lazy, Suspense } from 'react'
import { Route, Routes } from 'react-router-dom'
import { Skeleton } from '../components/ui/Skeleton'
import { ProtectedRoute } from './ProtectedRoute'

// Route-level code splitting keeps the landing page bundle small.
const LandingPage = lazy(() => import('../pages/LandingPage'))
const RegisterPage = lazy(() => import('../pages/RegisterPage'))
const OtpVerificationPage = lazy(() => import('../pages/OtpVerificationPage'))
const LoginPage = lazy(() => import('../pages/LoginPage'))
const AdminLoginPage = lazy(() => import('../pages/AdminLoginPage'))
const DriverDashboardPage = lazy(() => import('../pages/DriverDashboardPage'))
const AdminDashboardPage = lazy(() => import('../pages/AdminDashboardPage'))
const NotFoundPage = lazy(() => import('../pages/NotFoundPage'))

function RouteFallback() {
  return (
    <div role="status" aria-label="Loading page" className="mx-auto max-w-3xl space-y-4 px-6 py-24">
      <Skeleton className="h-10 w-2/3" />
      <Skeleton className="h-6 w-full" />
      <Skeleton className="h-6 w-5/6" />
    </div>
  )
}

export function AppRouter() {
  return (
    <Suspense fallback={<RouteFallback />}>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/verify-otp" element={<OtpVerificationPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/admin/login" element={<AdminLoginPage />} />

        <Route element={<ProtectedRoute role="APPLICANT_DRIVER" />}>
          <Route path="/driver/dashboard" element={<DriverDashboardPage />} />
        </Route>
        <Route element={<ProtectedRoute role="LOGISTICS_ADMIN" />}>
          <Route path="/admin/dashboard" element={<AdminDashboardPage />} />
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  )
}
