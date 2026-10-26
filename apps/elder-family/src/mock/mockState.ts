import { reactive, ref } from 'vue'

export type ElderStatusMode = 'NORMAL' | 'CODE_EXPIRED' | 'LOCKED' | 'SUCCESS'
export type FamilyStatusMode = 'ACTIVE' | 'PENDING' | 'FROZEN' | 'REJECTED' | 'EMPTY'

export interface ElderItem {
  id: string
  name: string
  age: number
  idCardMasked: string
  archiveNo: string
  relation: string
  status: FamilyStatusMode
  statusDesc: string
  phone: string
  address: string
  emergencyContact: {
    name: string
    relation: string
    phone: string
  }
}

export const mockState = reactive({
  // 数据源驱动模式 (默认支持一键切换真实后端 API 127.0.0.1:8080)
  useRealApi: false,

  // 当前模拟的登录身份
  currentRole: 'ROLE_SELECT' as 'ROLE_SELECT' | 'ELDER' | 'FAMILY',
  
  // 老人端 6 位激活码页面调试状态
  elderMode: 'NORMAL' as ElderStatusMode,
  elderLockMinutesRemaining: 15,
  elderWrongTries: 2, // 剩余尝试次数
  
  // 子女端多长辈上下文调试状态
  familyMode: 'ACTIVE' as FamilyStatusMode,
  selectedElderId: 'E101',
  
  // 并发冲突 (409) 模拟开关 (FZ-01)
  has409Conflict: false,

  // 长辈列表数据
  elders: [
    {
      id: 'E101',
      name: '张建国',
      age: 78,
      idCardMasked: '32010219480312****',
      archiveNo: 'ARC-NJ-2026-0891',
      relation: '父亲',
      status: 'ACTIVE',
      statusDesc: '已核验生效中',
      phone: '13800138000',
      address: '玄武区后宰门街道北安门社区5栋201室',
      emergencyContact: {
        name: '张文博',
        relation: '儿子',
        phone: '13912345678'
      }
    },
    {
      id: 'E102',
      name: '李秀英',
      age: 75,
      idCardMasked: '32010619510722****',
      archiveNo: 'ARC-NJ-2026-1042',
      relation: '母亲',
      status: 'PENDING',
      statusDesc: '社区居委审核中 (预计2个工作日)',
      phone: '13900139000',
      address: '秦淮区夫子庙街道大石坝街18号',
      emergencyContact: {
        name: '张文博',
        relation: '儿子',
        phone: '13912345678'
      }
    },
    {
      id: 'E103',
      name: '王德祥',
      age: 82,
      idCardMasked: '32010419441105****',
      archiveNo: 'ARC-NJ-2026-0312',
      relation: '岳父',
      status: 'FROZEN',
      statusDesc: '监护争议冻结 (权限临时受限)',
      phone: '13700137000',
      address: '建邺区莫愁湖街道南湖东路8号',
      emergencyContact: {
        name: '王莉',
        relation: '女儿',
        phone: '13612345678'
      }
    }
  ] as ElderItem[]
})

// 辅助方法：设置老人端状态
export function setElderMode(mode: ElderStatusMode) {
  mockState.elderMode = mode
}

// 辅助方法：设置子女端状态
export function setFamilyMode(mode: FamilyStatusMode) {
  mockState.familyMode = mode
  if (mode === 'EMPTY') {
    mockState.selectedElderId = ''
  } else {
    const target = mockState.elders.find(e => e.status === mode)
    if (target) {
      mockState.selectedElderId = target.id
    }
  }
}

// 辅助方法：选择长辈
export function selectElder(id: string) {
  mockState.selectedElderId = id
  const elder = mockState.elders.find(e => e.id === id)
  if (elder) {
    mockState.familyMode = elder.status
  }
}

// 辅助方法：切换并发冲突
export function toggle409Conflict() {
  mockState.has409Conflict = !mockState.has409Conflict
}
