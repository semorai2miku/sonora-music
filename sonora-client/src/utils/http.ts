/**
 * HTTP 请求封装
 * 基于 axios 封装统一的请求/响应拦截、进度条和常用 HTTP 方法
 */
import axios, {
    AxiosInstance,
    AxiosResponse,
    InternalAxiosRequestConfig,
} from 'axios'
import NProgress from '@/config/nprogress'
import pinia from '@/stores'
import { useUserStore } from '@/stores/modules/user'

const parseExpiresTime = (expires?: string) => {
    if (!expires) return Number.POSITIVE_INFINITY
    const time = Date.parse(expires.replace(/-/g, '/'))
    return Number.isFinite(time) ? time : Number.POSITIVE_INFINITY
}

const isProtectedClientUrl = (url?: string) => {
    const value = String(url || '')
    return (
        value.startsWith('/api/client/me/') ||
        value === '/api/client/auth/me' ||
        value === '/api/client/auth/avatar' ||
        value.includes('/api/client/me/playlists/') && value.endsWith('/cover') ||
        value === '/api/client/auth/password'
    )
}

/** 创建 axios 实例 */
const instance: AxiosInstance = axios.create({
    baseURL: import.meta.env.VITE_APP_BASE_API,
    timeout: 1000000,
    withCredentials: true,
})

/** 请求拦截器：启动进度条、添加时间戳防缓存 */
instance.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
        // 开启进度条
        NProgress.start()
        let token = ''
        try {
            const userStore = useUserStore(pinia)
            if (userStore.isAuthenticated) {
                token = userStore.accessToken || ''
            } else if (userStore.isLoggedIn) {
                userStore.logout()
            }
        } catch {}

        if (!token) {
            const rawUser = localStorage.getItem('user')
            if (rawUser) {
                try {
                    const userState = JSON.parse(rawUser)
                    const expiresTime = parseExpiresTime(userState?.expires)
                    if (userState?.accessToken && expiresTime > Date.now()) {
                        token = userState.accessToken
                    } else if (userState?.accessToken) {
                        localStorage.removeItem('user')
                    }
                } catch {}
            }
        }

        if (token) {
            config.headers.Authorization = `Bearer ${token}`
        }
        if (config.params === undefined) {
            config.params = {}
        }
        // 添加或修改params
        Object.assign(config.params, {
            timestamp: Date.now(),
            realIP: '116.25.146.177',
        })
        return config
    },
    (error) => Promise.reject(error)
)

/** 响应拦截器：结束进度条、直接返回 data */
instance.interceptors.response.use(
    (response: AxiosResponse) => {
        const { data } = response

        // 进度条结束
        NProgress.done()
        return data
    },
    (error) => {
        // 响应错误时也结束进度条
        NProgress.done()
        const status = error?.response?.status
        if ((status === 401 || status === 403) && isProtectedClientUrl(error?.config?.url)) {
            try {
                const userStore = useUserStore(pinia)
                userStore.logout()
            } catch {}
            if (typeof window !== 'undefined') {
                window.dispatchEvent(new CustomEvent('sonora:auth-expired'))
            }
            error.message = status === 403 ? '登录已过期或无权限，请重新登录' : '请先登录'
        }
        return Promise.reject(error)
    }
)

/** GET 请求 */
export const httpGet = <T>(url: string, params?: object): Promise<T> =>
    instance.get(url, { params })

/** POST 请求 */
export const httpPost = <T>(
    url: string,
    data?: object,
    header?: object
): Promise<T> => instance.post(url, data, header)

/** PUT 请求 */
export const httpPut = <T>(url: string, data?: object): Promise<T> =>
    instance.put(url, data)

/** DELETE 请求 */
export const httpDelete = <T>(url: string, params?: object): Promise<T> =>
    instance.delete(url, { params })

/** 文件上传（multipart/form-data） */
export const httpUpload = <T>(
    url: string,
    formData: FormData,
    header?: object
): Promise<T> => {
    return instance.post(url, formData, {
        headers: {
            'Content-Type': 'multipart/form-data',
            ...header,
        },
    })
}
