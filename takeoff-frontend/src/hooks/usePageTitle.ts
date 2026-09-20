import { useEffect } from 'react'

/** Sets the document title (announced by screen readers on navigation). */
export function usePageTitle(title: string) {
  useEffect(() => {
    document.title = `${title} | TakeOFF`
  }, [title])
}
