# -*- coding: utf-8 -*-
"""授权码模式 -> BFF 会话的端到端验证（不用浏览器，按浏览器的方式走 HTTP 跳转）。"""
import http.cookiejar
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

MARKETING = "http://localhost:7072"
ORDER = "http://localhost:7073"
AUDIT = "http://localhost:7074"
RBAC = "http://localhost:7070"
AUTH_WEB = "http://localhost:5174"

PASS = 0
FAIL = 0


def check(name, actual, expected):
    global PASS, FAIL
    if actual == expected:
        PASS += 1
        print("PASS", name)
    else:
        FAIL += 1
        print("FAIL", name, "expected=", expected, "actual=", actual)


class Browser:
    """不跟随跳转，手动看每一步的 Location —— 授权码就在 Location 里。"""

    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.jar),
            _NoRedirect(),
        )

    def get(self, url):
        req = urllib.request.Request(url, method="GET")
        try:
            with self.opener.open(req) as r:
                return r.status, r.headers, r.read()
        except urllib.error.HTTPError as e:
            return e.code, e.headers, e.read()

    def post_form(self, url, fields):
        data = urllib.parse.urlencode(fields).encode()
        req = urllib.request.Request(
            url, data=data, method="POST",
            headers={"Content-Type": "application/x-www-form-urlencoded"})
        try:
            with self.opener.open(req) as r:
                return r.status, r.headers, r.read()
        except urllib.error.HTTPError as e:
            return e.code, e.headers, e.read()

    def post_json(self, url, body):
        data = json.dumps(body).encode()
        req = urllib.request.Request(
            url, data=data, method="POST",
            headers={"Content-Type": "application/json; charset=utf-8"})
        try:
            with self.opener.open(req) as r:
                return r.status, r.headers, json.loads(r.read().decode())
        except urllib.error.HTTPError as e:
            raw = e.read().decode("utf-8", "replace")
            try:
                return e.code, e.headers, json.loads(raw)
            except ValueError:
                return e.code, e.headers, raw

    def get_json(self, url):
        status, headers, raw = self.get(url)
        try:
            return status, json.loads(raw.decode("utf-8"))
        except ValueError:
            return status, raw


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def cookie_names(browser):
    return sorted(c.name for c in browser.jar)


def authorize_code(browser, system, base):
    """走一次某系统的授权码流程，返回 (code, state)。"""
    status, headers, _ = browser.get(base + "/auth/sso-login")
    check(system + " sso-login redirects", status, 302)
    authorize_url = headers.get("Location", "")
    check(system + " authorize url host", authorize_url.startswith(AUTH_WEB), True)
    state = urllib.parse.parse_qs(urllib.parse.urlparse(authorize_url).query)["state"][0]

    status, headers, _ = browser.get(authorize_url)
    check(system + " authorize issued code (auto approve)", status in (302, 303), True)
    location = headers.get("Location", "")
    callback_base = (base.replace("7072", "5175")
                     .replace("7073", "5176")
                     .replace("7074", "5178")
                     .replace("7070", "5173"))
    check(system + " callback location", location.startswith(callback_base), True)
    params = urllib.parse.parse_qs(urllib.parse.urlparse(location).query)
    check(system + " callback has code", "code" in params, True)
    check(system + " callback state matches", params.get("state", [None])[0], state)
    return params["code"][0], state


b = Browser()

# 登录授权服务（原生表单，CSRF 已关闭）
status, headers, _ = b.post_form(AUTH_WEB + "/oauth/login",
                                 {"username": "zhangsan", "password": "admin123"})
check("auth server form login", status, 302)

# 营销系统：授权码 -> BFF 会话
code, state = authorize_code(b, "marketing", MARKETING)
status, headers, body = b.post_json(MARKETING + "/auth/code", {"code": code, "state": state})
check("marketing code exchange", status, 200)
check("marketing code exchange code", body.get("code") if isinstance(body, dict) else None, 200)
data = body.get("data") or {}
check("marketing returns user info", data.get("username"), "zhangsan")
check("marketing returns menus", isinstance(data.get("menus"), list), True)
check("marketing response has no tokens",
      "accessToken" in data or "refreshToken" in data, False)
set_cookie = "; ".join(headers.get_all("Set-Cookie") or [])
check("marketing sets HttpOnly BFF cookie",
      "marketing_bff_session=" in set_cookie and "HttpOnly" in set_cookie, True)

status, info = b.get_json(MARKETING + "/auth/info")
check("marketing info with cookie", status, 200)
check("marketing info user", (info.get("data") or {}).get("username"), "zhangsan")

# 已登录状态下进订单中心：不再经过登录页，直接静默发码
code2, state2 = authorize_code(b, "order", ORDER)
status, headers, body = b.post_json(ORDER + "/auth/code", {"code": code2, "state": state2})
check("order code exchange", status, 200)
check("order sets its own BFF cookie",
      "order_bff_session=" in "; ".join(headers.get_all("Set-Cookie") or []), True)
status, info = b.get_json(ORDER + "/auth/info")
check("order info with cookie", status, 200)
check("order info user", (info.get("data") or {}).get("username"), "zhangsan")

# 审计系统：只有授权码登录，走同一条链路
code3, state3 = authorize_code(b, "audit", AUDIT)
status, headers, body = b.post_json(AUDIT + "/auth/code", {"code": code3, "state": state3})
check("audit code exchange", status, 200)
check("audit sets its own BFF cookie",
      "audit_bff_session=" in "; ".join(headers.get_all("Set-Cookie") or []), True)
status, info = b.get_json(AUDIT + "/auth/info")
check("audit info with cookie", status, 200)
check("audit info user", (info.get("data") or {}).get("username"), "zhangsan")

# 管理后台：同样只有授权码登录
code4, state4 = authorize_code(b, "rbac", RBAC)
status, headers, body = b.post_json(RBAC + "/auth/code", {"code": code4, "state": state4})
check("rbac code exchange", status, 200)
check("rbac sets its own BFF cookie",
      "rbac_bff_session=" in "; ".join(headers.get_all("Set-Cookie") or []), True)
status, info = b.get_json(RBAC + "/auth/info")
check("rbac info with cookie", status, 200)
check("rbac info user", ((info.get("data") or {}).get("user") or {}).get("username"), "zhangsan")
check("rbac info has permissions", isinstance((info.get("data") or {}).get("permissions"), list), True)

# 两套会话的 Cookie 名互不覆盖
names = cookie_names(b)
check("all four BFF cookies coexist",
      all(n in names for n in ("marketing_bff_session", "order_bff_session",
                               "audit_bff_session", "rbac_bff_session")), True)

# 统一登出：登出营销会删掉授权服务的 SSO 会话，订单中心也应当立刻失效（BFF 会话被删）
status, _, _ = b.post_json(MARKETING + "/auth/logout", {})
check("marketing logout", status, 200)
check("marketing session gone", b.get(MARKETING + "/auth/info")[0], 401)
check("unified logout also invalidates order (single logout)",
      b.get(ORDER + "/auth/info")[0], 401)
check("unified logout also invalidates audit (single logout)",
      b.get(AUDIT + "/auth/info")[0], 401)
check("unified logout also invalidates rbac (single logout)",
      b.get(RBAC + "/auth/info")[0], 401)
check("order BFF session removed after revocation",
      b.post_json(ORDER + "/auth/logout", {})[0], 200)

print("\nRESULT: PASS=%d FAIL=%d" % (PASS, FAIL))
sys.exit(1 if FAIL else 0)
