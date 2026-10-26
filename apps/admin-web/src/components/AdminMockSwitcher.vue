<template>
  <div class="admin-switcher-wrap">
    <div class="switcher-pill" @click="isOpen = !isOpen">
      <span class="dot"></span>
      <span class="txt">管理端原型切换器</span>
    </div>

    <el-drawer
      v-model="isOpen"
      title="⚙️ Web 管理端原型调试控制器"
      size="380px"
      direction="rtl"
    >
      <div class="drawer-content">
        <el-alert
          title="纯前端 Mock 驱动"
          type="info"
          description="无需依赖后端 API，点击以下选项可直接测试空态、冲突态和数据重置。"
          show-icon
          :closable="false"
          style="margin-bottom: 20px"
        />

        <div class="ctrl-section">
          <h4>🔌 数据源连接模式</h4>
          <div class="switch-row">
            <span>数据驱动源</span>
            <el-switch
              v-model="adminMockState.useRealApi"
              active-text="真实API (8080)"
              inactive-text="离线Mock"
            />
          </div>
        </div>

        <el-divider />

        <div class="ctrl-section">
          <h4>📊 界面四态快速模拟</h4>
          <div class="switch-row">
            <span>表格空数据状态 (Empty State)</span>
            <el-switch
              v-model="adminMockState.simulateEmpty"
              active-text="空态"
              inactive-text="正常"
            />
          </div>
          <div class="switch-row" style="margin-top: 12px">
            <span>重置所有 Mock 数据为初始状态</span>
            <el-button size="small" type="warning" plain @click="handleReset">
              一键重置
            </el-button>
          </div>
        </div>

        <el-divider />

        <div class="ctrl-section">
          <h4>🧭 全模块页面快捷导航</h4>
          <div class="nav-links">
            <el-button
              type="primary"
              plain
              style="width: 100%; margin-bottom: 8px; justify-content: flex-start"
              @click="navTo('/dashboard/community')"
            >
              📊 M09: 社区态势感知综合大屏
            </el-button>
            <el-button
              type="primary"
              plain
              style="width: 100%; margin-bottom: 8px; margin-left: 0; justify-content: flex-start"
              @click="navTo('/elder/archive')"
            >
              📄 M02: 老人档案建档与激活码
            </el-button>
            <el-button
              type="success"
              plain
              style="width: 100%; margin-bottom: 8px; margin-left: 0; justify-content: flex-start"
              @click="navTo('/family/kinship')"
            >
              🛡️ M02: 亲情绑定核验与争议冻结
            </el-button>
            <el-button
              type="warning"
              plain
              style="width: 100%; margin-bottom: 8px; margin-left: 0; justify-content: flex-start"
              @click="navTo('/elder/correction')"
            >
              📝 M02: 档案更正审批工作台 (FZ-01)
            </el-button>
            <el-button
              type="danger"
              plain
              style="width: 100%; margin-bottom: 8px; margin-left: 0; justify-content: flex-start"
              @click="navTo('/health/emergency-alert')"
            >
              🚨 M05: 预警与 SOS 应急指挥大厅
            </el-button>
            <el-button
              type="info"
              plain
              style="width: 100%; margin-bottom: 8px; margin-left: 0; justify-content: flex-start"
              @click="navTo('/health/simulation-control')"
            >
              🎛️ M10: IoT 设备体征仿真控制台
            </el-button>
            <el-button
              type="warning"
              plain
              style="width: 100%; margin-bottom: 8px; margin-left: 0; justify-content: flex-start"
              @click="navTo('/medication/plan-monitor')"
            >
              💊 M04: 长辈用药档案与依从性监视
            </el-button>
            <el-button
              type="primary"
              plain
              style="width: 100%; margin-bottom: 8px; margin-left: 0; justify-content: flex-start"
              @click="navTo('/order/dispatch-desk')"
            >
              📋 M06: 助老服务工单调度大厅
            </el-button>
            <el-button
              type="success"
              plain
              style="width: 100%; margin-bottom: 8px; margin-left: 0; justify-content: flex-start"
              @click="navTo('/order/execution-review')"
            >
              📷 M08: 服务登记与真实性结案审核
            </el-button>
            <el-button
              type="info"
              plain
              style="width: 100%; margin-left: 0; justify-content: flex-start"
              @click="navTo('/audit/trail-center')"
            >
              🛡️ M12: 全局安全审计与合规追溯
            </el-button>
          </div>
        </div>

        <el-divider />

        <div class="ctrl-section">
          <h4>ℹ️ 业务规则备忘</h4>
          <ul class="rule-list">
            <li><strong>6位激活码</strong>：24h 有效，旧码重签立即作废，24h 最多 3 次。</li>
            <li><strong>争议冻结</strong>：设置为 FROZEN 后启动 5 工作日复核倒计时。</li>
            <li><strong>FZ-01 档案更正</strong>：核验 baseline 快照，若有并发冲突标红阻止批准。</li>
          </ul>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminMockState, resetMockData } from '../mock/adminMock'
import { ElMessage } from 'element-plus'

const router = useRouter()
const isOpen = ref(false)

function handleReset() {
  resetMockData()
  ElMessage.success('已恢复全部初始 Mock 数据')
}

function navTo(path: string) {
  isOpen.value = false
  router.push(path)
}
</script>

<style scoped>
.admin-switcher-wrap {
  position: fixed;
  right: 20px;
  bottom: 24px;
  z-index: 2000;
}

.switcher-pill {
  background: #1f2937;
  color: #fff;
  padding: 8px 16px;
  border-radius: 24px;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.15);
  transition: transform 0.2s;
}

.switcher-pill:hover {
  transform: translateY(-2px);
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 8px #10b981;
}

.txt {
  font-size: 13px;
  font-weight: 600;
}

.switch-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: #374151;
}

.rule-list {
  padding-left: 18px;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.8;
}
</style>
