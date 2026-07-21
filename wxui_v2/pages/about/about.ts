const FEEDBACK_QQ_GROUP = '1078739008'

Page({
  copyFeedbackQQ() {
    wx.setClipboardData({
      data: FEEDBACK_QQ_GROUP,
      success: () => wx.showToast({ title: '群号已复制', icon: 'success' })
    })
  }
})
