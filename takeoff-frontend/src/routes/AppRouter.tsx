import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { Skeleton } from '../components/ui/Skeleton'
import { ProtectedRoute } from './ProtectedRoute'

// Route-level code splitting keeps the first load small.
const LoginPage = lazy(() => import('../pages/LoginPage'))
const RegisterPage = lazy(() => import('../pages/RegisterPage'))
const OtpVerificationPage = lazy(() => import('../pages/OtpVerificationPage'))
const DriverDashboardPage = lazy(() => import('../pages/DriverDashboardPage'))
const DriverApplicationPage = lazy(() => import('../pages/DriverApplicationPage'))
const DriverNotificationsPage = lazy(() => import('../pages/DriverNotificationsPage'))
const AdminDashboardPage = lazy(() => import('../pages/AdminDashboardPage'))
const AdminApplicationsPage = lazy(() => import('../pages/AdminApplicationsPage'))
const AdminApplicationDetailPage = lazy(() => import('../pages/AdminApplicationDetailPage'))
const AdminSettingsPage = lazy(() => import('../pages/AdminSettingsPage'))
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
        {/* One sign-in form for everyone at "/". The old login URLs still work and lead here. */}
        <Route path="/" element={<LoginPage />} />
        <Route path="/login" element={<Navigate to="/" replace />} />
        <Route path="/admin/login" element={<Navigate to="/" replace />} />

        <Route path="/register" element={<RegisterPage />} />
        <Route path="/verify-otp" element={<OtpVerificationPage />} />

        <Route element={<ProtectedRoute role="APPLICANT_DRIVER" />}>
          <Route path="/driver/dashboard" element={<DriverDashboardPage />} />
          <Route path="/driver/application" element={<DriverApplicationPage />} />
          <Route path="/driver/notifications" element={<DriverNotificationsPage />} />
        </Route>
        <Route element={<ProtectedRoute role="LOGISTICS_ADMIN" />}>
          <Route path="/admin/dashboard" element={<AdminDashboardPage />} />
          <Route path="/admin/applications" element={<AdminApplicationsPage />} />
          <Route path="/admin/applications/:id" element={<AdminApplicationDetailPage />} />
          <Route path="/admin/settings" element={<AdminSettingsPage />} />
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  )
}
