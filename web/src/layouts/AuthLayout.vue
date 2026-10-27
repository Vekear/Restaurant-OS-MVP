<script setup>
/**
 * 登录 / 注册页外壳：左侧品牌叙事 + 右侧表单插槽
 * 表单内容由 Login.vue / Register.vue 通过默认插槽提供。
 */
import BrandMark from '@/components/BrandMark.vue'

const features = [
  { title: '双端一致的点餐体验', desc: '外卖下单与堂食扫码共用一套菜品与价格体系' },
  { title: '双状态机实时流转', desc: '订单与桌台状态由后端驱动，WebSocket 秒级推送' },
  { title: '商家经营一体化', desc: '接单、出餐、库存与 ECharts 报表开箱即用' }
]
</script>

<template>
  <div class="auth-shell">
    <section class="auth-shell__hero">
      <div class="auth-shell__hero-glow auth-shell__hero-glow--one" />
      <div class="auth-shell__hero-glow auth-shell__hero-glow--two" />

      <div class="auth-shell__hero-inner">
        <BrandMark size="lg" theme="dark" subtitle="SHIWEI ORDER · MVP" />

        <div class="auth-shell__headline-wrap">
          <p class="auth-shell__eyebrow">餐厅点餐系统 · 四层架构</p>
          <h1 class="auth-shell__headline">
            一桌好菜，<br />
            <span>从点单开始。</span>
          </h1>
          <p class="auth-shell__desc">
            外卖与堂食统一订单、双状态机流转、后厨实时提醒，商家与顾客在同一套系统里高效协同。
          </p>
        </div>

        <ul class="auth-shell__features">
          <li v-for="feature in features" :key="feature.title" class="auth-shell__feature">
            <span class="auth-shell__feature-dot"><el-icon><CircleCheck /></el-icon></span>
            <div>
              <p class="auth-shell__feature-title">{{ feature.title }}</p>
              <p class="auth-shell__feature-desc">{{ feature.desc }}</p>
            </div>
          </li>
        </ul>

        <div class="auth-shell__roles">
          <span>顾客端</span>
          <span>商家工作台</span>
          <span>管理后台</span>
        </div>
      </div>

      <p class="auth-shell__copyright">© 2026 拾味点餐 · 教学演示项目</p>
    </section>

    <section class="auth-shell__main">
      <div class="auth-shell__mobile-brand">
        <BrandMark size="md" />
      </div>
      <div class="auth-shell__card">
        <slot />
      </div>
      <p class="auth-shell__tip">演示环境：支付为模拟扣款 0.01 元，界面金额按真实订单展示</p>
    </section>
  </div>
</template>

<style scoped>
.auth-shell {
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  min-height: 100vh;
  background: var(--color-bg-page);
}

/* ============ 左侧品牌区 ============ */
.auth-shell__hero {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: var(--space-10);
  padding: var(--space-11) var(--space-12);
  overflow: hidden;
  color: var(--color-text-inverse);
  background: linear-gradient(150deg, #231b16 0%, #35251d 42%, #5c1f11 100%);
}

.auth-shell__hero-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(10px);
  pointer-events: none;
}

.auth-shell__hero-glow--one {
  top: -140px;
  right: -120px;
  width: 420px;
  height: 420px;
  background: radial-gradient(circle, rgba(228, 87, 46, 0.4), transparent 68%);
}

.auth-shell__hero-glow--two {
  bottom: -180px;
  left: -120px;
  width: 380px;
  height: 380px;
  background: radial-gradient(circle, rgba(227, 154, 43, 0.24), transparent 70%);
}

.auth-shell__hero-inner {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--space-10);
  max-width: 480px;
}

.auth-shell__headline-wrap {
  margin-top: var(--space-4);
}

.auth-shell__eyebrow {
  font-size: var(--font-size-xs);
  letter-spacing: 0.24em;
  text-transform: uppercase;
  color: rgba(246, 197, 110, 0.9);
}

.auth-shell__headline {
  margin-top: var(--space-4);
  font-size: var(--font-size-5xl);
  line-height: 1.16;
  letter-spacing: -0.02em;
  font-weight: var(--font-weight-bold);
}

.auth-shell__headline span {
  background: linear-gradient(100deg, #fb7748, #f6c56e);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.auth-shell__desc {
  margin-top: var(--space-6);
  max-width: 420px;
  font-size: var(--font-size-md);
  line-height: var(--line-height-loose);
  color: rgba(233, 224, 216, 0.76);
}

.auth-shell__features {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.auth-shell__feature {
  display: flex;
  align-items: flex-start;
  gap: var(--space-4);
}

.auth-shell__feature-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  margin-top: 2px;
  border-radius: 50%;
  background: rgba(228, 87, 46, 0.2);
  color: #f6c56e;
  font-size: 13px;
  flex: none;
}

.auth-shell__feature-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-inverse);
}

.auth-shell__feature-desc {
  margin-top: 2px;
  font-size: var(--font-size-sm);
  color: rgba(207, 197, 187, 0.7);
}

.auth-shell__roles {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.auth-shell__roles span {
  padding: var(--space-2) var(--space-4);
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: var(--radius-pill);
  font-size: var(--font-size-xs);
  letter-spacing: 0.04em;
  color: rgba(233, 224, 216, 0.82);
  background: rgba(255, 255, 255, 0.04);
}

.auth-shell__copyright {
  position: relative;
  font-size: var(--font-size-xs);
  color: rgba(207, 197, 187, 0.5);
}

/* ============ 右侧表单区 ============ */
.auth-shell__main {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-6);
  padding: var(--space-10) var(--space-8);
}

.auth-shell__card {
  width: 100%;
  max-width: 420px;
  padding: var(--space-10) var(--space-9);
  background: var(--color-surface);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
}

.auth-shell__mobile-brand {
  display: none;
}

.auth-shell__tip {
  max-width: 360px;
  text-align: center;
  font-size: var(--font-size-xs);
  line-height: var(--line-height-base);
  color: var(--color-text-tertiary);
}

@media (max-width: 960px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }

  .auth-shell__hero {
    display: none;
  }

  .auth-shell__main {
    min-height: 100vh;
    padding: var(--space-9) var(--space-5);
  }

  .auth-shell__mobile-brand {
    display: block;
    margin-bottom: var(--space-2);
  }

  .auth-shell__card {
    padding: var(--space-8) var(--space-6);
  }
}
</style>
