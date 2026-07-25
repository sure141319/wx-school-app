import { COMMON_MESSAGES } from '../../utils/messages'

const FEEDBACK_QQ_GROUP = '1078739008'

Page({
  copyFeedbackQQ() {
    wx.setClipboardData({
      data: FEEDBACK_QQ_GROUP,
      success: () => wx.showToast({ title: '群号已复制', icon: 'success' }),
      fail: () => wx.showToast({ title: COMMON_MESSAGES.COPY_FAILED, icon: 'none' })
    })
  }
})
