const QQ_REGEX = /^\d{5,12}$/

interface ContactProfile {
  wechatId?: string
  qq?: string
}

export function hasContactMethod(profile?: ContactProfile): boolean {
  if (!profile) return false
  const wechatId = (profile.wechatId || '').trim()
  const qq = (profile.qq || '').trim()
  return Boolean(wechatId || QQ_REGEX.test(qq))
}

export function validateContactDraft(profile: ContactProfile): { ok: boolean; message: string } {
  const wechatId = (profile.wechatId || '').trim()
  const qq = (profile.qq || '').trim()
  if (!wechatId && !qq) {
    return { ok: false, message: '请至少填写微信号或QQ号' }
  }
  if (qq && !QQ_REGEX.test(qq)) {
    return { ok: false, message: 'QQ号需为5-12位数字' }
  }
  return { ok: true, message: '' }
}
