const QQ_REGEX = /^\d{5,12}$/

type ProfileAvatarSource = 'UPLOADED' | 'QQ' | 'INITIAL' | string | undefined

interface AvatarProfile {
  avatarUrl?: string
  avatarSource?: ProfileAvatarSource
  qq?: string
}

export function buildQqAvatarUrl(qq?: string): string {
  const trimmedQq = (qq || '').trim()
  if (!QQ_REGEX.test(trimmedQq)) {
    return ''
  }
  return `https://q1.qlogo.cn/g?b=qq&nk=${trimmedQq}&s=640`
}

export function resolveProfileDisplayAvatar(profile: AvatarProfile): string {
  const avatarUrl = (profile.avatarUrl || '').trim()
  if (avatarUrl && profile.avatarSource !== 'INITIAL') {
    return avatarUrl
  }
  return buildQqAvatarUrl(profile.qq)
}

export function canUseQqAvatarPreview(profile: AvatarProfile, avatarChanged: boolean): boolean {
  if (avatarChanged) {
    return false
  }
  return profile.avatarSource !== 'UPLOADED'
}

export function resolveQqAvatarPreview(profile: AvatarProfile, qq: string, avatarChanged: boolean): string {
  if (!canUseQqAvatarPreview(profile, avatarChanged)) {
    return ''
  }
  return buildQqAvatarUrl(qq)
}
