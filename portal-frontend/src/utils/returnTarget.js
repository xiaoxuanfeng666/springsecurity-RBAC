/**
 * 「把用户送回他来的那个子系统」所需的存取
 * <p>
 * 子系统没有登录页，未登录时会跳到 {@code 门户/login?redirect=<子系统的源>}。这个「来处」必须
 * 跨一次 7071 的完整往返（门户授权 → 登录页 → 回调）还能读到，所以存 sessionStorage 而不是内存：
 * 同一标签页内跨域跳转回来它还在，新开标签页则不会互相干扰。
 */
const RETURN_ORIGIN_KEY = 'portal_return_origin'

export function saveReturnOrigin(origin) {
  if (origin) {
    sessionStorage.setItem(RETURN_ORIGIN_KEY, origin)
  }
}

export function clearReturnOrigin() {
  sessionStorage.removeItem(RETURN_ORIGIN_KEY)
}

/** 取出来处并立刻清掉：只应该生效一次，否则用户下次从门户进工作台会被莫名送回子系统 */
export function takeReturnOrigin() {
  const origin = sessionStorage.getItem(RETURN_ORIGIN_KEY)
  sessionStorage.removeItem(RETURN_ORIGIN_KEY)
  return origin
}

/**
 * 取 URL 的「源」（协议 + 主机 + 端口），解析失败返回 null。
 * <p>
 * 只比源、不整串比较：子系统报上来的可能带路径或末尾斜杠，而来源相同就是同一个系统。
 */
export function originOf(url) {
  try {
    return new URL(url).origin
  } catch (e) {
    return null
  }
}
