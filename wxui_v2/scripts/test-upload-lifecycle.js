const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const publish = fs.readFileSync(path.join(root, 'pages/publish/publish.ts'), 'utf8')
const profile = fs.readFileSync(path.join(root, 'pages/profile/profile.ts'), 'utf8')
const upload = fs.readFileSync(path.join(root, 'utils/upload.ts'), 'utf8')

function expect(pattern, source, message) {
  if (!pattern.test(source)) throw new Error(message)
}

expect(/deleteStagedImage\(/, upload, 'upload utility must expose staged image deletion')
expect(/outcomes\s*=\s*await Promise\.all/, publish, 'multi-image upload must retain individual outcomes')
expect(/if \(outcome\.ok\) uploaded\.push/, publish, 'successful uploads must remain visible after partial failure')
expect(/removed\?\.staged[\s\S]*deleteStagedImage/, publish, 'removing a staged preview must delete its object')
expect(/onUnload\(\)[\s\S]*cleanupStagedPhotos/, publish, 'abandoned publish pages must clean staged photos')
expect(/closeProfileEditor\(\)[\s\S]*deleteStagedImage\(stagedAvatar\)/, profile, 'discarded avatar drafts must be deleted')
expect(/onUnload\(\)[\s\S]*discardDraftAvatar\(\)/, profile, 'abandoned profile pages must clean staged avatars')

console.log('upload lifecycle checks passed')
