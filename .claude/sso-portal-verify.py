# -*- coding: utf-8 -*-
"""统一门户 SSO 端到端验证：单点登录 / 一键签发 / 统一登出 / 会话管理 / 回归。

模拟两个浏览器（各自的 Cookie jar），把「浏览器里发生的事」如实跑一遍：
  1. 门户授权码登录（走 /oauth/authorize + /oauth/login 表单）
  2. 用门户令牌取子系统清单，逐个走子系统授权码流程（相当于门户里那次「一键登录」）
  3. 统一登出后验证：所有子系统的旧令牌立刻失效、刷新换不出新令牌、浏览器会话被销毁
  4. 管理端会话列表与强制下线
  5. 回归：client_credentials 令牌不受影响、权限点分级生效
"""
import http.cookiejar
import json
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

OAUTH = "http://localhost:7071"
RBAC = "http://localhost:7070"
ORDER = "http://localhost:7073"
MARKETING = "http://localhost:7072"
ORDER_WEB = "http://localhost:5176"
PORTAL_WEB = "http://localhost:5177"

PASS = 0
FAIL = 0


def check(label, actual, expected):
    global PASS, FAIL
    if actual == expected:
        PASS += 1
        print("  [OK]   %-62s %s" % (label, actual))
    else:
        FAIL += 1
        print("  [FAIL] %-62s got=%s want=%s" % (label, actual, expected))


def check_true(label, cond, detail=""):
    global PASS, FAIL
    if cond:
        PASS += 1
        print("  [OK]   %-62s %s" % (label, detail))
    else:
        FAIL += 1
        print("  [FAIL] %-62s %s" % (label, detail))


def redis(*args):
    out = subprocess.run(["redis-cli", "--raw"] + list(args),
                         capture_output=True, text=True, encoding="utf-8", errors="replace")
    return (out.stdout or "").strip()


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def new_browser():
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(NoRedirect(), urllib.request.HTTPCookieProcessor(jar))
    return opener, jar


def go(opener, url, method="GET", form=None, token=None):
    """返回 (status, headers, body)；不自动跟随 302"""
    headers = {}
    data = None
    if form is not None:
        data = urllib.parse.urlencode(form).encode("utf-8")
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with opener.open(req) as r:
            return r.status, dict(r.headers), r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read().decode("utf-8", "replace")


def plain(url, method="GET", form=None, token=None, basic=None, body=None):
    """不关心 Cookie 的普通请求"""
    headers = {}
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    elif form is not None:
        data = urllib.parse.urlencode(form).encode("utf-8")
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    if token:
        headers["Authorization"] = "Bearer " + token
    if basic:
        import base64
        headers["Authorization"] = "Basic " + base64.b64encode(
            ("%s:%s" % basic).encode()).decode()
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as r:
            text = r.read().decode("utf-8", "replace")
            try:
                return r.status, json.loads(text)
            except ValueError:
                return r.status, text
    except urllib.error.HTTPError as e:
        text = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(text)
        except ValueError:
            return e.code, text


def q(value):
    return urllib.parse.quote(value, safe="")


def authorize_url(client_id, client_secret, redirect_uri, scope="read"):
    return ("%s/oauth/authorize?response_type=code&client_id=%s&client_secret=%s"
            "&redirect_uri=%s&scope=%s&state=portal"
            % (OAUTH, q(client_id), q(client_secret), q(redirect_uri), q(scope)))


def code_from_location(location):
    query = urllib.parse.urlparse(location).query
    return urllib.parse.parse_qs(query).get("code", [""])[0]


def check_token(token, client_id, client_secret):
    return plain(OAUTH + "/oauth/check_token", "POST", form={"token": token},
                 basic=(client_id, client_secret))


def rbac_login(username, password):
    st, body = plain(RBAC + "/auth/login", "POST", body={"username": username, "password": password})
    if st != 200 or body.get("code") != 200:
        print("   !! 登录 rbac-backend 失败: %s" % body)
        sys.exit(2)
    return body["data"]["accessToken"]


def portal_login(opener, jar, username, password):
    """门户授权码登录，返回门户 access_token"""
    st, hdr, _ = go(opener, authorize_url("portal", "portal123",
                                          "http://localhost:5177/oauth/callback"))
    if "/login" not in hdr.get("Location", ""):
        print("   !! 未登录访问门户授权地址，预期跳登录页，实际: %s" % hdr.get("Location"))
        sys.exit(2)
    go(opener, OAUTH + "/oauth/login", "POST", {"username": username, "password": password})
    st, hdr, _ = go(opener, authorize_url("portal", "portal123",
                                          "http://localhost:5177/oauth/callback"))
    code = code_from_location(hdr.get("Location", ""))
    if not code:
        print("   !! 门户授权未拿到 code，跳转: %s" % hdr.get("Location"))
        sys.exit(2)
    st, body = plain(RBAC + "/portal/token", "POST", body={"code": code})
    if body.get("code") != 200:
        print("   !! 门户换令牌失败: %s" % body)
        sys.exit(2)
    return body["data"]["accessToken"]


def subsystem_login(opener, start_url, backend, endpoint):
    """子系统的授权码流程。

    start_url 可能是后端的 302 端点（/portal/sso-login/{code}，一键登录的 iframe 走的就是它），
    也可能直接是授权地址 —— 前者要再跟一跳才到授权端点。
    """
    st, hdr, _ = go(opener, start_url)
    loc = hdr.get("Location", "")
    if "oauth/authorize" in loc:
        st, hdr, _ = go(opener, loc)
        loc = hdr.get("Location", "")
    code = code_from_location(loc)
    if not code:
        print("   !! 子系统授权未拿到 code，HTTP %s 跳转: %s" % (st, loc[:150]))
        return None
    st, body = plain(backend + endpoint, "POST", body={"code": code})
    if body.get("code") != 200:
        print("   !! 子系统换令牌失败: %s" % body)
        return None
    return body["data"]


print("=" * 88)
print("第 0 步：清掉可能残留的 SSO 会话，保证可重复执行")
print("=" * 88)
for key in ("sso:session:admin", "sso:browser:admin", "sso:uid:1",
            "sso:session:zhangsan", "sso:browser:zhangsan", "sso:uid:2"):
    redis("del", key)
# 登录态是 oauth2:auth:{token}：上一轮遗留的也要清掉，否则下面「只有一条」的断言会被旧数据干扰
for key in redis("keys", "oauth2:auth:*").split("\n"):
    if key.strip():
        redis("del", key.strip())
check("残留清理后 sso:session:admin 为空", redis("get", "sso:session:admin"), "")

print()
print("=" * 88)
print("第 1 步：单点登录 —— 门户授权码登录")
print("=" * 88)
browser, jar = new_browser()
portal_token = portal_login(browser, jar, "admin", "admin123")
check_true("门户拿到 access_token", bool(portal_token), portal_token[:20] + "...")

# 登录态存哪：直接是 Redis 里一条精简的认证信息，而不是 Spring Session 序列化出来的会话对象
auth_keys = [k for k in redis("keys", "oauth2:auth:*").split("\n") if k.strip()]
check("登录成功后写了一条 oauth2:auth:{token}", len(auth_keys), 1)
auth_json = redis("get", auth_keys[0]) if auth_keys else ""
check_true("内容是能看懂的 JSON，只含认证信息（userId / username / nickname / loginTime）",
           '"userId":1' in auth_json and '"username":"admin"' in auth_json
           and '"nickname"' in auth_json and '"loginTime"' in auth_json,
           auth_json[:120])
check_true("不是序列化后的 SecurityContext",
           "SPRING_SECURITY_CONTEXT" not in auth_json and "java.util" not in auth_json,
           "ok")
check("完全没有 Spring Session 的键（rbac:session:* 为空）",
      [k for k in redis("keys", "rbac:session:*").split("\n") if k.strip()], [])
auth_token = auth_keys[0].split("oauth2:auth:")[-1] if auth_keys else ""
check("浏览器登录态索引指向同一个 token", redis("get", "sso:browser:admin"), auth_token)
check_true("浏览器会话已被记录（sso:browser:admin）", bool(redis("get", "sso:browser:admin")),
           redis("get", "sso:browser:admin")[:16] + "...")

st, body = plain(RBAC + "/portal/info", token=portal_token)
check("门户 info 的 clients 已含 portal", body["data"]["clients"], ["portal"])
check("门户 info 的用户名", body["data"]["username"], "admin")
check("门户 info 的昵称", body["data"]["nickname"], "超级管理员")

st, body = plain(RBAC + "/portal/subsystems", token=portal_token)
subsystems = body["data"]
check("子系统清单数量", len(subsystems), 2)
check("每个子系统都是 ready", sorted(s["ready"] for s in subsystems), [True, True])
check("子系统编码", sorted(s["code"] for s in subsystems), ["marketing", "order-center"])
# 接口**不再**下发授权地址：那个地址带 client_secret，只能由后端持有并 302 出去
check("子系统清单里没有任何带密钥的授权地址",
      [k for s in subsystems for k in s if k == "authorizeUrl"], [])
check_true("清单正文里不出现 client_secret",
           "client_secret" not in json.dumps(subsystems), json.dumps(subsystems)[:90] + "...")
# 一键登录用的那一跳：后端拼好授权地址并 302，前端只拿得到 302 响应
st, hdr, _ = go(new_browser()[0], RBAC + "/portal/sso-login/order-center")
check("GET /portal/sso-login/order-center 是 302", st, 302)
check_true("302 的 Location 指向授权端点且带 client_secret",
           "/oauth/authorize?" in hdr.get("Location", "")
           and "client_secret=" in hdr.get("Location", ""),
           hdr.get("Location", "")[:80])
st, hdr, _ = go(new_browser()[0], RBAC + "/portal/sso-login/not-exist")
check("未登记的子系编码 -> 404（不是开放重定向）", st, 404)
# 子系统没有登录页，未登录时跳「门户/login?redirect=<自己的源>」，门户登录后靠 entryUrl 比对出是谁
check("每个子系统都有入口地址（门户据此把用户送回他来的系统）",
      sorted(s["entryUrl"] for s in subsystems),
      ["http://localhost:5175", "http://localhost:5176"])

st, body = plain(RBAC + "/portal/subsystems")
check("不带门户令牌访问子系统清单 -> 401", st, 401)

print()
print("=" * 88)
print("第 2 步：一键登录所有子系统（脚本里等价于逐个走子系统授权码流程）")
print("=" * 88)
tokens = {}
for sysinfo in sorted(subsystems, key=lambda s: s["code"]):
    backend = ORDER if sysinfo["code"] == "order-center" else MARKETING
    data = subsystem_login(browser, RBAC + "/portal/sso-login/" + sysinfo["code"],
                           backend, "/auth/code")
    check_true("「%s」静默换到令牌（无登录页、无确认页）" % sysinfo["name"], data is not None)
    if data:
        tokens[sysinfo["code"]] = data

st, body = plain(RBAC + "/portal/info", token=portal_token)
check("SSO 会话的 clients 已含全部三个客户端",
      sorted(body["data"]["clients"]), ["marketing", "order-center", "portal"])

session_json = redis("get", "sso:session:admin")
check_true("Redis 里 sso:session:admin 已是合法 JSON 且含 userId",
           session_json.startswith("{") and '"userId":1' in session_json, session_json[:80] + "...")
check("Redis 里 sso:uid:1 反查索引", redis("get", "sso:uid:1"), "admin")

order_token = tokens["order-center"]["accessToken"]
check("订单中心令牌可用（/order/stat）",
      plain(ORDER + "/order/stat", token=order_token)[1].get("code"), 200)

print()
print("=" * 88)
print("第 3 步：统一登出 —— 旧令牌立刻失效")
print("=" * 88)
before = [(repo, tok) for repo, tok in
          (("order-center", tokens["order-center"]["accessToken"]),
           ("marketing", tokens["marketing"]["accessToken"]))]
check("登出前两个子系统的令牌都有效",
      [check_token(t, c, s)[0] for (c, t), s in
       zip(before, ("order123", "marketing123"))], [200, 200])

st, hdr, _ = go(browser, OAUTH + "/oauth/sso-logout?redirect_uri="
                + q("http://localhost:5176/login"))
check("统一登出（整页跳转）返回 302", st, 302)
check("登出后跳回子系统登录页（redirect_uri 在已登记来源内）",
      hdr.get("Location"), "http://localhost:5176/login")

check("sso:session:admin 已被删除", redis("get", "sso:session:admin"), "")
check("sso:browser:admin 已被删除", redis("get", "sso:browser:admin"), "")
check("sso:uid:1 已被删除", redis("get", "sso:uid:1"), "")

for code, secret in (("order-center", "order123"), ("marketing", "marketing123")):
    st, _ = check_token(tokens[code]["accessToken"], code, secret)
    check("%s 的旧访问令牌 check_token -> 400" % code, st, 400)

check("订单中心接口用旧令牌 -> 401", plain(ORDER + "/order/stat", token=order_token)[0], 401)

st, body = plain(OAUTH + "/oauth/token", "POST",
                 form={"grant_type": "refresh_token",
                       "refresh_token": tokens["order-center"]["refreshToken"]},
                 basic=("order-center", "order123"))
check("用旧刷新令牌续期 -> 400 invalid_grant", (st, body.get("error")), (400, "invalid_grant"))
check_true("拒绝续期的原因是会话已结束", "会话" in (body.get("error_description") or ""),
           body.get("error_description"))

# 前端实际撞到的是子系统自己的 /auth/refresh（order-frontend 的 401 拦截器会调它续期）。
# 它必须也失败，前端才会清凭证跳登录页 —— 而不是拿着一张废令牌一直重试。
st, body = plain(ORDER + "/auth/refresh", "POST",
                 body={"refreshToken": tokens["order-center"]["refreshToken"]})
check_true("订单中心的 /auth/refresh 也拒绝续期（前端据此跳登录页）",
           body.get("code") != 200, body.get("message"))

st, hdr, _ = go(browser, authorize_url("order-center", "order123",
                                       "http://localhost:5176/oauth/callback"))
check_true("浏览器会话已销毁：再发起授权要重新登录",
           "/login" in hdr.get("Location", ""), hdr.get("Location", "")[:80])

print()
print("=" * 88)
print("第 4 步：回归 —— 无用户流程不受影响")
print("=" * 88)
st, body = plain(OAUTH + "/oauth/token", "POST",
                 form={"grant_type": "client_credentials", "scope": "read"},
                 basic=("rbac-third-party", "client123"))
cc_token = body.get("access_token")
check_true("client_credentials 仍能签发令牌", bool(cc_token))
st, _ = check_token(cc_token, "rbac-third-party", "client123")
check("client_credentials 令牌 check_token 仍为 200（统一登出不该影响它）", st, 200)

# 机器身份没有用户认证，OAuth2Authentication#getName() 会退化成客户端 id
# —— 若拿它当用户名去查 sso:session:，机器令牌会被误判成「已登出」。
# 这里连刷新路径一起验，覆盖刚修掉的那个坑。
if body.get("refresh_token"):
    st, body2 = plain(OAUTH + "/oauth/token", "POST",
                      form={"grant_type": "refresh_token",
                            "refresh_token": body["refresh_token"]},
                      basic=("rbac-third-party", "client123"))
    check("client_credentials 的刷新令牌仍可用（不被误判为已登出）",
          (st, bool(body2.get("access_token"))), (200, True))
else:
    check_true("client_credentials 未签发刷新令牌（无需校验该路径）", True, "无 refresh_token")

print()
print("=" * 88)
print("第 5 步：会话管理 —— 列表与强制下线")
print("=" * 88)
admin_token = rbac_login("admin", "admin123")

browser2, _ = new_browser()
portal_token_2 = portal_login(browser2, None, "zhangsan", "admin123")
zs_order = subsystem_login(browser2, RBAC + "/portal/sso-login/order-center",
                           ORDER, "/auth/code")
check_true("zhangsan 也完成了门户 + 订单中心登录", zs_order is not None)
check("zhangsan 的订单中心令牌可用",
      plain(ORDER + "/order/stat", token=zs_order["accessToken"])[1].get("code"), 200)

st, body = plain(RBAC + "/sso/session", token=admin_token)
usernames = sorted(s["username"] for s in body["data"])
check("admin 能看到在线会话列表", usernames, ["zhangsan"])
zs = [s for s in body["data"] if s["username"] == "zhangsan"][0]
check("列表里有 userId", zs["userId"], 2)
check("列表里显示已登录的客户端", zs["clients"], ["portal", "order-center"])
check_true("列表里有登录时间", bool(zs["loginTime"]), zs["loginTime"])

st, body = plain(RBAC + "/sso/session/2", "DELETE", token=admin_token)
check("强制下线返回 200", body.get("code"), 200)
check("zhangsan 的订单中心令牌立刻 401",
      plain(ORDER + "/order/stat", token=zs_order["accessToken"])[0], 401)
check("zhangsan 的 SSO 会话已删除", redis("get", "sso:session:zhangsan"), "")
check("zhangsan 的浏览器会话索引已删除", redis("get", "sso:browser:zhangsan"), "")

# 踢人必须连浏览器会话一起销毁：只删令牌会话的话，残留的 SESSION 能静默重走一遍授权码、
# 换出新令牌，而签发新令牌会重建 SSO 会话 —— 踢人当场失效。
check("被踢后：他的门户令牌也一并失效（同一层 SSO 会话）",
      plain(RBAC + "/portal/info", token=portal_token_2)[0], 401)
st, hdr, _ = go(browser2, authorize_url("order-center", "order123",
                                        "http://localhost:5176/oauth/callback"))
check_true("被踢后：浏览器会话已销毁，再发起授权要重新登录",
           "/login" in hdr.get("Location", ""), hdr.get("Location", "")[:80])

st, body = plain(RBAC + "/sso/session", token=admin_token)
check("会话列表已不含 zhangsan", [s["username"] for s in body["data"]], [])

print()
print("=" * 88)
print("第 6 步：登出的边界")
print("=" * 88)
# 开放重定向防护：redirect_uri 只认已登记客户端的来源
st, hdr, _ = go(new_browser()[0], OAUTH + "/oauth/sso-logout?redirect_uri="
                + q("http://evil.example.com/steal"))
check_true("redirect_uri 不在已登记来源内 -> 不跳外部站",
           "evil.example.com" not in hdr.get("Location", ""), hdr.get("Location", ""))
check("...而是退回授权服务的登录页（没有可跳的地方）",
      hdr.get("Location"), "http://localhost:5174/login")

# 子系统登出后落在门户登录页（各子系统前端 utils/sso.js 就是这么配的）。
# 那个来源必须在授权服务的白名单里，否则会被静默退回 5174 —— 用户就看不到统一入口了。
st, hdr, _ = go(new_browser()[0], OAUTH + "/oauth/sso-logout?redirect_uri="
                + q("http://localhost:5177/login"))
check("登出后允许跳回门户登录页（5177 是已登记客户端的来源）",
      hdr.get("Location"), "http://localhost:5177/login")

# POST 版（XHR）返回 JSON，不跳转
st, _, body = go(new_browser()[0], OAUTH + "/oauth/sso-logout", "POST")
check("POST /oauth/sso-logout 返回 200", st, 200)
check_true("POST 版返回 JSON 而非 302", body.strip().startswith("{"), body[:70])

# 幂等：重复登出不报错
st, _, body = go(new_browser()[0], OAUTH + "/oauth/sso-logout", "POST")
check("重复登出仍然 200（幂等）", st, 200)

zs_admin_token = rbac_login("zhangsan", "admin123")
st, body = plain(RBAC + "/sso/session", token=zs_admin_token)
check("普通角色有 system:sso:list：能看列表", body.get("code"), 200)
st, body = plain(RBAC + "/sso/session/1", "DELETE", token=zs_admin_token)
check("普通角色没有 system:sso:kick：踢人 -> 403", st, 403)

print()
print("=" * 88)
print("第 7 步：两种登录方式的登出都必须清干净")
print("=" * 88)
print("（这一步守的是真实踩过的坑：从营销系统登出后，订单中心仍然进得去）")
print()
print("-- A. 密码模式登录：浏览器里【没有】授权服务的 SESSION --")
for key in ("sso:session:admin", "sso:browser:admin", "sso:uid:1"):
    redis("del", key)

browser3, _ = new_browser()
st, body = plain(MARKETING + "/auth/login", "POST", body={"username": "admin", "password": "admin123"})
mk_pw_token = body["data"]["accessToken"]
st, body = plain(ORDER + "/auth/login", "POST", body={"username": "admin", "password": "admin123"})
od_pw_token = body["data"]["accessToken"]
check("两个子系统都拿到了令牌（订单中心接口 200）",
      plain(ORDER + "/order/stat", token=od_pw_token)[1].get("code"), 200)
check("同一次登录已记入 SSO 会话", redis("get", "sso:uid:1"), "admin")

st, hdr, _ = go(browser3, authorize_url("marketing", "marketing123",
                                        "http://localhost:5175/oauth/callback"))
check_true("密码模式登录的用户，浏览器里确实没有授权服务的 SESSION",
           "/login" in hdr.get("Location", ""), hdr.get("Location", "")[:70])

# 只做整页跳转（最初的实现）：请求既没 Authorization 头也没 Cookie，认不出是谁在登出
go(browser3, OAUTH + "/oauth/sso-logout?redirect_uri=" + q("http://localhost:5175/login"))
check("只跳转：SSO 会话原封不动（认不出人，什么也没删）", redis("get", "sso:uid:1"), "admin")
check("只跳转：订单中心旧令牌仍可用 —— 这正是「登出没生效」的原因",
      plain(ORDER + "/order/stat", token=od_pw_token)[0], 200)

# 补上带令牌的 POST（现在的实现：fetch 那一步）
st, _, _ = go(browser3, OAUTH + "/oauth/sso-logout", "POST", token=mk_pw_token)
check("补上带令牌的 POST -> 200", st, 200)
check("两步之后 SSO 会话已删除", redis("get", "sso:uid:1"), "")
check("订单中心旧令牌立刻 401", plain(ORDER + "/order/stat", token=od_pw_token)[0], 401)
# 前端「切回标签页就校验一次」用的就是 /auth/info（见各前端 utils/sessionWatch.js）：
# 这条断言保证那个探针在会话被撤销后确实回 401，前端才会被拦到登录页
check("切回标签页的探针 GET /auth/info -> 401（前端据此跳登录页）",
      plain(ORDER + "/auth/info", token=od_pw_token)[0], 401)
check("营销系统自己的令牌也失效",
      check_token(mk_pw_token, "marketing", "marketing123")[0], 400)

print()
print("-- B. 授权码模式登录：浏览器里有 SESSION，跳转一步即可认人 --")
browser4, _ = new_browser()
st, hdr, _ = go(browser4, authorize_url("marketing", "marketing123",
                                        "http://localhost:5175/oauth/callback"))
go(browser4, OAUTH + "/oauth/login", "POST", {"username": "admin", "password": "admin123"})
st, hdr, _ = go(browser4, authorize_url("marketing", "marketing123",
                                        "http://localhost:5175/oauth/callback"))
code = code_from_location(hdr.get("Location", ""))
st, body = plain(MARKETING + "/auth/code", "POST", body={"code": code})
mk_code_token = body["data"]["accessToken"]
st, body = plain(ORDER + "/auth/login", "POST", body={"username": "admin", "password": "admin123"})
od_code_token = body["data"]["accessToken"]
check("授权码模式登录成功，SSO 会话已建立", redis("get", "sso:uid:1"), "admin")

go(browser4, OAUTH + "/oauth/sso-logout?redirect_uri=" + q("http://localhost:5175/login"))
check("有 SESSION 时，跳转一步就能删掉 SSO 会话", redis("get", "sso:uid:1"), "")
check("订单中心旧令牌立刻 401", plain(ORDER + "/order/stat", token=od_code_token)[0], 401)
check("浏览器会话索引已清理", redis("get", "sso:browser:admin"), "")
check("营销系统令牌也已失效",
      check_token(mk_code_token, "marketing", "marketing123")[0], 400)

print()
print("=" * 88)
print("第 8 步：门户自身的登录链路 + 一键登录用的子系统授权地址")
print("=" * 88)
print("（门户是独立入口，不在子系统的登录链路上；这里验它自己的登录，")
print("  以及它给「一键登录」准备的那些子系统授权地址确实可用）")
print()

for key in ("sso:session:admin", "sso:browser:admin", "sso:uid:1"):
    redis("del", key)

# ① 打开门户：真实浏览器会落到门户自己的入口页（一个「统一登录」按钮）
browser5, _ = new_browser()
# 注意是**前端**的 /login（5176），不是后端的 —— 后端 7073 上没有这个路径
st, hdr, body = go(browser5, ORDER_WEB + "/login")
check_true("订单中心 /login 仍是前端页面（它现在是跳转页，不是表单）",
           st == 200 and 'id="app"' in body, "HTTP %s" % st)

# ② 门户登录由**后端**发起：GET /portal/sso-login 拼好带密钥的授权地址并 302 出去。
#    接口本身不返回地址 —— 前端拿不到 client_secret。
st, body = plain(RBAC + "/portal/login-url")
check("旧的 /portal/login-url 已下线（不再把带密钥的地址交给前端）", st, 401)

st, hdr, _ = go(browser5, RBAC + "/portal/sso-login")
portal_login_url = hdr.get("Location", "")
check("GET /portal/sso-login 是 302", st, 302)
check_true("302 指向授权端点且带 client_secret（地址只存在于 302 响应头里）",
           "/oauth/authorize?" in portal_login_url and "client_secret" in portal_login_url,
           portal_login_url[:80])

# ③ 走完门户的授权码登录（等价于用户在 5174 输入账号密码）
st, hdr, _ = go(browser5, portal_login_url)
check_true("未登录时门户授权地址 302 到 5174 登录页",
           "/login" in hdr.get("Location", ""), hdr.get("Location", "")[:60])
go(browser5, OAUTH + "/oauth/login", "POST", {"username": "admin", "password": "admin123"})
st, hdr, _ = go(browser5, portal_login_url)
code = code_from_location(hdr.get("Location", ""))
check_true("门户拿到 code", bool(code))
st, body = plain(RBAC + "/portal/token", "POST", body={"code": code})
portal_token5 = body["data"]["accessToken"]
check("门户换到令牌", body.get("code"), 200)

# ④ 门户据「来处」从子系统清单里比对出是谁，拿到它的授权地址
st, body = plain(RBAC + "/portal/subsystems", token=portal_token5)
matched = [s for s in body["data"] if s["entryUrl"] == "http://localhost:5176"]
check_true("门户能从子系统清单里比对出「http://localhost:5176 是订单中心」",
           len(matched) == 1 and matched[0]["code"] == "order-center",
           [s["code"] for s in body["data"]])
# ⑤ 把浏览器送到那个子系统的 302 端点（一键登录的 iframe 就是指向它）：
#    后端拼好授权地址后 302，授权端点再凭同一个 SESSION 静默发码（不弹登录页、不弹确认页）
st, hdr, _ = go(browser5, RBAC + "/portal/sso-login/" + matched[0]["code"])
check("子系统的 302 端点响应", st, 302)
st, hdr, _ = go(browser5, hdr.get("Location", ""))
loc = hdr.get("Location", "")
check("凭门户建立的 SESSION 直接发码（303，无登录页、无确认页）", st, 303)
check_true("码发到订单中心的回调页", "localhost:5176/oauth/callback" in loc and "code=" in loc,
           loc[:70])

# ⑥ 订单中心回调页把 code 交给自己的后端换令牌，用户就落在订单中心
code = code_from_location(loc)
st, body = plain(ORDER + "/auth/code", "POST", body={"code": code})
order_token5 = body["data"]["accessToken"]
check("订单中心换到令牌", body.get("code"), 200)
check("订单中心接口可用（用户落在他本来要去的系统里）",
      plain(ORDER + "/order/stat", token=order_token5)[1].get("code"), 200)

# ⑦ 整条链路只登录了一次：SSO 会话里同时记着门户与订单中心
st, body = plain(RBAC + "/portal/info", token=portal_token5)
check("SSO 会话记录了两个客户端（一次登录，两处生效）",
      sorted(body["data"]["clients"]), ["order-center", "portal"])
check("全程没有用到密码模式：浏览器里确实存在授权服务的 SESSION",
      redis("get", "sso:browser:admin") != "", True)

for key in ("sso:session:admin", "sso:browser:admin", "sso:uid:1"):
    redis("del", key)

print()
print("=" * 88)
print("结果：%d 项通过，%d 项失败" % (PASS, FAIL))
print("=" * 88)
sys.exit(1 if FAIL else 0)
