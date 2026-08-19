import { useUserStore } from '@/admin/store/user'

export function hasPerm(code: string): boolean {
  const store = useUserStore()
  if (!store.permissions) return false
  return store.permissions.includes(code)
}

export function hasRole(role: string): boolean {
  const store = useUserStore()
  if (!store.userInfo) return false
  return store.userInfo.role === role
}

export function hasAnyPerm(codes: string[]): boolean {
  const store = useUserStore()
  if (!store.permissions) return false
  return codes.some(c => store.permissions!.includes(c))
}

export function hasAllPerm(codes: string[]): boolean {
  const store = useUserStore()
  if (!store.permissions) return false
  return codes.every(c => store.permissions!.includes(c))
}