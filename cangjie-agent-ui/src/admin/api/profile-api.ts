import { request } from '@shared/api/http'
import type { UserIdentity } from '@shared/types'

export interface ProfileUpdateDTO {
  nickname?: string
  email?: string
  phone?: string
}

export interface PasswordChangeDTO {
  oldPassword: string
  newPassword: string
}

export const profileApi = {
  get() {
    return request<UserIdentity>({ method: 'GET', url: '/profile' })
  },
  update(data: ProfileUpdateDTO) {
    return request<UserIdentity>({ method: 'PUT', url: '/profile', data })
  },
  changePassword(data: PasswordChangeDTO) {
    return request<void>({ method: 'PUT', url: '/profile/password', data })
  }
}
