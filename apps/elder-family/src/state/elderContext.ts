import { computed, reactive } from 'vue'
import { elderApi } from '../api/elderApi'

export type KinshipStatus = 'ACTIVE' | 'PENDING' | 'FROZEN' | 'REJECTED' | string

export interface BoundElder {
  linkId: number
  elderId: number
  name: string
  archiveNo?: string
  age?: number
  sex?: string
  relationship?: string
  status: KinshipStatus
  freezeReason?: string
  reviewDeadline?: string
  address?: string
  contactPhone?: string
}

const context = reactive({
  elders: [] as BoundElder[],
  selectedElderId: null as number | null,
  loading: false,
  loaded: false,
  errorMessage: '',
})

const activeElders = computed(() => context.elders.filter(elder => elder.status === 'ACTIVE'))
const selectedElder = computed(() =>
  activeElders.value.find(elder => elder.elderId === context.selectedElderId) ?? null
)

function errorMessageFor(error: unknown): string {
  const apiError = error as { message?: string; code?: string; statusCode?: number }
  if (apiError?.statusCode === 401 || apiError?.code === 'UNAUTHORIZED') {
    return '登录状态已失效，请重新登录后查看亲情关系。'
  }
  if (apiError?.code === 'API_BASE_URL_MISSING') {
    return '尚未配置可访问的服务地址，请检查小程序 API 配置。'
  }
  if (apiError?.code === 'NETWORK_ERROR') {
    return '网络连接失败，请检查网络后重试。'
  }
  return apiError?.message || '亲情关系加载失败，请稍后重试。'
}

async function refreshElders(): Promise<boolean> {
  context.loading = true
  context.errorMessage = ''
  try {
    const response = await elderApi.getMyBoundElders()
    if (!Array.isArray(response.data)) {
      throw new Error('亲情关系接口返回格式异常，请稍后重试。')
    }

    const elders = response.data as BoundElder[]
    context.elders = elders
    const stillActive = elders.find(elder => elder.status === 'ACTIVE' && elder.elderId === context.selectedElderId)
    context.selectedElderId = stillActive?.elderId ?? elders.find(elder => elder.status === 'ACTIVE')?.elderId ?? null
    context.loaded = true
    return true
  } catch (error) {
    // Do not keep showing an older elder or an old successful relationship list after a failed refresh.
    context.elders = []
    context.selectedElderId = null
    context.loaded = true
    context.errorMessage = errorMessageFor(error)
    return false
  } finally {
    context.loading = false
  }
}

function selectActiveElder(elderId: number): boolean {
  const elder = activeElders.value.find(item => item.elderId === elderId)
  if (!elder) return false
  context.selectedElderId = elder.elderId
  return true
}

export const elderContext = {
  state: context,
  activeElders,
  selectedElder,
  refresh: refreshElders,
  select: selectActiveElder,
  getErrorMessage: errorMessageFor,
}
