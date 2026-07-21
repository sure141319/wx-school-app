import { getBaseUrl } from './config/env'
import { request } from './utils/request'

const ANNOUNCEMENT_READ_STORAGE_KEY = 'announcementReadState'
const BEIJING_UTC_OFFSET_MINUTES = 8 * 60

interface AnnouncementPublic {
  title: string
  content: string
  revision: number
}

interface AnnouncementReadState {
  date: string
  revision: number
}

interface AnnouncementPopupOpenOptions {
  title: string
  content: string
  onIgnore: () => void
  onRead: () => void
}

interface AnnouncementPopupInstance {
  open(options: AnnouncementPopupOpenOptions): void
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

    const popup = await waitForAnnouncementPopup()
    if (!popup) return

    announcementModalVisible = true
    popup.open({
      title: announcement.title,
      content: announcement.content,
      onIgnore: () => {
        announcementModalVisible = false
      },
      onRead: () => {
        try {
          wx.setStorageSync(ANNOUNCEMENT_READ_STORAGE_KEY, {
            date: today,
            revision: announcement.revision
          } as AnnouncementReadState)
        } finally {
          announcementModalVisible = false
        }
      }
    })
  } catch (_error) {
    // 公告加载失败不应阻塞小程序的正常使用。
  } finally {
    announcementChecking = false
  }
}

function localDateKey(date: Date): string {
  const beijingDate = new Date(date.getTime() + BEIJING_UTC_OFFSET_MINUTES * 60 * 1000)
  const year = beijingDate.getUTCFullYear()
  const month = String(beijingDate.getUTCMonth() + 1).padStart(2, '0')
  const day = String(beijingDate.getUTCDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

async function waitForAnnouncementPopup(): Promise<AnnouncementPopupInstance | null> {
  for (let attempt = 0; attempt < 8; attempt += 1) {
    const popup = getCurrentAnnouncementPopup()
    if (popup) return popup
    await delay(50)
  }
  return null
}

function getCurrentAnnouncementPopup(): AnnouncementPopupInstance | null {
  const pages = getCurrentPages()
  const currentPage = pages[pages.length - 1]
  if (!currentPage) return null

  const popup = currentPage.selectComponent('#announcementPopup') as unknown as AnnouncementPopupInstance | undefined
  return popup && typeof popup.open === 'function' ? popup : null
}

function delay(milliseconds: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, milliseconds))
}
