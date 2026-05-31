<script setup lang="ts">
defineOptions({
  name: "Welcome"
});

const modules = [
  {
    title: "音乐内容",
    value: "Songs / Albums / Artists",
    description: "集中维护歌曲、专辑与歌手信息，保证内容资产一致。"
  },
  {
    title: "运营分发",
    value: "Playlists / Banners",
    description: "管理首页曝光位、歌单推荐和轮播展示内容。"
  },
  {
    title: "账号协同",
    value: "Admin / Client Users",
    description: "围绕管理端与客户端账号形成统一的数据闭环。"
  }
];

const quickActions = [
  "优先整理歌手、专辑与歌曲的元数据完整度。",
  "定期检查轮播图与推荐歌单的曝光内容是否需要更新。",
  "在用户增长前先保持默认歌单、封面与资料链路稳定。"
];
</script>

<template>
  <section class="welcome">
    <div class="welcome-hero">
      <div class="welcome-copy">
        <span class="welcome-tag brand-font">SONORA CONTROL</span>
        <h1>Sonora 音乐管理后台</h1>
      </div>
      <img
        src="/branding/sonora-logo-horizontal-light.svg"
        alt="Sonora"
        class="welcome-logo"
      />
    </div>

    <div class="welcome-grid">
      <article v-for="item in modules" :key="item.title" class="welcome-card">
        <span class="welcome-card__title">{{ item.title }}</span>
        <strong class="brand-font">{{ item.value }}</strong>
        <p>{{ item.description }}</p>
      </article>
    </div>

    <div class="welcome-notes">
    </div>
  </section>
</template>

<style lang="scss" scoped>
.welcome {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.welcome-hero,
.welcome-card,
.welcome-notes {
  background: var(--sonora-surface-strong);
  border: 1px solid var(--sonora-border);
  border-radius: 24px;
  box-shadow: var(--sonora-shadow-sm);
}

.welcome-hero {
  display: flex;
  gap: 24px;
  align-items: center;
  justify-content: space-between;
  padding: 32px;
}

.welcome-copy {
  max-width: 620px;
}

.welcome-tag {
  display: inline-flex;
  padding: 6px 12px;
  margin-bottom: 18px;
  font-size: 11px;
  font-weight: 700;
  color: var(--el-color-primary);
  background: rgba(31, 124, 255, 0.1);
  border-radius: 999px;
}

.welcome-copy h1 {
  margin: 0 0 12px;
  font-size: clamp(28px, 4vw, 42px);
  line-height: 1.08;
}

.welcome-copy p,
.welcome-card p,
.welcome-notes li {
  margin: 0;
  font-size: 15px;
  line-height: 1.8;
  color: var(--el-text-color-regular);
}

.welcome-logo {
  width: min(360px, 36vw);
}

.welcome-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.welcome-card {
  padding: 24px;
}

.welcome-card__title {
  display: inline-flex;
  margin-bottom: 12px;
  font-size: 12px;
  font-weight: 700;
  color: var(--el-color-primary);
}

.welcome-card strong {
  display: block;
  margin-bottom: 10px;
  font-size: 18px;
  line-height: 1.3;
}

.welcome-notes {
  padding: 28px 32px;
}

.welcome-notes h2 {
  margin: 0 0 12px;
  font-size: 18px;
}

.welcome-notes ul {
  display: grid;
  gap: 10px;
  padding-left: 18px;
  margin: 0;
}

html.dark {
  .welcome-hero,
  .welcome-card,
  .welcome-notes {
    background: rgba(8, 17, 28, 0.88);
  }
}

@media (max-width: 960px) {
  .welcome-hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .welcome-logo {
    width: min(320px, 100%);
  }

  .welcome-grid {
    grid-template-columns: 1fr;
  }
}
</style>
