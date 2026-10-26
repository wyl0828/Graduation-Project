import { reactive } from 'vue'

export interface ElderArchive {
  id: string
  archiveNo: string
  name: string
  sex: '男' | '女' | '未说明'
  birthDate: string
  idNumberMasked: string
  contactPhone: string
  phoneMissingReason?: string
  address: string
  status: 'ACTIVE' | 'INACTIVE'
  hasAccount: boolean
  currentActivationCode?: {
    code: string
    issuedAt: string
    expireAt: string
    status: 'ISSUED' | 'USED' | 'LOCKED' | 'EXPIRED' | 'SUPERSEDED'
    reissueCount24h: number
    deliveryType: '当面交付老人' | '代理人代领'
    proxyReason?: string
  }
  emergencyContact: {
    name: string
    relation: string
    phone: string
  }
  createdAt: string
}

export interface KinshipApplication {
  id: string
  elderId: string
  elderName: string
  elderArchiveNo: string
  applicantName: string
  applicantPhone: string
  relation: string
  applyReason: string
  status: 'PENDING' | 'ACTIVE' | 'FROZEN' | 'REJECTED' | 'REVOKED'
  freezeReason?: string
  freezeDeadline?: string // 5工作日倒计时
  rejectReason?: string
  revokeReason?: string
  applyTime: string
  auditTime?: string
}

export interface ArchiveCorrection {
  id: string
  elderId: string
  elderName: string
  elderArchiveNo: string
  applicantName: string
  applicantPhone: string
  reason: string
  applyTime: string
  status: 'PENDING_REVIEW' | 'APPROVED_APPLIED' | 'REJECTED'
  rejectReason?: string
  diffItems: {
    fieldKey: string
    fieldName: string
    baselineValue: string // 提交时固化的快照
    currentDbValue: string // 数据库当前最新值
    proposedValue: string // 子女建议修改的新值
  }[]
}

const initialArchives: ElderArchive[] = [
  {
    id: 'E101',
    archiveNo: 'ARC-NJ-2026-0891',
    name: '张建国',
    sex: '男',
    birthDate: '1948-03-12',
    idNumberMasked: '32010219480312001X',
    contactPhone: '13800138000',
    address: '玄武区后宰门街道北安门社区5栋201室',
    status: 'ACTIVE',
    hasAccount: true,
    currentActivationCode: {
      code: '826190',
      issuedAt: '2026-10-25 09:30:00',
      expireAt: '2026-10-26 09:30:00',
      status: 'ISSUED',
      reissueCount24h: 1,
      deliveryType: '当面交付老人'
    },
    emergencyContact: {
      name: '张文博',
      relation: '儿子',
      phone: '13912345678'
    },
    createdAt: '2026-10-20 10:00:00'
  },
  {
    id: 'E102',
    archiveNo: 'ARC-NJ-2026-1042',
    name: '李秀英',
    sex: '女',
    birthDate: '1951-07-22',
    idNumberMasked: '320106195107223841',
    contactPhone: '13900139000',
    address: '秦淮区夫子庙街道大石坝街18号',
    status: 'ACTIVE',
    hasAccount: false,
    currentActivationCode: {
      code: '639145',
      issuedAt: '2026-10-24 15:00:00',
      expireAt: '2026-10-25 15:00:00',
      status: 'ISSUED',
      reissueCount24h: 0,
      deliveryType: '代理人代领',
      proxyReason: '老人行动不便，由独生女张文博持居委会委托书代为领取。'
    },
    emergencyContact: {
      name: '张文博',
      relation: '女儿',
      phone: '13912345678'
    },
    createdAt: '2026-10-22 14:30:00'
  },
  {
    id: 'E103',
    archiveNo: 'ARC-NJ-2026-0312',
    name: '王德祥',
    sex: '男',
    birthDate: '1944-11-05',
    idNumberMasked: '320104194411051234',
    contactPhone: '13700137000',
    address: '建邺区莫愁湖街道南湖东路8号',
    status: 'ACTIVE',
    hasAccount: true,
    currentActivationCode: {
      code: '419208',
      issuedAt: '2026-10-23 08:00:00',
      expireAt: '2026-10-24 08:00:00',
      status: 'EXPIRED',
      reissueCount24h: 2,
      deliveryType: '当面交付老人'
    },
    emergencyContact: {
      name: '王莉',
      relation: '女儿',
      phone: '13612345678'
    },
    createdAt: '2026-09-15 09:20:00'
  },
  {
    id: 'E104',
    archiveNo: 'ARC-NJ-2026-0055',
    name: '陈桂兰',
    sex: '女',
    birthDate: '1940-02-18',
    idNumberMasked: '320103194002189022',
    contactPhone: '',
    phoneMissingReason: '老人系高龄失智独居，无个人手机，由护工公共机代管。',
    address: '玄武区后宰门街道北安门社区2栋102室',
    status: 'INACTIVE',
    hasAccount: false,
    emergencyContact: {
      name: '李建军',
      relation: '外甥',
      phone: '13500135000'
    },
    createdAt: '2026-08-10 11:15:00'
  }
]

const initialKinships: KinshipApplication[] = [
  {
    id: 'K201',
    elderId: 'E102',
    elderName: '李秀英',
    elderArchiveNo: 'ARC-NJ-2026-1042',
    applicantName: '张文博',
    applicantPhone: '13912345678',
    relation: '女儿',
    applyReason: '母亲今年75岁，常住大石坝街，本人作为独女负责其就医与日常生活看护。',
    status: 'PENDING',
    applyTime: '2026-10-25 10:15:00'
  },
  {
    id: 'K202',
    elderId: 'E101',
    elderName: '张建国',
    elderArchiveNo: 'ARC-NJ-2026-0891',
    applicantName: '张文博',
    applicantPhone: '13912345678',
    relation: '儿子',
    applyReason: '与父亲同城居住，负责日常送餐预约与慢病服药打卡监护。',
    status: 'ACTIVE',
    applyTime: '2026-10-20 11:30:00',
    auditTime: '2026-10-20 14:00:00'
  },
  {
    id: 'K203',
    elderId: 'E103',
    elderName: '王德祥',
    elderArchiveNo: 'ARC-NJ-2026-0312',
    applicantName: '王强',
    applicantPhone: '13888889999',
    relation: '继子',
    applyReason: '申请老人名下护理代办及适老化改造申报。',
    status: 'FROZEN',
    freezeReason: '老人生女王莉提出异议，称未委托继子代理，存在监护权争议。',
    freezeDeadline: '2026-10-30 17:00 (剩余 5 个工作日)',
    applyTime: '2026-10-22 09:00:00',
    auditTime: '2026-10-24 16:30:00'
  }
]

const initialCorrections: ArchiveCorrection[] = [
  {
    id: 'COR-2026-01',
    elderId: 'E101',
    elderName: '张建国',
    elderArchiveNo: 'ARC-NJ-2026-0891',
    applicantName: '张文博',
    applicantPhone: '13912345678',
    reason: '长辈已搬家至富贵山小区与子女同住，需更正现居地址与紧急联系人。',
    applyTime: '2026-10-25 11:00:00',
    status: 'PENDING_REVIEW',
    diffItems: [
      {
        fieldKey: 'address',
        fieldName: '现居住地址',
        baselineValue: '玄武区后宰门街道北安门社区5栋201室',
        currentDbValue: '玄武区后宰门街道北安门社区5栋201室', // 当前一致，无冲突
        proposedValue: '玄武区后宰门街道富贵山小区12栋402室'
      },
      {
        fieldKey: 'emergency_phone',
        fieldName: '紧急联系人电话',
        baselineValue: '13912345678',
        currentDbValue: '13912345678',
        proposedValue: '13987654321'
      }
    ]
  },
  {
    id: 'COR-2026-02',
    elderId: 'E102',
    elderName: '李秀英',
    elderArchiveNo: 'ARC-NJ-2026-1042',
    applicantName: '张文博',
    applicantPhone: '13912345678',
    reason: '老人更换了专门的适老老年机号码。',
    applyTime: '2026-10-25 09:40:00',
    status: 'PENDING_REVIEW',
    diffItems: [
      {
        fieldKey: 'contactPhone',
        fieldName: '联系电话',
        baselineValue: '13900139000',
        currentDbValue: '13900139999', // 注意：当前值与 baseline 不一致！409 冲突示例！
        proposedValue: '13900138888'
      }
    ]
  }
]

export const adminMockState = reactive({
  archives: [...initialArchives],
  kinships: [...initialKinships],
  corrections: [...initialCorrections],
  // 模拟空数据开关
  simulateEmpty: false,
  // 模拟加载延迟开关
  simulateLoading: false,
  // 真实后端联调模式开关 (默认开启，自动接入 127.0.0.1:8080 Spring Boot)
  useRealApi: true
})

export function resetMockData() {
  adminMockState.archives = JSON.parse(JSON.stringify(initialArchives))
  adminMockState.kinships = JSON.parse(JSON.stringify(initialKinships))
  adminMockState.corrections = JSON.parse(JSON.stringify(initialCorrections))
  adminMockState.simulateEmpty = false
  adminMockState.useRealApi = true
}
