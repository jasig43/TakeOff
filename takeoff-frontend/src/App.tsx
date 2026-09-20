import { BrowserRouter } from 'react-router-dom'
import { MotionConfig } from 'framer-motion'
import { AuthProvider } from './context/AuthContext'
import { ToastProvider } from './context/ToastContext'
import { AppRouter } from './routes/AppRouter'

export default function App() {
  return (
    // reducedMotion="user": every Framer Motion transform/layout animation is skipped
    // when the operating system asks for reduced motion.
    <MotionConfig reducedMotion="user">
      <ToastProvider>
        <AuthProvider>
          <BrowserRouter>
            <AppRouter />
          </BrowserRouter>
        </AuthProvider>
      </ToastProvider>
    </MotionConfig>
  )
}
