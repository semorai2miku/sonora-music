<script setup lang="ts">
import { changeClientPassword, clientMe, updateClientMe } from '@/api'
import { useUserStore } from '@/stores/modules/user'
import Button from '@/components/Ui/Button.vue'

const userStore = useUserStore()
const router = useRouter()

const state = reactive({
  loading: false,
  saving: false,
  passwordSaving: false,
  message: '',
  error: '',
  form: {
    username: '',
    profileId: '',
    avatar: '',
    bio: '',
  },
  passwordForm: {
    oldPassword: '',
    newPassword: '',
    confirmPassword: '',
  },
})

const loadProfile = async () => {
  if (!userStore.isLoggedIn) return
  state.loading = true
  try {
    const res = await clientMe()
    if (res?.data) {
      userStore.updateProfile(res.data)
      state.form.username = res.data.username || res.data.nickname || ''
      state.form.profileId = res.data.profileId || ''
      state.form.avatar = res.data.avatarUrl || '/default-avatar.svg'
      state.form.bio = res.data.bio || ''
    }
  } finally {
    state.loading = false
  }
}

const saveProfile = async () => {
  state.message = ''
  state.error = ''
  try {
    state.saving = true
    const res = await updateClientMe({
      username: state.form.username,
      profileId: state.form.profileId,
      avatar: state.form.avatar,
      bio: state.form.bio,
    })
    if (res?.code === 200 && res?.data) {
      userStore.updateProfile(res.data)
      state.message = '资料已保存'
    } else {
      state.error = res?.message || '保存失败'
    }
  } catch (error: any) {
    state.error = error?.response?.data?.message || error?.message || '保存失败'
  } finally {
    state.saving = false
  }
}

const savePassword = async () => {
  state.message = ''
  state.error = ''
  if (!state.passwordForm.oldPassword || !state.passwordForm.newPassword) {
    state.error = '请输入原密码和新密码'
    return
  }
  if (state.passwordForm.newPassword !== state.passwordForm.confirmPassword) {
    state.error = '两次输入的新密码不一致'
    return
  }
  try {
    state.passwordSaving = true
    const res = await changeClientPassword({
      oldPassword: state.passwordForm.oldPassword,
      newPassword: state.passwordForm.newPassword,
    })
    if (res?.code !== 200) {
      state.error = res?.message || '密码修改失败'
      return
    }
    state.passwordForm.oldPassword = ''
    state.passwordForm.newPassword = ''
    state.passwordForm.confirmPassword = ''
    state.message = '密码已修改'
  } catch (error: any) {
    state.error = error?.response?.data?.message || error?.message || '密码修改失败'
  } finally {
    state.passwordSaving = false
  }
}

const logout = () => {
  userStore.logout()
  router.push('/')
}

onMounted(loadProfile)
</script>

<template>
  <div class="relative flex-1 overflow-auto p-6">
    <div class="mx-auto max-w-5xl space-y-6">
      <div class="glass-container relative overflow-hidden rounded-3xl p-6">
        <div class="relative z-10 flex flex-col gap-5 md:flex-row md:items-center md:justify-between">
          <div class="flex items-center gap-4">
            <img
              :src="state.form.avatar || userStore.avatarUrl || '/default-avatar.svg'"
              alt="avatar"
              class="h-20 w-20 rounded-3xl object-cover ring-1 ring-white/10"
            />
            <div>
              <h1 class="text-primary text-3xl font-bold">个人资料</h1>
              <p class="text-primary/60 mt-1 text-sm">
                角色ID：{{ state.form.profileId || userStore.profileId || '-' }}
              </p>
            </div>
          </div>
          <Button variant="glass" size="sm" icon="icon-[mdi--logout]" @click="logout">退出登录</Button>
        </div>
      </div>

      <div v-if="!userStore.isLoggedIn" class="glass-card rounded-3xl p-8 text-center">
        <p class="text-primary/70">请先登录后再管理个人资料</p>
      </div>

      <template v-else>
        <div v-if="state.message" class="rounded-2xl bg-emerald-500/15 px-4 py-3 text-sm text-emerald-200">
          {{ state.message }}
        </div>
        <div v-if="state.error" class="rounded-2xl bg-red-500/15 px-4 py-3 text-sm text-red-200">
          {{ state.error }}
        </div>

        <div class="grid gap-6 lg:grid-cols-2">
          <section class="glass-card rounded-3xl p-6">
            <h2 class="text-primary mb-5 text-xl font-semibold">基础资料</h2>
            <div class="space-y-4">
              <label class="block">
                <span class="text-primary/60 mb-2 block text-xs font-medium">用户名</span>
                <input
                  v-model="state.form.username"
                  class="text-primary glass-card w-full rounded-xl px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-pink-400/40"
                  placeholder="请输入用户名"
                />
              </label>
              <label class="block">
                <span class="text-primary/60 mb-2 block text-xs font-medium">角色ID</span>
                <input
                  v-model="state.form.profileId"
                  class="text-primary glass-card w-full rounded-xl px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-pink-400/40"
                  placeholder="如 S123456"
                />
              </label>
              <label class="block">
                <span class="text-primary/60 mb-2 block text-xs font-medium">头像 URL</span>
                <input
                  v-model="state.form.avatar"
                  class="text-primary glass-card w-full rounded-xl px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-pink-400/40"
                  placeholder="/default-avatar.svg"
                />
              </label>
              <label class="block">
                <span class="text-primary/60 mb-2 block text-xs font-medium">个人简介</span>
                <textarea
                  v-model="state.form.bio"
                  rows="4"
                  class="text-primary glass-card w-full resize-none rounded-xl px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-pink-400/40"
                  placeholder="写点想展示给自己的内容"
                ></textarea>
              </label>
              <Button variant="solid" size="md" :loading="state.saving" @click="saveProfile">
                保存资料
              </Button>
            </div>
          </section>

          <section class="glass-card rounded-3xl p-6">
            <h2 class="text-primary mb-5 text-xl font-semibold">修改密码</h2>
            <div class="space-y-4">
              <label class="block">
                <span class="text-primary/60 mb-2 block text-xs font-medium">原密码</span>
                <input
                  v-model="state.passwordForm.oldPassword"
                  type="password"
                  class="text-primary glass-card w-full rounded-xl px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-purple-400/40"
                  placeholder="请输入原密码"
                />
              </label>
              <label class="block">
                <span class="text-primary/60 mb-2 block text-xs font-medium">新密码</span>
                <input
                  v-model="state.passwordForm.newPassword"
                  type="password"
                  class="text-primary glass-card w-full rounded-xl px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-purple-400/40"
                  placeholder="至少 6 位"
                />
              </label>
              <label class="block">
                <span class="text-primary/60 mb-2 block text-xs font-medium">确认新密码</span>
                <input
                  v-model="state.passwordForm.confirmPassword"
                  type="password"
                  class="text-primary glass-card w-full rounded-xl px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-purple-400/40"
                  placeholder="再次输入新密码"
                />
              </label>
              <Button variant="solid" size="md" :loading="state.passwordSaving" @click="savePassword">
                修改密码
              </Button>
            </div>
          </section>
        </div>
      </template>
    </div>
  </div>
</template>
