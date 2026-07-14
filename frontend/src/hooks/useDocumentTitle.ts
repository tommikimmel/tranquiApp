import { useEffect } from 'react'

// Every route rendered the same static <title> from index.html — this keeps document.title
// (and therefore what shows in a browser tab / history entry) in sync with the page actually
// being viewed, without pulling in react-helmet for something this small.
export function useDocumentTitle(title: string) {
  useEffect(() => {
    const previous = document.title
    document.title = title
    return () => {
      document.title = previous
    }
  }, [title])
}
