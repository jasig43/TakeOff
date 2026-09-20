import { useLocation } from 'react-router-dom'

/** Shows the current URL so tests can assert on the path and search params. */
export function LocationProbe() {
  const { pathname, search } = useLocation()
  return <p data-testid="location">{pathname + search}</p>
}
