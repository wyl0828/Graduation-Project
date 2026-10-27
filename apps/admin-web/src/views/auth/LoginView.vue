<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <div class="logo-box">
          <span class="logo-icon">🏡</span>
        </div>
        <h2 class="login-title">社区居家养老服务系统</h2>
        <p class="login-subtitle">智慧民政数字化管理工作台 · 身份认证中心</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        class="login-form"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="phone">
          <el-input
            v-model="form.phone"
            placeholder="管理员 / 网格员 / 护工手机号"
            prefix-icon="User"
            clearable
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="登录密码"
            prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-button
          type="primary"
          class="submit-btn"
          :loading="loading"
          @click="handleLogin"
        >
          立即登录工作台
        </el-button>
      </el-form>

      <div class="login-footer">
        <span>© 2026 社区居家养老数字化服务运营中心 · 审计留痕受控保护</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance } from 'element-plus'
import { adminApi } from '../../api/adminApi'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  phone: '',
  password: '',
})

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的11位手机号', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入登录密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位字符', trigger: 'blur' }
  ]
}

async function handleLogin() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res = await adminApi.login(form.phone.trim(), form.password)
      const data = res.data
      if (data && data.csrfToken) {
        localStorage.setItem('user', JSON.stringify({ userId: data.userId, phone: data.phone }))
        localStorage.setItem('csrfToken', data.csrfToken)
        if (data.activeRole) {
          localStorage.setItem('activeRole', data.activeRole)
        }
        const current = await adminApi.getCurrentUser()
        if (!current.data || !['COMMUNITY_ADMIN', 'GRID_WORKER', 'CARE_WORKER'].includes(current.data.currentRole)) {
          throw new Error('当前账号没有管理端可用角色')
        }
        localStorage.setItem('user', JSON.stringify(current.data))
        ElMessage.success('登录成功')
        router.push(['GRID_WORKER', 'CARE_WORKER'].includes(current.data.currentRole) ? '/order/execution-review' : '/dashboard/community')
      } else {
        ElMessage.error(res.message || '登录响应缺少会话校验信息')
      }
    } catch (error: unknown) {
      ElMessage.error(error instanceof Error ? error.message : '登录失败，请检查账号密码')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0F172A 0%, #1E293B 50%, #0F172A 100%);
  padding: 20px;
  box-sizing: border-box;
}

.login-card {
  width: 100%;
  max-width: 440px;
  background: #FFFFFF;
  border-radius: 16px;
  padding: 40px 32px 28px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.3), 0 8px 10px -6px rgba(0, 0, 0, 0.2);
}

.login-header {
  text-align: center;
  margin-bottom: 28px;
}

.logo-box {
  width: 56px;
  height: 56px;
  background: #EFF6FF;
  border: 1px solid #BFDBFE;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 14px;
}

.logo-icon {
  font-size: 28px;
}

.login-title {
  font-size: 22px;
  font-weight: 700;
  color: #0F172A;
  margin: 0 0 6px 0;
}

.login-subtitle {
  font-size: 13px;
  color: #64748B;
  margin: 0;
}

.login-form {
  margin-top: 10px;
}

.quick-login-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 18px;
  background: #F8FAFC;
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px dashed #CBD5E1;
}

.quick-title {
  font-size: 12px;
  color: #64748B;
}

.submit-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 8px;
  margin-top: 6px;
}

.login-footer {
  margin-top: 24px;
  text-align: center;
  font-size: 11px;
  color: #94A3B8;
  border-top: 1px solid #F1F5F9;
  padding-top: 16px;
}
</style>
