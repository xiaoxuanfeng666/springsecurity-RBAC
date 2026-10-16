# -*- coding: utf-8 -*-
"""订单中心：授权码模式全链路 + 前端路由/权限契约验证。

后端接口的 CRUD 与 MQ 下发由 order-center-verify.py 覆盖，本脚本补两块：
  ① 授权码模式：按浏览器的方式走完 302→登录→确认→303 code→换令牌
  ② 前端契约：用真实的 /auth/info 载荷跑一遍 permission.js 的路由生成逻辑，
     确认每个 menu.component 都能解析到实际存在的 .vue，且按钮权限门与后端一致
"""
import http.cookiejar
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

OAUTH = "http://localhost:7071"
ORDER = "http://localhost:7073"
ORDER_WEB = "http://localhost:5176"
VIEWS_DIR = "order-frontend/src/views"

PASS = 0
FAIL = 0


def check(label, actual, expected):
    global PASS, FAIL
    if actual == expected:
        PASS += 1
        print("  [OK]   %-60s %s" % (label, actual))
    else:
        FAIL += 1
        print("  [FAIL] %-60s got=%s want=%s" % (label, actual, expected))


def check_true(label, cond, detail=""):
    global PASS, FAIL
    if cond:
        PASS += 1
        print("  [OK]   %-60s %s" % (label, detail))
    else:
        FAIL += 1
        print("  [FAIL] %-60s %s" % (label, detail))


# ---------- 通用 HTTP（不跟随跳转，手动看 Location）----------
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


jar = http.cookiejar.CookieJar()
browser = urllib.request.build_opener(NoRedirect(), urllib.request.HTTPCookieProcessor(jar))


def go(url, method="GET", form=None, token=None):
    headers = {}
    data = None
    if form is not None:
        data = urllib.parse.urlencode(form).encode("utf-8")
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with browser.open(req) as r:
            return r.status, dict(r.headers), r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read().decode("utf-8", "replace")


def api(path, method="GET", body=None, token=None):
    headers = {}
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(ORDER + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, raw


print("=" * 80)
print("第 1 步：授权码模式全链路（等价于浏览器操作）")
print("=" * 80)

# 1.1 前端点「前往授权」时调的就是这个接口
st, r = api("/auth/authorize-url?state=browser-e2e")
check("GET /auth/authorize-url code", r["code"], 200)
authorize_url = r["data"]

# 1.2 浏览器整页跳到授权端点（此时还没有 rbac-oauth 的会话 Cookie）
st, hdr, _ = go(authorize_url)
loc = hdr.get("Location", "")
check("未登录时授权端点 302", st, 302)
check_true("跳到 rbac-oauth-web 登录页(5174)", "/login" in loc, loc[:70])

# 1.3 在授权登录页提交账号密码（原生表单 POST /oauth/login）
st, hdr, _ = go(OAUTH + "/oauth/login", "POST", {"username": "admin", "password": "admin123"})
check("POST /oauth/login 后跳转", st in (302, 303), True)

# 1.4 重新发起授权请求 → 应直接进确认页，不再要求登录
st, hdr, _ = go(authorize_url)
loc = hdr.get("Location", "")
check_true("已登录：直接进确认页 /approve", "/approve" in loc, loc[:70])

# 1.5 确认页渲染所需的数据
st, _, body = go(OAUTH + "/oauth/approval-info")
approval = json.loads(body) if body.strip().startswith("{") else {}
check("授权确认页 clientId", approval.get("data", {}).get("clientId"), "order-center")
check("授权确认页 redirectUri", approval.get("data", {}).get("redirectUri"),
      "http://localhost:5176/oauth/callback")
check_true("授权确认页带 state（回跳时用于校验同一次授权）",
           approval.get("data", {}).get("state") == "browser-e2e",
           approval.get("data", {}).get("state"))

# 1.6 点「同意授权」
st, hdr, _ = go(OAUTH + "/oauth/authorize", "POST", {"user_oauth_approval": "true"})
loc = hdr.get("Location", "")
check("同意授权后 303", st, 303)
check_true("回跳订单中心前端 5176/oauth/callback", "localhost:5176/oauth/callback" in loc, loc[:90])

query = urllib.parse.parse_qs(urllib.parse.urlparse(loc).query)
code = query.get("code", [""])[0]
check_true("回调地址上带 code", bool(code), code[:18] + "..." if code else "(无)")

# 1.7 回调页把 code 交给后端换令牌（换令牌要带 client_secret，不能放前端做）
st, r = api("/auth/code", "POST", {"code": code})
check("POST /auth/code 换令牌 code", r.get("code") if isinstance(r, dict) else r, 200)
code_token = r["data"]["accessToken"] if isinstance(r, dict) and r.get("code") == 200 else None
check_true("拿到 accessToken", bool(code_token))

# 1.8 用授权码流程拿到的令牌访问业务接口
if code_token:
    st, r = api("/auth/info", token=code_token)
    check("授权码令牌 GET /auth/info", r.get("code"), 200)
    check("身份为 admin", r["data"]["username"], "admin")
    st, r = api("/order/page?pageNum=1&pageSize=5", token=code_token)
    check("授权码令牌 GET /order/page", r.get("code"), 200)

# 1.9 授权码是一次性的
st, r = api("/auth/code", "POST", {"code": code})
check_true("同一个 code 不能重复使用", r.get("code") != 200, "code=%s" % r.get("code"))

print()
print("=" * 80)
print("第 2 步：前端路由契约 —— 后端下发的 component 能否解析到真实组件")
print("=" * 80)

# 复刻 order-frontend/src/store/permission.js 的逻辑
view_files = set()
for root, _dirs, files in os.walk(VIEWS_DIR):
    for f in files:
        if f.endswith(".vue"):
            rel = os.path.relpath(os.path.join(root, f), VIEWS_DIR).replace("\\", "/")
            view_files.add("../views/" + rel)


def resolve_component(component):
    if not component:
        return None
    key = "../views/%s.vue" % component
    return key if key in view_files else None


def build_routes(menus):
    routes = []
    top = []
    for m in menus:
        if m["type"] == 1:
            children = []
            for c in m.get("children", []):
                if c["type"] != 2:
                    continue
                res = resolve_component(c["component"])
                children.append({"path": c["path"], "name": "Menu%d" % c["id"],
                                 "component": res, "title": c["menuName"]})
            if children:
                routes.append({"path": m["path"], "name": "Dir%d" % m["id"],
                               "component": "Layout", "children": children})
        elif m["type"] == 2:
            top.append(m)
    for m in top:
        routes.append({"path": "/", "name": "TopLevel", "component": "Layout",
                       "children": [{"path": m["path"].lstrip("/"), "name": "Menu%d" % m["id"],
                                     "component": resolve_component(m["component"]),
                                     "title": m["menuName"]}]})
    return routes


def find_first_path(routes):
    for route in routes:
        if route.get("children"):
            base = "" if route["path"] == "/" else route["path"]
            full = ("%s/%s" % (base, route["children"][0]["path"])).replace("//", "/")
            return full if full.startswith("/") else "/" + full
    return "/404"


for who, pwd in (("admin", "admin123"), ("zhangsan", "admin123")):
    st, r = api("/auth/login", "POST", {"username": who, "password": pwd})
    token = r["data"]["accessToken"]
    st, info = api("/auth/info", token=token)
    menus = info["data"]["menus"]
    routes = build_routes(menus)

    unresolved = [c["component"] for rt in routes for c in rt.get("children", [])
                  if rt.get("children") and c["component"] is None]
    check("%s：所有 component 都解析到真实 .vue" % who, unresolved, [])

    leaf = [c["title"] for rt in routes for c in rt.get("children", [])]
    default_path = find_first_path(routes)
    check_true("%s：落地页不是 404" % who, default_path != "/404", default_path)

    # 生成的路由 path 是否与菜单树对得上
    dirs = [rt["path"] for rt in routes if rt["name"].startswith("Dir")]
    check("%s：目录路由" % who, dirs, ["/order"])

    if who == "admin":
        check("admin 叶子页面数", len(leaf), 2)
    else:
        check("zhangsan 叶子页面", leaf, ["订单列表", "订单统计"])
        write_perms = [p for p in info["data"]["permissions"]
                       if p.endswith((":add", ":edit", ":remove"))]
        check("zhangsan 不含任何写权限点", write_perms, [])

print()
print("=" * 80)
print("第 3 步：按钮级权限门（v-permission 等价判定）")
print("=" * 80)

# 订单列表页上各按钮声明的权限点，取自 views/order/list/index.vue
BUTTONS = [
    ("新增订单", "order:order:add"),
    ("修改", "order:order:edit"),
    ("删除", "order:order:remove"),
]
# 统计页无按钮；列表页其余为只读列


def visible_perms(perms):
    """复刻 store/user.js 的 hasAnyPerm + directive/permission.js 的移除逻辑"""
    granted = set(perms)
    return {label: (("*:*:*" in granted) or (perm in granted)) for label, perm in BUTTONS}


for who, pwd, expect in (("admin", "admin123", {"新增订单": True, "修改": True, "删除": True}),
                         ("zhangsan", "admin123", {"新增订单": False, "修改": False, "删除": False})):
    st, r = api("/auth/login", "POST", {"username": who, "password": pwd})
    st, info = api("/auth/info", token=r["data"]["accessToken"])
    actual = visible_perms(info["data"]["permissions"])
    check("%s 的按钮可见性" % who, actual, expect)

print()
print("=" * 80)
print("第 4 步：前端服务与代理")
print("=" * 80)

PAGES = ["/", "/login", "/oauth/callback", "/order/list", "/order/stat", "/404"]
for path in PAGES:
    st, _h, body = go(ORDER_WEB + path)
    check_true("页面 %-18s HTTP 200 且是 SPA 入口" % path,
               st == 200 and '<div id="app">' in body, "HTTP %s" % st)

# 每个 SFC 都要能被 vite 正常编译（模板/脚本语法错误的兜底）
sources = ["/src/main.js", "/src/App.vue", "/src/router/index.js", "/src/store/user.js",
           "/src/store/permission.js", "/src/api/request.js", "/src/api/auth.js", "/src/api/order.js",
           "/src/directive/permission.js", "/src/layout/index.vue",
           "/src/views/login/index.vue", "/src/views/oauth/callback.vue",
           "/src/views/dashboard/index.vue", "/src/views/error/404.vue",
           "/src/views/order/list/index.vue", "/src/views/order/stat/index.vue"]
bad = []
for src in sources:
    st, _h, body = go(ORDER_WEB + src)
    if st != 200 or "Internal server error" in body or "Transform failed" in body:
        bad.append("%s(HTTP %s)" % (src, st))
check("16 个源模块经 vite 编译无错", bad, [])

# 页面通过代理调用的接口
st, r = api("/auth/login", "POST", {"username": "admin", "password": "admin123"})
t = r["data"]["accessToken"]
for path in ["/api/auth/info", "/api/order/page?pageNum=1&pageSize=10", "/api/order/stat"]:
    st, _h, body = go(ORDER_WEB + path, token=t)
    code = json.loads(body).get("code") if body.strip().startswith("{") else None
    check("代理 %-38s code" % path, code, 200)

print()
print("=" * 80)
print("结果：%d 项通过，%d 项失败" % (PASS, FAIL))
print("=" * 80)
sys.exit(1 if FAIL else 0)
