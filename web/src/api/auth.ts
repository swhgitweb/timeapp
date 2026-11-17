import http from './http'

export interface UserVO {
  id: number
  username: string
  displayName: string
  timezone: string
}

export interface AuthStatus {
  initialized: boolean
  authenticated: boolean
  user: UserVO | null
}

export interface LoginPayload {
  username: string
  password: string
  displayName?: string
  timezone?: string
}

export function getAuthStatus(): Promise<AuthStatus> {
  return http.get('/auth/status')
}

export function login(payload: LoginPayload): Promise<UserVO> {
  return http.post('/auth/login', payload)
}

export function initializeAccount(payload: LoginPayload): Promise<UserVO> {
  return http.post('/auth/initialize', payload)
}

export function logout(): Promise<null> {
  return http.post('/auth/logout')
}
