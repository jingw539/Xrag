const storage = window.sessionStorage

export const getToken = () => {
  return storage.getItem('token')
}

export const setToken = (token) => {
  storage.setItem('token', token)
}

export const removeToken = () => {
  storage.removeItem('token')
}

export const getUserInfo = () => {
  const userInfo = storage.getItem('userInfo')
  return userInfo ? JSON.parse(userInfo) : null
}

export const setUserInfo = (userInfo) => {
  storage.setItem('userInfo', JSON.stringify(userInfo))
}

export const removeUserInfo = () => {
  storage.removeItem('userInfo')
}
