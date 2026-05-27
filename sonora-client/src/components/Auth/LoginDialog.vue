<script setup lang="ts">
import { clientLogin, clientRegister } from '@/api'
import { useUserStore } from '@/stores/modules/user'
import Button from '@/components/Ui/Button.vue'

const emit = defineEmits<{ (e: 'close'): void; (e: 'success'): void }>()
const userStore = useUserStore()
const visible = ref(true)
const loginSuccess = ref(false)
const errorMessage = ref('')

const state = reactive({
  mode: 'login' as 'login' | 'register',
  loading: false,
  username: '',
  password: '',
  confirmPassword: '',
})
const { mode, loading, username, password, confirmPassword } = toRefs(state)

const canSubmit = computed(() => {
  if (!state.username.trim() || !state.password) return false
  if (state.mode === 'register') return state.password === state.confirmPassword
  return true
})

const submit = async () => {
  errorMessage.value = ''
  if (!state.username.trim()) {
    errorMessage.value = '请输入用户名'
    return
  }
  if (state.password.length < 6) {
    errorMessage.value = '密码至少 6 位'
    return
  }
  if (state.mode === 'register' && state.password !== state.confirmPassword) {
    errorMessage.value = '两次输入的密码不一致'
    return
  }

  try {
    state.loading = true
    const res =
      state.mode === 'register'
        ? await clientRegister({ username: state.username.trim(), password: state.password })
        : await clientLogin({ username: state.username.trim(), password: state.password })
    const data = res?.data
    if (!data?.profile || !data?.accessToken) {
      errorMessage.value = res?.message || '登录失败'
      return
    }
    userStore.setUser(data.profile, {
      accessToken: data.accessToken,
      refreshToken: data.refreshToken,
      expires: data.expires,
    })
    loginSuccess.value = true
    visible.value = false
  } catch (error: any) {
    errorMessage.value =
      error?.response?.data?.message || error?.message || (state.mode === 'register' ? '注册失败' : '登录失败')
  } finally {
    state.loading = false
  }
}

const handleAfterLeave = () => {
  if (loginSuccess.value) emit('success')
  emit('close')
  loginSuccess.value = false
}

watch(mode, () => {
  errorMessage.value = ''
  state.password = ''
  state.confirmPassword = ''
})
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center p-4">
    <Transition name="mask" appear>
      <div
        v-if="visible"
        class="absolute inset-0 bg-black/60 backdrop-blur-sm"
        @click="visible = false"
      />
    </Transition>

    <Transition name="dialog" appear @after-leave="handleAfterLeave">
      <div v-if="visible" class="relative z-10 w-full max-w-md">
        <div class="glass-container-strong overflow-hidden">
          <Button
            variant="soft"
            size="icon-sm"
            rounded="full"
            icon="icon-[mdi--close]"
            icon-class="h-4 w-4"
            class="absolute top-4 right-4 z-20"
            @click="visible = false"
          />

          <div class="relative p-6 pb-4">
            <div class="mb-6 flex items-center gap-4">
              <div
                class="flex h-12 w-12 items-center justify-center rounded-2xl bg-linear-to-br from-pink-500 to-purple-600 shadow-lg shadow-pink-500/25"
              >
                <span class="icon-[mdi--account-circle] h-6 w-6 text-white" />
              </div>
              <div>
                <h2 class="text-primary text-xl font-bold">Sonora 账号</h2>
                <p class="text-primary/50 mt-0.5 text-sm">登录后可同步喜欢的音乐和个人资料</p>
              </div>
            </div>

            <div class="glass-card mx-auto flex w-fit items-center gap-2 p-1.5">
              <button
                class="flex items-center gap-2 rounded-xl px-4 py-2.5 text-sm font-medium transition-all"
                :class="
                  mode === 'login'
                    ? 'bg-hover-glass text-primary shadow-sm'
                    : 'text-primary/60 hover:bg-hover-glass/50 hover:text-primary/80'
                "
                @click="mode = 'login'"
              >
                <span class="icon-[mdi--login] h-4 w-4"></span>
                <span>登录</span>
              </button>
              <button
                class="flex items-center gap-2 rounded-xl px-4 py-2.5 text-sm font-medium transition-all"
                :class="
                  mode === 'register'
                    ? 'bg-hover-glass text-primary shadow-sm'
                    : 'text-primary/60 hover:bg-hover-glass/50 hover:text-primary/80'
                "
                @click="mode = 'register'"
              >
                <span class="icon-[mdi--account-plus-outline] h-4 w-4"></span>
                <span>注册</span>
              </button>
            </div>
          </div>

          <div class="px-6 pb-6">
            <div class="space-y-4">
              <div class="space-y-2">
                <label class="text-primary/60 block text-xs font-medium">用户名</label>
                <div
                  class="glass-card group flex items-center gap-3 rounded-xl px-4 py-3 transition-all focus-within:ring-2 focus-within:ring-pink-400/50"
                >
                  <span
                    class="icon-[mdi--account-outline] text-primary/40 h-5 w-5 transition-colors group-focus-within:text-pink-400"
                  />
                  <input
                    v-model="username"
                    type="text"
                    placeholder="请输入用户名"
                    class="text-primary w-full bg-transparent text-sm outline-none placeholder:text-white/30"
                    @keyup.enter="submit"
                  />
                </div>
              </div>

              <div class="space-y-2">
                <label class="text-primary/60 block text-xs font-medium">密码</label>
                <div
                  class="glass-card group flex items-center gap-3 rounded-xl px-4 py-3 transition-all focus-within:ring-2 focus-within:ring-purple-400/50"
                >
                  <span
                    class="icon-[mdi--lock-outline] text-primary/40 h-5 w-5 transition-colors group-focus-within:text-purple-400"
                  />
                  <input
                    v-model="password"
                    type="password"
                    placeholder="请输入密码"
                    class="text-primary w-full bg-transparent text-sm outline-none placeholder:text-white/30"
                    @keyup.enter="submit"
                  />
                </div>
              </div>

              <div v-if="mode === 'register'" class="space-y-2">
                <label class="text-primary/60 block text-xs font-medium">确认密码</label>
                <div
                  class="glass-card group flex items-center gap-3 rounded-xl px-4 py-3 transition-all focus-within:ring-2 focus-within:ring-purple-400/50"
                >
                  <span
                    class="icon-[mdi--lock-check-outline] text-primary/40 h-5 w-5 transition-colors group-focus-within:text-purple-400"
                  />
                  <input
                    v-model="confirmPassword"
                    type="password"
                    placeholder="请再次输入密码"
                    class="text-primary w-full bg-transparent text-sm outline-none placeholder:text-white/30"
                    @keyup.enter="submit"
                  />
                </div>
              </div>
            </div>

            <p v-if="errorMessage" class="mt-4 text-sm text-red-300">{{ errorMessage }}</p>

            <Button
              variant="solid"
              size="md"
              block
              :loading="loading"
              :disabled="loading || !canSubmit"
              :icon="mode === 'register' ? 'icon-[mdi--account-plus-outline]' : 'icon-[mdi--login]'"
              class="mt-6"
              @click="submit"
            >
              {{ loading ? '处理中...' : mode === 'register' ? '注册并登录' : '登录' }}
            </Button>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.dialog-enter-active,
.dialog-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.dialog-enter-from,
.dialog-leave-to {
  opacity: 0;
  transform: scale(0.95) translateY(10px);
}

.mask-enter-active,
.mask-leave-active {
  transition: opacity 0.3s ease;
}
.mask-enter-from,
.mask-leave-to {
  opacity: 0;
}
</style>
