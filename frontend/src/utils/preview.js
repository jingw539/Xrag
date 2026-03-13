const storage = window.sessionStorage
const PREVIEW_TOKEN_KEY = 'previewToken'

export const loadPreviewToken = () => {
  const params = new URLSearchParams(window.location.search)
  const token = params.get('preview_token')
  if (token) {
    storage.setItem(PREVIEW_TOKEN_KEY, token)
    return token
  }
  return storage.getItem(PREVIEW_TOKEN_KEY) || ''
}

export const getPreviewToken = () => storage.getItem(PREVIEW_TOKEN_KEY) || ''

export const isPreviewMode = () => Boolean(getPreviewToken())
