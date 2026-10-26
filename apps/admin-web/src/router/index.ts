import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '../layout/AdminLayout.vue'
import LoginView from '../views/auth/LoginView.vue'
import { adminApi } from '../api/adminApi'
import ElderArchiveView from '../views/elder/ElderArchiveView.vue'
import KinshipApprovalView from '../views/family/KinshipApprovalView.vue'
import CorrectionApprovalView from '../views/elder/CorrectionApprovalView.vue'
import EmergencyAlertView from '../views/health/EmergencyAlertView.vue'
import SimulationControlView from '../views/health/SimulationControlView.vue'
import MedicationPlanView from '../views/medication/MedicationPlanView.vue'
import WorkOrderDispatchView from '../views/order/WorkOrderDispatchView.vue'
import WorkOrderExecutionView from '../views/order/WorkOrderExecutionView.vue'
import CommunityDashboardView from '../views/dashboard/CommunityDashboardView.vue'
import AuditLogView from '../views/audit/AuditLogView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: LoginView,
      meta: { title: '管理工作台登录', public: true }
    },
    {
      path: '/',
      component: AdminLayout,
      redirect: '/dashboard/community',
      children: [
        {
          path: '/dashboard/community',
          name: 'CommunityDashboard',
          component: CommunityDashboardView,
          meta: { title: '社区态势感知综合大屏' }
        },
        {
          path: '/elder/archive',
          name: 'ElderArchive',
          component: ElderArchiveView,
          meta: { title: '老人档案与激活码管理' }
        },
        {
          path: '/family/kinship',
          name: 'KinshipApproval',
          component: KinshipApprovalView,
          meta: { title: '亲情绑定核验大厅' }
        },
        {
          path: '/elder/correction',
          name: 'CorrectionApproval',
          component: CorrectionApprovalView,
          meta: { title: '档案更正审批工作台' }
        },
        {
          path: '/health/emergency-alert',
          name: 'EmergencyAlert',
          component: EmergencyAlertView,
          meta: { title: '应急指挥与健康预警调度' }
        },
        {
          path: '/health/simulation-control',
          name: 'SimulationControl',
          component: SimulationControlView,
          meta: { title: 'IoT 设备体征仿真控制台' }
        },
        {
          path: '/medication/plan-monitor',
          name: 'MedicationPlan',
          component: MedicationPlanView,
          meta: { title: '长辈用药档案与依从性监视' }
        },
        {
          path: '/order/dispatch-desk',
          name: 'WorkOrderDispatch',
          component: WorkOrderDispatchView,
          meta: { title: '助老服务工单调度大厅' }
        },
        {
          path: '/order/execution-review',
          name: 'WorkOrderExecution',
          component: WorkOrderExecutionView,
          meta: { title: '服务登记与真实性结案审核' }
        },
        {
          path: '/audit/trail-center',
          name: 'AuditLog',
          component: AuditLogView,
          meta: { title: '全局安全审计与合规追溯' }
        }
      ]
    }
  ]
})

router.beforeEach(async (to) => {
  try {
    const current = await adminApi.getCurrentUser()
    if (!current.data || !['COMMUNITY_ADMIN', 'GRID_WORKER'].includes(current.data.currentRole)) {
      localStorage.removeItem('user')
      localStorage.removeItem('csrfToken')
      localStorage.removeItem('activeRole')
      return to.path === '/login' ? true : '/login'
    }
    localStorage.setItem('user', JSON.stringify(current.data))
    const workerHome = '/order/execution-review'
    if (current.data.currentRole === 'GRID_WORKER') {
      return to.path === workerHome ? true : workerHome
    }
    return to.path === '/login' ? '/dashboard/community' : true
  } catch {
    return to.path === '/login' ? true : '/login'
  }
})

export default router
