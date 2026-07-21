interface AnnouncementPopupOpenOptions {
  title: string
  content: string
  onIgnore: () => void
  onRead: () => void
}

interface AnnouncementPopupInstanceState {
  ignoreAction?: (() => void) | null
  readAction?: (() => void) | null
  setData(data: Record<string, unknown>): void
}

Component({
  data: {
    visible: false,
    title: '',
    content: ''
  },

  methods: {
    open(options: AnnouncementPopupOpenOptions) {
      const popup = this as unknown as AnnouncementPopupInstanceState
      popup.ignoreAction = options.onIgnore
      popup.readAction = options.onRead
      this.setData({
        visible: true,
        title: options.title,
        content: options.content
      })
    },

    handleIgnore() {
      if (!this.data.visible) return
      const popup = this as unknown as AnnouncementPopupInstanceState
      const action = popup.ignoreAction
      clearActions(popup)
      this.setData({ visible: false })
      action?.()
    },

    handleRead() {
      if (!this.data.visible) return
      const popup = this as unknown as AnnouncementPopupInstanceState
      const action = popup.readAction
      clearActions(popup)
      this.setData({ visible: false })
      action?.()
    },

    preventInteraction() {
      // 阻止点击和滑动穿透到弹窗后的页面。
    }
  }
})

function clearActions(popup: AnnouncementPopupInstanceState): void {
  popup.ignoreAction = null
  popup.readAction = null
}
