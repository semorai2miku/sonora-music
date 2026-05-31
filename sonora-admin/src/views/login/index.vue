<script setup lang="ts">
import Motion from "./utils/motion";
import { useRouter } from "vue-router";
import { message } from "@/utils/message";
import { loginRules } from "./utils/rule";
import { computed, ref, reactive } from "vue";
import { debounce } from "@pureadmin/utils";
import { useNav } from "@/layout/hooks/useNav";
import { useEventListener } from "@vueuse/core";
import type { FormInstance } from "element-plus";
import { useLayout } from "@/layout/hooks/useLayout";
import { useUserStoreHook } from "@/store/modules/user";
import { initRouter, getTopMenu } from "@/router/utils";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import { useDataThemeChange } from "@/layout/hooks/useDataThemeChange";

import dayIcon from "@/assets/svg/day.svg?component";
import darkIcon from "@/assets/svg/dark.svg?component";
import Lock from "~icons/ri/lock-fill";
import User from "~icons/ri/user-3-fill";

defineOptions({
  name: "Login"
});

const router = useRouter();
const loading = ref(false);
const disabled = ref(false);
const ruleFormRef = ref<FormInstance>();

const { initStorage } = useLayout();
initStorage();

const { dataTheme, overallStyle, dataThemeChange } = useDataThemeChange();
dataThemeChange(overallStyle.value);
const { title } = useNav();
const brandLogo = computed(() =>
  dataTheme.value
    ? "/branding/sonora-logo-horizontal-dark.svg"
    : "/branding/sonora-logo-horizontal-light.svg"
);

const ruleForm = reactive({
  username: "sonora-music",
  password: "admin123"
});

const onLogin = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate(valid => {
    if (valid) {
      loading.value = true;
      useUserStoreHook()
        .loginByUsername({
          username: ruleForm.username,
          password: ruleForm.password
        })
        .then(res => {
          if (res.success) {
            // 获取后端路由
            return initRouter().then(() => {
              disabled.value = true;
              router
                .push(getTopMenu(true).path)
                .then(() => {
                  message("登录成功", { type: "success" });
                })
                .finally(() => (disabled.value = false));
            });
          } else {
            message("登录失败", { type: "error" });
          }
        })
        .finally(() => (loading.value = false));
    }
  });
};

const immediateDebounce: any = debounce(
  formRef => onLogin(formRef),
  1000,
  true
);

useEventListener(document, "keydown", ({ code }) => {
  if (
    ["Enter", "NumpadEnter"].includes(code) &&
    !disabled.value &&
    !loading.value
  )
    immediateDebounce(ruleFormRef.value);
});
</script>

<template>
  <div class="login-shell select-none">
    <div class="theme-toggle">
      <!-- 主题 -->
      <el-switch
        v-model="dataTheme"
        inline-prompt
        :active-icon="dayIcon"
        :inactive-icon="darkIcon"
        @change="dataThemeChange"
      />
    </div>
    <div class="login-panel">
      <section class="login-brand">
        <img :src="brandLogo" alt="Sonora" class="brand-lockup" />
        <Motion>
          <h1 class="brand-heading">SONORA ADMIN</h1>
        </Motion>
        <Motion :delay="80">
          <p class="brand-copy">
            面向音乐内容、歌单运营、轮播资源和用户资料的一体化管理后台。
          </p>
        </Motion>
        <div class="brand-grid">
          <Motion :delay="120">
            <div class="brand-card">
              <span class="brand-card__label">内容管理</span>
              <strong>歌曲 / 专辑 / 歌手</strong>
              <p>统一录入、编辑、批量维护核心音乐资产。</p>
            </div>
          </Motion>
          <Motion :delay="160">
            <div class="brand-card">
              <span class="brand-card__label">运营分发</span>
              <strong>歌单 / 轮播 / 推荐位</strong>
              <p>更清晰地管理对外曝光的内容组织方式。</p>
            </div>
          </Motion>
          <Motion :delay="200">
            <div class="brand-card">
              <span class="brand-card__label">账号体系</span>
              <strong>后台与客户端联动</strong>
              <p>围绕用户、收藏和资料维护形成完整闭环。</p>
            </div>
          </Motion>
        </div>
      </section>

      <section class="login-card">
        <div class="login-card__head">
          <p class="login-card__eyebrow">欢迎回来</p>
          <h2 class="outline-hidden">{{ title }}</h2>
          <span>使用超级管理员账号进入后台。</span>
        </div>

        <el-form
          ref="ruleFormRef"
          :model="ruleForm"
          :rules="loginRules"
          size="large"
        >
          <Motion :delay="100">
            <el-form-item
              :rules="[
                {
                  required: true,
                  message: '请输入账号',
                  trigger: 'blur'
                }
              ]"
              prop="username"
            >
              <el-input
                v-model="ruleForm.username"
                clearable
                placeholder="账号"
                :prefix-icon="useRenderIcon(User)"
              />
            </el-form-item>
          </Motion>

          <Motion :delay="150">
            <el-form-item prop="password">
              <el-input
                v-model="ruleForm.password"
                clearable
                show-password
                placeholder="密码"
                :prefix-icon="useRenderIcon(Lock)"
              />
            </el-form-item>
          </Motion>

          <Motion :delay="220">
            <div class="login-hint">
              <span class="brand-font">sonora-music</span>
              <span>/</span>
              <span>admin123</span>
            </div>
          </Motion>

          <Motion :delay="250">
            <el-button
              class="login-submit w-full mt-4!"
              size="default"
              type="primary"
              :loading="loading"
              :disabled="disabled"
              @click="onLogin(ruleFormRef)"
            >
              登录后台
            </el-button>
          </Motion>
        </el-form>
      </section>
    </div>
  </div>
</template>

<style lang="scss" scoped>
:deep(.el-input-group__append, .el-input-group__prepend) {
  padding: 0;
}

.login-shell {
  position: relative;
  min-height: 100vh;
  padding: 32px;
}

.login-shell::before {
  position: absolute;
  inset: 0;
  content: "";
  background:
    radial-gradient(circle at left top, rgba(77, 163, 255, 0.24), transparent 24%),
    radial-gradient(circle at right bottom, rgba(31, 124, 255, 0.14), transparent 28%);
  pointer-events: none;
}

.theme-toggle {
  position: absolute;
  top: 24px;
  right: 28px;
  z-index: 2;
}

.login-panel {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(360px, 420px);
  gap: 32px;
  align-items: stretch;
  max-width: 1240px;
  min-height: calc(100vh - 64px);
  margin: 0 auto;
}

.login-brand,
.login-card {
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  border: 1px solid var(--sonora-border);
  box-shadow: var(--sonora-shadow-lg);
}

.login-brand {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 48px;
  background: linear-gradient(
    160deg,
    rgba(255, 255, 255, 0.88),
    rgba(246, 251, 255, 0.98)
  );
  border-radius: 28px;
}

.brand-lockup {
  width: min(420px, 100%);
}

.brand-heading {
  margin: 26px 0 12px;
  font-family: "Orbitron", sans-serif;
  font-size: clamp(26px, 4vw, 44px);
  font-weight: 700;
  line-height: 1.05;
  letter-spacing: 0.08em;
  color: var(--sonora-ink);
}

.brand-copy {
  max-width: 560px;
  margin: 0 0 28px;
  font-size: 15px;
  line-height: 1.8;
  color: var(--el-text-color-regular);
}

.brand-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.brand-card {
  padding: 20px;
  border: 1px solid rgba(31, 124, 255, 0.1);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.76);
}

.brand-card__label {
  display: inline-flex;
  margin-bottom: 12px;
  padding: 5px 10px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--el-color-primary);
  background: rgba(31, 124, 255, 0.1);
  border-radius: 999px;
}

.brand-card strong {
  display: block;
  margin-bottom: 8px;
  font-size: 17px;
  color: var(--sonora-ink);
}

.brand-card p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

.login-card {
  align-self: center;
  padding: 32px;
  background: rgba(255, 255, 255, 0.94);
  border-radius: 24px;
}

.login-card__head {
  margin-bottom: 24px;
}

.login-card__head h2 {
  margin: 8px 0 10px;
  font-size: 26px;
  font-weight: 700;
  color: var(--sonora-ink);
}

.login-card__head span {
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.login-card__eyebrow {
  margin: 0;
  font-family: "Orbitron", sans-serif;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: var(--el-color-primary);
}

.login-hint {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  background: rgba(31, 124, 255, 0.06);
  border-radius: 999px;
}

.login-submit {
  height: 44px;
}

html.dark {
  .login-brand {
    background: linear-gradient(
      160deg,
      rgba(8, 17, 28, 0.86),
      rgba(10, 19, 33, 0.96)
    );
  }

  .brand-heading,
  .brand-card strong,
  .login-card__head h2 {
    color: #f6fbff;
  }

  .brand-card {
    background: rgba(8, 17, 28, 0.72);
    border-color: rgba(77, 163, 255, 0.14);
  }

  .login-card {
    background: rgba(8, 17, 28, 0.92);
  }

  .login-hint {
    background: rgba(77, 163, 255, 0.12);
  }
}

@media (max-width: 1080px) {
  .login-panel {
    grid-template-columns: 1fr;
    gap: 20px;
    align-items: start;
    padding-top: 48px;
  }

  .login-brand {
    padding: 32px;
  }

  .brand-grid {
    grid-template-columns: 1fr;
  }

  .login-card {
    width: min(100%, 460px);
    margin: 0 auto;
  }
}

@media (max-width: 640px) {
  .login-shell {
    padding: 18px;
  }

  .theme-toggle {
    top: 18px;
    right: 18px;
  }

  .login-brand,
  .login-card {
    padding: 24px;
  }
}
</style>
