function request(options) {
  return new Promise((resolve, reject) => {
    const token = wx.getStorageSync('token')
    const header = {
      'Content-Type': 'application/json',
      ...options.header
    }
    
    if (token) {
      header['Authorization'] = `Bearer ${token}`
    }

    wx.request({
      url: options.url,
      method: options.method || 'GET',
      data: options.data,
      header,
      success: (res) => {
        if (res.statusCode === 401) {
          wx.removeStorageSync('token')
          wx.removeStorageSync('user')
          wx.redirectTo({ url: '/pages/auth/auth' })
          reject(new Error('未登录'))
          return
        }
        resolve(res)
      },
      fail: (err) => reject(err)
    })
  })
}

module.exports = { request }
