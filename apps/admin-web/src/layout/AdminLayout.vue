<template>
  <el-container class="layout-container">
    <!-- 侧边导航 -->
    <el-aside width="240px" class="aside-menu">
      <div class="brand-box">
        <span class="brand-icon">🏛️</span>
        <div class="brand-text">
          <span class="main-title">居家养老管理端</span>
          <span class="sub-title">社区治理 · 网格照护</span>
        </div>
      </div>

      <el-menu
        :default-active="activeRoute"
        class="menu-vertical"
        router
        background-color="#0f172a"
        text-color="#94a3b8"
        active-text-color="#38bdf8"
      >
        <el-menu-item v-if="isAdmin" index="/dashboard/community">
          <el-icon><DataAnalysis /></el-icon>
          <span>社区综合态势大屏</span>
        </el-menu-item>
        <el-menu-item v-if="isAdmin" index="/elder/archive">
          <el-icon><User /></el-icon>
          <span>老人档案与激活码</span>
        </el-menu-item>
        <el-menu-item v-if="isAdmin" index="/family/kinship">
          <el-icon><Connection /></el-icon>
          <span>亲情绑定与风控大厅</span>
        </el-menu-item>
        <el-menu-item v-if="isAdmin" index="/elder/correction">
          <el-icon><DocumentChecked /></el-icon>
          <span>档案更正审批 (FZ-01)</span>
        </el-menu-item>
        <el-menu-item v-if="isAdmin" index="/health/emergency-alert">
          <el-icon><Bell /></el-icon>
          <span>预警与 SOS 应急指挥</span>
        </el-menu-item>
        <el-menu-item v-if="isAdmin" index="/health/simulation-control">
          <el-icon><Odometer /></el-icon>
          <span>IoT 设备体征仿真 (M10)</span>
        </el-menu-item>
        <el-menu-item v-if="isAdmin" index="/medication/plan-monitor">
          <el-icon><FirstAidKit /></el-icon>
          <span>长辈用药档案与依从性</span>
        </el-menu-item>
        <el-menu-item v-if="isAdmin" index="/order/dispatch-desk">
          <el-icon><Tickets /></el-icon>
          <span>助老工单调度大厅</span>
        </el-menu-item>
        <el-menu-item index="/order/execution-review">
          <el-icon><Checked /></el-icon>
          <span>{{ executionMenuLabel }}</span>
        </el-menu-item>
        <el-menu-item v-if="isAdmin" index="/staff/roles">
          <el-icon><User /></el-icon>
          <span>工作人员与角色</span>
        </el-menu-item>
        <el-menu-item v-if="isAdmin" index="/audit/trail-center">
          <el-icon><Lock /></el-icon>
          <span>全局审计与合规追溯</span>
        </el-menu-item>
      </el-menu>

      <div class="aside-footer">
        <span class="badge-tag">本地联调版</span>
        <span class="version-txt">部分模块待接真实接口</span>
      </div>
    </el-aside>

    <!-- 主体区域 -->
    <el-container>
      <!-- 顶部 Header -->
      <el-header height="60px" class="layout-header">
        <div class="header-left">
          <el-tag effect="plain" type="info" class="community-tag">
            当前社区 ID：{{ currentUser?.communityId || '未分配' }}
          </el-tag>
        </div>
        <div class="header-right">
          <el-avatar size="small" style="background-color: #0284c7">
            {{ currentUser?.phone?.slice(-1) || '管' }}
          </el-avatar>
          <span class="user-name">{{ currentUser?.phone || '当前用户' }} ({{ currentUser?.currentRole || '角色未确定' }})</span>
          <el-button link type="danger" size="small" @click="handleLogout">
            退出登录
          </el-button>
        </div>
      </el-header>

      <!-- 内容主体 -->
      <el-main class="layout-main">
        <el-alert title="部分页面仍在接入真实数据；请以实际接口返回结果为准" type="warning" :closable="false" show-icon />
        <router-view />
      </el-main>
    </el-container>

  </el-container>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { User, Connection, DocumentChecked, Bell, Odometer, FirstAidKit, Tickets, Checked, DataAnalysis, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../api/adminApi'

const route = useRoute()
const router = useRouter()
const activeRoute = computed(() => route.path)
type LayoutUser = { phone?: string; communityId?: string | number | null; currentRole?: string }

function readCurrentUser(): LayoutUser | null {
  try { return JSON.parse(localStorage.getItem('user') || 'null') as { phone?: string; communityId?: string; currentRole?: string } | null }
  catch { return null }
}

const currentUser = ref<LayoutUser | null>(readCurrentUser())
watch(() => route.fullPath, () => {
  currentUser.value = readCurrentUser()
})
const isAdmin = computed(() => currentUser.value?.currentRole === 'COMMUNITY_ADMIN')
const isWorker = computed(() => ['GRID_WORKER', 'CARE_WORKER'].includes(currentUser.value?.currentRole || ''))
const executionMenuLabel = computed(() => {
  if (currentUser.value?.currentRole === 'CARE_WORKER') return '我的护工工单'
  if (isWorker.value) return '我的执行任务'
  return '服务登记与结案审核'
})

async function handleLogout() {
  try {
    await adminApi.logout()
    ElMessage.success('已退出工作台')
  } catch {
    ElMessage.warning('退出请求未完成，请检查网络后重试')
    return
  }
  localStorage.removeItem('user')
  localStorage.removeItem('token')
  localStorage.removeItem('activeRole')
  localStorage.removeItem('csrfToken')
  currentUser.value = null
  router.push('/login')
}
</script>

<style scoped>
.layout-container {
  min-height: 100vh;
}

.aside-menu {
  background-color: #0f172a;
  display: flex;
  flex-direction: column;
}

.brand-box {
  height: 64px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid #1e293b;
}

.brand-icon {
  font-size: 26px;
}

.brand-text {
  display: flex;
  flex-direction: column;
}

.main-title {
  color: #f8fafc;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.sub-title {
  color: #64748b;
  font-size: 11px;
}

.menu-vertical {
  border-right: none;
  flex: 1;
}

.aside-footer {
  padding: 16px;
  border-top: 1px solid #1e293b;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.badge-tag {
  font-size: 11px;
  color: #38bdf8;
  background: rgba(56, 189, 248, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
}

.version-txt {
  font-size: 11px;
  color: #475569;
}

.layout-header {
  background-color: #ffffff;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.community-tag {
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-name {
  font-size: 14px;
  color: #334155;
  font-weight: 600;
}

.layout-main {
  background-color: #f8fafc;
  padding: 20px;
}
</style>
