# -*- coding: utf-8 -*-
"""订单中心（order-center）端到端验证。

覆盖：RBAC 侧触发 MQ 下发、订单侧密码模式登录、权限拦截、订单 CRUD、统计汇总。
纯标准库实现；所有请求体显式 UTF-8 编码（Windows 下 Git Bash 会把中文 argv 转成 GBK）。
"""
import json
import sys
import time
import urllib.error
import urllib.request

RBAC = "http://localhost:7070"
ORDER = "http://localhost:7073"

PASS = 0
FAIL = 0


def call(base, path, method="GET", body=None, token=None):
    """返回 (http_status, parsed_json_or_text)"""
    headers = {}
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read().decode("utf-8")
            status = r.status
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        status = e.code
    try:
        return status, json.loads(raw)
    except ValueError:
        return status, raw


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    """不自动跟随 302 —— 要看的是后端返回的跳转地址本身"""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


_no_redirect = urllib.request.build_opener(_NoRedirect())


def redirect_location(path):
    """请求一个 302 端点，返回 (status, Location)"""
    try:
        with _no_redirect.open(ORDER + path) as r:
            return r.status, r.headers.get("Location", "")
    except urllib.error.HTTPError as e:
        return e.code, e.headers.get("Location", "")


def check(label, actual, expected):
    global PASS, FAIL
    ok = actual == expected
    if ok:
        PASS += 1
        print("  [OK]   %-58s %s" % (label, actual))
    else:
        FAIL += 1
        print("  [FAIL] %-58s got=%s want=%s" % (label, actual, expected))


def check_true(label, cond, detail=""):
    global PASS, FAIL
    if cond:
        PASS += 1
        print("  [OK]   %-58s %s" % (label, detail))
    else:
        FAIL += 1
        print("  [FAIL] %-58s %s" % (label, detail))


def wait_for_perms(zs_token, expected, timeout=20):
    """等 MQ 把新的授权下发到订单侧，返回最终看到的权限点列表。

    原来这里是固定 time.sleep(4)，是这类测试的经典毛病：MQ 稍慢一点断言就假失败，
    而且失败得很难看 —— 「撤销授权后 zhangsan 的越权写入被拒」这条会因为权限还没撤下来
    而真的写进一条订单（脏数据），还要靠人工清理。改成轮询到预期出现为止。
    """
    deadline = time.time() + timeout
    seen = None
    while time.time() < deadline:
        st, r = call(ORDER, "/auth/info", token=zs_token)
        if isinstance(r, dict) and r.get("code") == 200:
            seen = sorted(r["data"]["permissions"])
            if seen == sorted(expected):
                return seen
        time.sleep(1)
    return seen


def wait_for_status(token, path, expected_status, timeout=20):
    """等 MQ 把用户状态变更下发到订单侧（同样不靠固定 sleep，见上）"""
    deadline = time.time() + timeout
    seen = None
    while time.time() < deadline:
        seen = call(ORDER, path, token=token)[0]
        if seen == expected_status:
            return seen
        time.sleep(1)
    return seen


print("=" * 78)
print("第 1 步：登录 RBAC 后台，触发一次 order-center 的 MQ 下发")
print("=" * 78)
st, rbac = call(RBAC, "/auth/login", "POST", {"username": "admin", "password": "admin123"})
check("POST rbac /auth/login code", rbac.get("code") if isinstance(rbac, dict) else rbac, 200)
rbac_token = rbac["data"]["accessToken"]
check_true("拿到 rbac accessToken", bool(rbac_token), rbac_token[:20] + "...")

# 授权内容与 SQL 种子一致：common 只拿目录 + 两个只读页面（不带写按钮）
st, r = call(RBAC, "/role/2/subsystem-menus", "PUT",
             {"subsystemId": 1, "menuIds": [1, 2, 3]}, rbac_token)
check("PUT rbac /role/2/subsystem-menus code", r.get("code") if isinstance(r, dict) else r, 200)
print("  ... 等待 MQ 消费并重建同步表")
time.sleep(4)

print()
print("=" * 78)
print("第 2 步：订单侧超管登录 + /auth/info")
print("=" * 78)
st, admin = call(ORDER, "/auth/login", "POST", {"username": "admin", "password": "admin123"})
check("POST order /auth/login(admin) code", admin.get("code") if isinstance(admin, dict) else admin, 200)
admin_token = admin["data"]["accessToken"]
check_true("令牌类型", admin["data"]["tokenType"] == "bearer", admin["data"]["tokenType"])
check_true("返回 refreshToken", bool(admin["data"]["refreshToken"]))
check("order token 长度 > 100", len(admin_token) > 100, True)

st, info = call(ORDER, "/auth/info", token=admin_token)
check("GET order /auth/info code", info.get("code"), 200)
perms = info["data"]["permissions"]
check("admin 权限是通配符", perms, ["*:*:*"])
check("admin 角色", info["data"]["roles"], ["admin"])
menu_names = [m["menuName"] for m in info["data"]["menus"]]
check("admin 菜单树含订单管理", "订单管理" in menu_names, True)
child_names = [c["menuName"] for m in info["data"]["menus"] for c in m.get("children", [])]
check("admin 子菜单", child_names, ["订单列表", "订单统计"])

print()
print("=" * 78)
print("第 3 步：超管的订单 CRUD")
print("=" * 78)
new_order = {
    "orderNo": "ORD-E2E-0001",
    "customerName": "端到端测试客户",
    "orderAmount": 1234.56,
    "status": 1,
    "orderTime": "2026-10-16 18:30:00",
    "remark": "验证用订单：中文与金额、日期格式",
}
st, r = call(ORDER, "/order", "POST", new_order, admin_token)
check("POST /order 新增 code", r.get("code"), 200)

st, r = call(ORDER, "/order/page?pageNum=1&pageSize=50&orderNo=ORD-E2E-0001", token=admin_token)
check("GET /order/page 查询 code", r.get("code"), 200)
records = r["data"]["records"]
check("按编号查到 1 条", len(records), 1)
created_id = records[0]["id"]
check("中文客户名往返正确", records[0]["customerName"], "端到端测试客户")
check("金额往返正确", float(records[0]["orderAmount"]), 1234.56)
check("日期格式为 yyyy-MM-dd HH:mm:ss", records[0]["orderTime"], "2026-10-16 18:30:00")

st, r = call(ORDER, "/order", "POST", new_order, admin_token)
check("重复 order_no 被拒 code", r.get("code"), 2007)

st, r = call(ORDER, "/order/%d" % created_id, token=admin_token)
check("GET /order/{id} 详情 code", r.get("code"), 200)

updated = dict(new_order)
updated["id"] = created_id
updated["status"] = 4
updated["orderAmount"] = 2000.00
st, r = call(ORDER, "/order", "PUT", updated, admin_token)
check("PUT /order 修改 code", r.get("code"), 200)

st, r = call(ORDER, "/order/%d" % created_id, token=admin_token)
check("修改后状态", r["data"]["status"], 4)
check("修改后金额", float(r["data"]["orderAmount"]), 2000.00)

print()
print("=" * 78)
print("第 4 步：订单统计（只读汇总）")
print("=" * 78)
st, r = call(ORDER, "/order/stat", token=admin_token)
check("GET /order/stat code", r.get("code"), 200)
stat = r["data"]
check_true("总数 >= 7（6 条种子 + 1 条新增）", stat["totalCount"] >= 7, stat["totalCount"])
status_map = {row["status"]: row for row in stat["statusCounts"]}
check_true("已完成(4) 单数 >= 2", status_map.get(4, {}).get("count", 0) >= 2,
           status_map.get(4, {}).get("count"))
expected_total = sum(float(row["amount"]) for row in stat["statusCounts"])
check_true("总金额 = 各状态金额之和", abs(float(stat["totalAmount"]) - expected_total) < 0.001,
           "%s vs %s" % (stat["totalAmount"], expected_total))

print()
print("=" * 78)
print("第 5 步：普通用户(zhangsan/common) 的权限拦截")
print("=" * 78)
st, zs = call(ORDER, "/auth/login", "POST", {"username": "zhangsan", "password": "admin123"})
check("POST order /auth/login(zhangsan) code", zs.get("code") if isinstance(zs, dict) else zs, 200)
zs_token = zs["data"]["accessToken"]

st, info = call(ORDER, "/auth/info", token=zs_token)
check("GET /auth/info(zhangsan) code", info.get("code"), 200)
check("zhangsan 权限点", info["data"]["permissions"], ["order:order:list", "order:stat:list"])
check("zhangsan 角色", info["data"]["roles"], ["common"])
zs_menus = [c["menuName"] for m in info["data"]["menus"] for c in m.get("children", [])]
check("zhangsan 只看到两个只读页面", zs_menus, ["订单列表", "订单统计"])

st, r = call(ORDER, "/order/page?pageNum=1&pageSize=5", token=zs_token)
check("zhangsan GET /order/page（有 list 权限）", r.get("code"), 200)

st, r = call(ORDER, "/order/stat", token=zs_token)
check("zhangsan GET /order/stat（有 stat:list 权限）", r.get("code"), 200)

st, r = call(ORDER, "/order", "POST", {
    "orderNo": "ORD-E2E-DENY", "customerName": "越权", "orderAmount": 1.00
}, zs_token)
check("zhangsan POST /order 被拒", (st, r.get("code") if isinstance(r, dict) else r), (403, 403))

st, r = call(ORDER, "/order", "PUT", {"id": created_id, "orderNo": "ORD-E2E-0001",
                                      "customerName": "越权改", "orderAmount": 1.00}, zs_token)
check("zhangsan PUT /order 被拒", (st, r.get("code") if isinstance(r, dict) else r), (403, 403))

st, r = call(ORDER, "/order/%d" % created_id, "DELETE", token=zs_token)
check("zhangsan DELETE /order 被拒", (st, r.get("code") if isinstance(r, dict) else r), (403, 403))

st, r = call(ORDER, "/auth/info")
check("无令牌 GET /auth/info 返回 401", (st, r.get("code") if isinstance(r, dict) else r), (401, 401))

st, r = call(ORDER, "/auth/login", "POST", {"username": "admin", "password": "wrong-password"})
check_true("错误密码被拒", r.get("code") in (2001, 2003), r.get("code"))

print()
print("=" * 78)
print("第 6 步：刷新令牌 + 删除（逻辑删除后编号可复用）")
print("=" * 78)
st, r = call(ORDER, "/auth/refresh", "POST", {"refreshToken": admin["data"]["refreshToken"]})
check("POST /auth/refresh code", r.get("code"), 200)
check_true("刷新得到新 accessToken", r["data"]["accessToken"] != admin_token)
refreshed_token = r["data"]["accessToken"]

st, r = call(ORDER, "/order/%d" % created_id, "DELETE", token=refreshed_token)
check("DELETE /order/{id} code", r.get("code"), 200)

st, r = call(ORDER, "/order/page?pageNum=1&pageSize=50&orderNo=ORD-E2E-0001", token=refreshed_token)
check("删除后查不到", len(r["data"]["records"]), 0)

new_order2 = dict(new_order)
new_order2["customerName"] = "编号复用客户"
st, r = call(ORDER, "/order", "POST", new_order2, refreshed_token)
check("逻辑删除后同编号可再次新增", r.get("code"), 200)

st, r = call(ORDER, "/order/page?pageNum=1&pageSize=50&orderNo=ORD-E2E-0001", token=refreshed_token)
check("复用后能查到", len(r["data"]["records"]), 1)
cleanup_id = r["data"]["records"][0]["id"]
st, r = call(ORDER, "/order/%d" % cleanup_id, "DELETE", token=refreshed_token)
check("清理验证订单", r.get("code"), 200)

st, r = call(ORDER, "/order/999999", token=refreshed_token)
check("查不存在的订单 code", r.get("code"), 2008)

print()
print("=" * 78)
print("第 7 步：授权码模式（/auth/sso-login 由后端拼装授权地址并 302）")
print("=" * 78)
st, url = redirect_location("/auth/sso-login?state=verify123")
check("GET /auth/sso-login 是 302", st, 302)
check_true("指向 5174 的授权端点（授权前端，与登录页同源）",
           url.startswith("http://localhost:5174/oauth/authorize?"), url[:80])
check_true("client_id=order-center", "client_id=order-center" in url)
check_true("redirect_uri 指向 5176", "5176%2Foauth%2Fcallback" in url or "5176/oauth/callback" in url)
check_true("带 state", "verify123" in url)
# 前端拿不到 client_secret：旧的「把授权地址返给前端」的接口已下线
st_old, _ = call(ORDER, "/auth/authorize-url?state=x")
check("旧的 /auth/authorize-url 已下线（不再把带密钥的地址交给前端）", st_old, 401)

print()
print("=" * 78)
print("第 8 步：MQ 下发的双向性 —— 授权增删即时反映到订单侧")
print("=" * 78)
# 把「订单新增」按钮(202)授给 common：zhangsan 应立刻获得写权限
st, r = call(RBAC, "/role/2/subsystem-menus", "PUT",
             {"subsystemId": 1, "menuIds": [1, 2, 3, 202]}, rbac_token)
check("RBAC 侧追加授权 order:order:add", r.get("code"), 200)

check("zhangsan 权限点含新增",
      wait_for_perms(zs_token, ["order:order:add", "order:order:list", "order:stat:list"]),
      ["order:order:add", "order:order:list", "order:stat:list"])
granted = {"orderNo": "ORD-E2E-GRANT", "customerName": "授权后可写", "orderAmount": 9.90,
           "status": 1, "orderTime": "2026-10-16 18:40:00"}
st, r = call(ORDER, "/order", "POST", granted, zs_token)
check("授权后 zhangsan POST /order 通过", r.get("code"), 200)

# 撤销「订单统计」菜单(3)与新增按钮(202)：zhangsan 应立刻失去
st, r = call(RBAC, "/role/2/subsystem-menus", "PUT",
             {"subsystemId": 1, "menuIds": [1, 2]}, rbac_token)
check("RBAC 侧撤销授权", r.get("code"), 200)

check("zhangsan 权限点只剩 list", wait_for_perms(zs_token, ["order:order:list"]),
      ["order:order:list"])
st, r = call(ORDER, "/order/stat", token=zs_token)
check("撤销后 zhangsan GET /order/stat 被拒", (st, r.get("code") if isinstance(r, dict) else r), (403, 403))
st, r = call(ORDER, "/order", "POST", {
    "orderNo": "ORD-E2E-REVOKE", "customerName": "撤销后越权", "orderAmount": 1.00
}, zs_token)
check("撤销后 zhangsan POST /order 被拒", (st, r.get("code") if isinstance(r, dict) else r), (403, 403))

# 复原为 SQL 种子的授权，并清理第 8 步产生的订单
st, r = call(RBAC, "/role/2/subsystem-menus", "PUT",
             {"subsystemId": 1, "menuIds": [1, 2, 3]}, rbac_token)
check("复原 common 的授权", r.get("code"), 200)
check("复原后权限点", wait_for_perms(zs_token, ["order:order:list", "order:stat:list"]),
      ["order:order:list", "order:stat:list"])
for order_no in ("ORD-E2E-GRANT", "ORD-E2E-REVOKE"):
    st, r = call(ORDER, "/order/page?pageNum=1&pageSize=50&orderNo=%s" % order_no,
                 token=admin_token)
    if r["data"]["records"]:
        call(ORDER, "/order/%d" % r["data"]["records"][0]["id"], "DELETE", token=admin_token)
        print("  ... 已清理验证订单 %s" % order_no)

print()
print("=" * 78)
print("第 9 步：MQ 下发的用户状态 —— 停用后立刻被拒，恢复后可用")
print("=" * 78)
st, r = call(RBAC, "/user/2/status?status=0", "PUT", None, rbac_token)
check("RBAC 侧停用 zhangsan", r.get("code") if isinstance(r, dict) else r, 200)

check("停用后订单侧被拒（401，无需重新登录）",
      wait_for_status(zs_token, "/order/page?pageNum=1&pageSize=5", 401), 401)

st, r = call(ORDER, "/auth/login", "POST", {"username": "zhangsan", "password": "admin123"})
check_true("停用后无法重新登录本系统", r.get("code") != 200 and r.get("code") in (2001, 2002, 2004),
           "code=%s" % r.get("code"))

st, r = call(RBAC, "/user/2/status?status=1", "PUT", None, rbac_token)
check("RBAC 侧恢复 zhangsan", r.get("code") if isinstance(r, dict) else r, 200)

# 同样不靠固定 sleep：轮询到「恢复能真的登录进去」为止
zs2 = {"code": None}
deadline = time.time() + 20
while time.time() < deadline:
    st, zs2 = call(ORDER, "/auth/login", "POST", {"username": "zhangsan", "password": "admin123"})
    if zs2.get("code") == 200:
        break
    time.sleep(1)
check("恢复后可登录", zs2.get("code"), 200)
st, r = call(ORDER, "/order/page?pageNum=1&pageSize=5", token=zs2["data"]["accessToken"])
check("恢复后接口可用", r.get("code"), 200)

print()
print("=" * 78)
print("结果：%d 项通过，%d 项失败" % (PASS, FAIL))
print("=" * 78)
sys.exit(1 if FAIL else 0)
