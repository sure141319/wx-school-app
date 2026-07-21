import { getBaseUrl } from './config/env'
import { request } from './utils/request'

const ANNOUNCEMENT_READ_STORAGE_KEY = 'announcementReadState'

interface AnnouncementPublic {
  title: string
  content: string
  revision: number
}

interface AnnouncementReadState {
  date: string
  revision: number
}

let announcementChecking = false
let announcementModalVisible = false

App({
  globalData: {
    userInfo: null as UserProfile | null,
    baseUrl: getBaseUrl()
  },

  onShow() {
    void checkAndShowAnnouncement(this.globalData.baseUrl)
  }
})

async function checkAndShowAnnouncement(baseUrl: string): Promise<void> {
  if (announcementChecking || announcementModalVisible) return
  announcementChecking = true

  try {
    const response = await request<ApiResponse<AnnouncementPublic | null>>({
      url: `${baseUrl}/announcements/current`
    })
    const payload = response.data
    if (response.statusCode !== 200 || !payload || !payload.success || !payload.data) return

    const announcement = payload.data
    const today = localDateKey(new Date())
    const readState = wx.getStorageSync(ANNOUNCEMENT_READ_STORAGE_KEY) as AnnouncementReadState | null
    if (readState && readState.date === today && readState.revision === announcement.revision) return

    announcementModalVisible = true
    wx.showModal({
      title: announcement.title,
      content: announcement.content,
      showCancel: true,
      cancelText: '忽略',
      confirmText: '已读',
      success: (result) => {
        if (result.confirm) {
          wx.setStorageSync(ANNOUNCEMENT_READ_STORAGE_KEY, {
            date: today,
            revision: announcement.revision
          } as AnnouncementReadState)
        }
      },
      complete: () => {
        announcementModalVisible = false
      }
    })
  } catch (_error) {
    // 公告加载失败不应阻塞小程序的正常使用。
  } finally {
    announcementChecking = false
  }
}

function localDateKey(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
