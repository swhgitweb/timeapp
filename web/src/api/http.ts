import axios from 'axios'

/** 统一响应 {code, message, data}，错误码：0 成功 / 40000 参数 / 40400 不存在 / 50000 系统 */
export class ApiError extends Error {
  code: number
  constructor(message: string, code = -1) {
    super(message)
    this.code = code
  }
}

const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

http.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) return body.data
      throw new ApiError(body.message || '请求失败', body.code)
    }
    return body
  },
  (err) => {
    // 后端返回了统一响应体（如 40000 参数错误）时透出真实 message
    const body = err?.response?.data
    if (body && typeof body === 'object' && 'code' in body) {
      throw new ApiError(body.message || '请求失败', body.code)
    }
    // 网络层错误（后端未启动、超时等）
    const message =
      err?.code === 'ECONNABORTED'
        ? '请求超时'
        : err?.response?.status === 404
          ? '接口不存在，请确认后端已启动'
          : '无法连接后端服务'
    throw new ApiError(message, -1)
  },
)

export default http
