<script setup lang="ts">
import {
  changeClientPassword,
  clientMe,
  deleteClientMe,
  updateClientMe,
  uploadClientAvatar,
} from '@/api'
import { useUserStore } from '@/stores/modules/user'
import Button from '@/components/Ui/Button.vue'
import AvatarCropperDialog from '@/components/Profile/AvatarCropperDialog.vue'

const userStore = useUserStore()
const router = useRouter()

const emailPattern = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/
const passwordPattern = /^[\x21-\x7E]{6,72}$/

const uploadInputRef = ref<HTMLInputElement | null>(null)
const cropperVisible = ref(false)
const cropperFile = ref<File | null>(null)

const state = reactive({
  loading: false,
  saving: false,
  avatarSaving: false,
  passwordSaving: false,
  cancelSaving: false,
  passwordDialogVisible: false,
  cancelDialogVisible: false,
  message: '',
  error: '',
  form: {
    username: '',
    email: '',
    phone: '',
    avatar: '',
    bio: '',
  },
  passwordForm: {
    oldPassword: '',
    newPassword: '',
    confirmPassword: '',
  },
})

const avatarUrl = computed(() => state.form.avatar || userStore.avatarUrl || '/default-avatar.svg')

const setMessage = (message: string) => {
  state.message = message
  state.error = ''
}

const setError = (message: string) => {
  state.error = message
  state.message = ''
}

const fillForm = (profile: NonNullable<typeof userStore.profile>) => {
  state.form.username = profile.username || profile.nickname || ''
  state.form.email = profile.email || ''
  state.form.phone = profile.phone || ''
  state.form.avatar = profile.avatarUrl || '/default-avatar.svg'
  state.form.bio = profile.bio || ''
}

const loadProfile = async () => {
  if (!userStore.isLoggedIn) return
  state.loading = true
  try {
    const res = await clientMe()
    if (res?.data) {
      userStore.updateProfile(res.data)
      fillForm(res.data)
    }
  } finally {
    state.loading = false
  }
}

const validateProfile = () => {
  if (!state.form.username.trim()) {
    setError('请输入用户名')
    return false
  }
  if (!emailPattern.test(state.form.email.trim())) {
    setError('请输入正确的邮箱')
    return false
  }
  return true
}

const saveProfile = async () => {
  if (!validateProfile()) return
  try {
    state.saving = true
    const res = await updateClientMe({
      username: state.form.username.trim(),
      email: state.form.email.trim(),
      phone: state.form.phone.trim(),
      avatar: state.form.avatar || '/default-avatar.svg',
      bio: state.form.bio.trim(),
    })
    if (res?.code === 200 && res?.data) {
      userStore.updateProfile(res.data)
      fillForm(res.data)
      setMessage('资料已更新')
    } else {
      setError(res?.message || '保存失败')
    }
  } catch (error: any) {
    setError(error?.response?.data?.message || error?.message || '保存失败')
  } finally {
    state.saving = false
  }
}

const triggerAvatarUpload = () => {
  uploadInputRef.value?.click()
}

const onAvatarFileChange = (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    setError('请选择图片文件')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    setError('头像图片不能超过 5MB')
    return
  }
  cropperFile.value = file
  cropperVisible.value = true
}

const onCropConfirm = async (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  try {
    state.avatarSaving = true
    const uploadRes = await uploadClientAvatar(formData)
    if (uploadRes?.code !== 200 || !uploadRes.data?.url) {
      setError(uploadRes?.message || '头像上传失败')
      return
    }
    state.form.avatar = uploadRes.data.url
    const saveRes = await updateClientMe({ avatar: uploadRes.data.url })
    if (saveRes?.code === 200 && saveRes.data) {
      userStore.updateProfile(saveRes.data)
      state.form.avatar = saveRes.data.avatarUrl || uploadRes.data.url
      setMessage('头像已更新')
    }
  } catch (error: any) {
    setError(error?.response?.data?.message || error?.message || '头像上传失败')
  } finally {
    state.avatarSaving = false
  }
}

const openPasswordDialog = () => {
  state.passwordForm.oldPassword = ''
  state.passwordForm.newPassword = ''
  state.passwordForm.confirmPassword = ''
  state.passwordDialogVisible = true
  state.error = ''
  state.message = ''
}

const savePassword = async () => {
  if (!state.passwordForm.oldPassword || !state.passwordForm.newPassword) {
    setError('请输入原密码和新密码')
    return
  }
  if (!passwordPattern.test(state.passwordForm.newPassword)) {
    setError('新密码需为 6-72 位，且只能包含字母、数字和特殊符号')
    return
  }
  if (state.passwordForm.newPassword !== state.passwordForm.confirmPassword) {
    setError('两次输入的新密码不一致')
    return
  }
  try {
    state.passwordSaving = true
    const res = await changeClientPassword({
      oldPassword: state.passwordForm.oldPassword,
      newPassword: state.passwordForm.newPassword,
    })
    if (res?.code !== 200) {
      setError(res?.message || '密码修改失败')
      return
    }
    state.passwordDialogVisible = false
    setMessage('密码已修改')
  } catch (error: any) {
    setError(error?.response?.data?.message || error?.message || '密码修改失败')
  } finally {
    state.passwordSaving = false
  }
}

const cancelAccount = async () => {
  try {
    state.cancelSaving = true
    const res = await deleteClientMe()
    if (res?.code !== 200) {
      setError(res?.message || '注销失败')
      return
    }
    userStore.logout()
    state.cancelDialogVisible = false
    router.push('/')
  } catch (error: any) {
    setError(error?.response?.data?.message || error?.message || '注销失败')
  } finally {
    state.cancelSaving = false
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
    <div class="mx-auto max-w-3xl">
      <div v-if="!userStore.isLoggedIn" class="glass-card rounded-3xl p-8 text-center">
        <p class="text-primary/70">请先登录后再管理个人资料</p>
      </div>

      <template v-else>
        <section class="glass-container-strong overflow-hidden p-6">
          <div class="flex flex-col items-center gap-5">
            <button
              class="group relative h-32 w-32 overflow-hidden rounded-full ring-1 ring-white/10"
              :disabled="state.avatarSaving"
              title="更换头像"
              @click="triggerAvatarUpload"
            >
              <img :src="avatarUrl" alt="avatar" class="h-full w-full object-cover" />
              <span
                class="absolute inset-0 flex items-center justify-center bg-black/55 text-sm font-medium text-white opacity-0 transition-opacity group-hover:opacity-100"
              >
                {{ state.avatarSaving ? '上传中...' : '更换头像' }}
              </span>
            </button>
            <input
              ref="uploadInputRef"
              class="hidden"
              type="file"
              accept="image/*"
              @change="onAvatarFileChange"
            />

            <div class="w-full space-y-4">
              <label class="block">
                <span class="text-primary/60 mb-2 block text-xs font-medium">用户名</span>
                <input
                  v-model="state.form.username"
                  class="text-primary glass-card w-full rounded-xl px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-pink-400/40"
                  placeholder="请输入用户名"
                />
              </label>
              <label class="block">
                <span class="text-primary/60 mb-2 block text-xs font-medium">邮箱</span>
                <input
                  v-model="state.form.email"
                  type="email"
                  class="text-primary glass-card w-full rounded-xl px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-sky-400/40"
                  placeholder="请输入邮箱"
                />
              </label>
              <label class="block">
                <span class="text-primary/60 mb-2 block text-xs font-medium">联系电话</span>
                <input
                  v-model="state.form.phone"
                  class="text-primary glass-card w-full rounded-xl px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-purple-400/40"
                  placeholder="请输入联系电话"
                />
              </label>
              <label class="block">
                <span class="text-primary/60 mb-2 block text-xs font-medium">简介</span>
                <textarea
                  v-model="state.form.bio"
                  rows="5"
                  maxlength="512"
                  class="text-primary glass-card w-full resize-none rounded-xl px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-pink-400/40"
                  placeholder="编辑个人简介"
                ></textarea>
              </label>
            </div>

            <div v-if="state.message" class="w-full rounded-2xl bg-emerald-500/15 px-4 py-3 text-sm text-emerald-200">
              {{ state.message }}
            </div>
            <div v-if="state.error" class="w-full rounded-2xl bg-red-500/15 px-4 py-3 text-sm text-red-200">
              {{ state.error }}
            </div>

            <div class="grid w-full gap-3 sm:grid-cols-4">
              <Button variant="solid" size="md" :loading="state.saving" @click="saveProfile">更新信息</Button>
              <Button variant="glass" size="md" @click="openPasswordDialog">修改密码</Button>
              <Button variant="glass" size="md" @click="logout">退出登录</Button>
              <Button variant="soft" size="md" @click="state.cancelDialogVisible = true">注销账号</Button>
            </div>
          </div>
        </section>
      </template>
    </div>

    <AvatarCropperDialog v-model="cropperVisible" :file="cropperFile" @confirm="onCropConfirm" />

    <Teleport to="body">
      <Transition name="modal">
        <div v-if="state.passwordDialogVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/65 p-4 backdrop-blur-sm">
          <div class="glass-container-strong w-full max-w-md p-6">
            <h3 class="text-primary mb-5 text-lg font-semibold">修改密码</h3>
            <div class="space-y-4">
              <input
                v-model="state.passwordForm.oldPassword"
                type="password"
                class="text-primary glass-card w-full rounded-xl px-4 py-3 text-sm outline-none"
                placeholder="原密码"
              />
              <input
                v-model="state.passwordForm.newPassword"
                type="password"
                class="text-primary glass-card w-full rounded-xl px-4 py-3 text-sm outline-none"
                placeholder="新密码"
              />
              <input
                v-model="state.passwordForm.confirmPassword"
                type="password"
                class="text-primary glass-card w-full rounded-xl px-4 py-3 text-sm outline-none"
                placeholder="确认密码"
              />
            </div>
            <div class="mt-6 flex justify-end gap-3">
              <Button variant="glass" size="sm" @click="state.passwordDialogVisible = false">取消</Button>
              <Button variant="solid" size="sm" :loading="state.passwordSaving" @click="savePassword">修改密码</Button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <Teleport to="body">
      <Transition name="modal">
        <div v-if="state.cancelDialogVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/65 p-4 backdrop-blur-sm">
          <div class="glass-container-strong w-full max-w-md p-6">
            <h3 class="text-primary text-lg font-semibold">注销账号</h3>
            <p class="text-primary/70 mt-3 text-sm leading-6">
              注销账号后，所有个人资料、喜欢的音乐、歌单和评论数据都将被清除，是否确认注销？
            </p>
            <div class="mt-6 flex justify-end gap-3">
              <Button variant="glass" size="sm" @click="state.cancelDialogVisible = false">返回</Button>
              <Button variant="solid" size="sm" :loading="state.cancelSaving" @click="cancelAccount">确定</Button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}
</style>
