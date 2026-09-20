import { useMemo } from 'react'
import { evaluatePassword, type PasswordEvaluation } from '../utils/passwordValidation'

/** Live evaluation of a password against the policy (memoized per value). */
export function usePasswordValidation(password: string): PasswordEvaluation {
  return useMemo(() => evaluatePassword(password), [password])
}
