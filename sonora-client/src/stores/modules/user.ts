// 用户信息存储模块：负责登录状态与用户资料管理
import piniaPersistConfig from '@/stores/persist'
import { defineStore } from 'pinia'

export interface UserProfile {
  userId: number
  profileId?: string
  username?: string
  email?: string
  phone?: string
  nickname: string
  avatarUrl: string
  bio?: string
  status?: number
  vipType?: number
}

export const useUserStore = defineStore('user', {
  state: () => ({
    isLoggedIn: false,
    profile: null as UserProfile | null,
    accessToken: '',
    refreshToken: '',
    expires: '',
    lastLoginAt: 0,
  }),
  getters: {
    userId: state => state.profile?.userId || 0,
    profileId: state => state.profile?.profileId || '',
    username: state => state.profile?.username || '',
    email: state => state.profile?.email || '',
    phone: state => state.profile?.phone || '',
    nickname: state => state.profile?.nickname || '',
    avatarUrl: state => state.profile?.avatarUrl || '',
  },
  actions: {
    // 设置用户信息
    setUser(profile: UserProfile, tokens?: { accessToken?: string; refreshToken?: string; expires?: string }) {
      this.isLoggedIn = true
      this.profile = profile
      this.accessToken = tokens?.accessToken || this.accessToken
      this.refreshToken = tokens?.refreshToken || this.refreshToken
      this.expires = tokens?.expires || this.expires
      this.lastLoginAt = Date.now()
    },
    updateProfile(profile: UserProfile) {
      this.profile = profile
    },
    // 清理用户信息
    logout() {
      this.isLoggedIn = false
      this.profile = null
      this.accessToken = ''
      this.refreshToken = ''
      this.expires = ''
      this.lastLoginAt = 0
    },
  },
  persist: piniaPersistConfig('user', [
    'isLoggedIn',
    'profile',
    'accessToken',
    'refreshToken',
    'expires',
    'lastLoginAt',
  ]),
})
