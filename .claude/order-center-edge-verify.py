# -*- coding: utf-8 -*-
"""订单中心：业务边界与入参校验测试。

覆盖分页、筛选、金额精度、字段长度、必填校验、非法 id、统计一致性。
"""
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

ORDER = "http://localhost:7073"
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


def code_of(resp):
    return resp.get("code") if isinstance(resp, dict) else resp


st, r = api("/auth/login", "POST", {"username": "admin", "password": "admin123"})
T = r["data"]["accessToken"]
created_ids = []


def make(order_no, **over):
    body = {"orderNo": order_no, "customerName": "边界测试", "orderAmount": 10.00,
            "status": 1, "orderTime": "2026-10-16 12:00:00", "remark": "edge"}
    body.update(over)
    return body


def create(body):
    """新增并把成功创建的 id 记下来，结尾按 id 清理（可重复运行，不依赖编号查询）"""
    st, resp = api("/order", "POST", body, T)
    if code_of(resp) == 200:
        st2, p = api("/order/page?pageNum=1&pageSize=5&orderNo=%s"
                     % urllib.parse.quote(body["orderNo"]), token=T)
        if code_of(p) == 200 and p["data"]["records"]:
            created_ids.append(p["data"]["records"][0]["id"])
    return st, resp


# 清掉上一轮可能残留的行，保证脚本可重复执行
st, r = api("/order/page?pageNum=1&pageSize=100&orderNo=E-EDGE", token=T)
for row in r.get("data", {}).get("records", []):
    api("/order/%d" % row["id"], "DELETE", None, T)
st, r = api("/order/page?pageNum=1&pageSize=5&orderNo=%s" % urllib.parse.quote("E" * 64), token=T)
for row in r.get("data", {}).get("records", []):
    api("/order/%d" % row["id"], "DELETE", None, T)


print("=" * 80)
print("第 1 步：必填与格式校验")
print("=" * 80)
missing_no = make("X")           # 占位，随后删掉 orderNo
missing_no.pop("orderNo")
missing_name = make("X")
missing_name.pop("customerName")
missing_amount = make("X")
missing_amount.pop("orderAmount")

check("缺 orderNo -> 400", code_of(api("/order", "POST", missing_no, T)[1]), 400)
check("缺 customerName -> 400", code_of(api("/order", "POST", missing_name, T)[1]), 400)
check("缺 orderAmount -> 400", code_of(api("/order", "POST", missing_amount, T)[1]), 400)
check("orderNo 空白字符串 -> 400",
      code_of(api("/order", "POST", make("E-EDGE-01", orderNo="   "), T)[1]), 400)
check("customerName 空白字符串 -> 400",
      code_of(api("/order", "POST", make("E-EDGE-01", customerName=""), T)[1]), 400)
check("orderAmount 为负 -> 400",
      code_of(api("/order", "POST", make("E-EDGE-01", orderAmount=-1), T)[1]), 400)
check("orderAmount 为 0 -> 200（合法）",
      code_of(create(make("E-EDGE-01", orderAmount=0))[1]), 200)
check("orderNo 重复 -> 2007",
      code_of(api("/order", "POST", make("E-EDGE-01"), T)[1]), 2007)

print()
print("=" * 80)
print("第 2 步：金额精度与字段长度")
print("=" * 80)
check("两位小数 0.01 -> 200",
      code_of(create(make("E-EDGE-02", orderAmount=0.01))[1]), 200)
check("DECIMAL(10,2) 上限 99999999.99 -> 200",
      code_of(create(make("E-EDGE-03", orderAmount=99999999.99))[1]), 200)

st, r = api("/order/page?pageNum=1&pageSize=10&orderNo=E-EDGE-03", token=T)
check("上限金额往返精度", str(r["data"]["records"][0]["orderAmount"]), "99999999.99")

st, r = api("/order/page?pageNum=1&pageSize=10&orderNo=E-EDGE-02", token=T)
check("0.01 往返精度", str(r["data"]["records"][0]["orderAmount"]), "0.01")

# 超出列精度：要么被参数校验挡住，要么落库时报错，两种都不该悄悄改数
oversize = create(make("E-EDGE-04", orderAmount=1234567890.12))[1]
check_true("超出 DECIMAL(10,2) 不打 200", code_of(oversize) != 200, "code=%s" % code_of(oversize))

# order_no 列长 64
long65 = api("/order", "POST", make("E" * 65), T)[1]
check_true("orderNo 65 字符（列长 64）不打 200", code_of(long65) != 200, "code=%s" % code_of(long65))

check("orderNo 恰好 64 字符 -> 200", code_of(create(make("E" * 64))[1]), 200)

print()
print("=" * 80)
print("第 3 步：中文与特殊字符")
print("=" * 80)
check("中文客户名 -> 200",
      code_of(create(make("E-EDGE-05", customerName="张三·李四（华东）"))[1]), 200)
st, r = api("/order/page?pageNum=1&pageSize=10&orderNo=E-EDGE-05", token=T)
check("中文客户名往返", r["data"]["records"][0]["customerName"], "张三·李四（华东）")
check("中文备注往返", r["data"]["records"][0]["remark"], "edge")

check("含引号/百分号的客户名 -> 200",
      code_of(create(make("E-EDGE-06", customerName="O'Brien 100% \"特价\""))[1]), 200)
st, r = api("/order/page?pageNum=1&pageSize=10&orderNo=E-EDGE-06", token=T)
check("特殊字符往返未被截断", r["data"]["records"][0]["customerName"], "O'Brien 100% \"特价\"")

print()
print("=" * 80)
print("第 4 步：分页与筛选")
print("=" * 80)
st, r = api("/order/page?pageNum=1&pageSize=2", token=T)
check("pageSize=2 返回 2 条", len(r["data"]["records"]), 2)
total_all = r["data"]["total"]
first_ids = [x["id"] for x in r["data"]["records"]]
st, r = api("/order/page?pageNum=2&pageSize=2", token=T)
second_ids = [x["id"] for x in r["data"]["records"]]
check_true("第 2 页与第 1 页不重叠", set(first_ids).isdisjoint(second_ids),
           "%s vs %s" % (first_ids, second_ids))
check_true("total 与页大小无关", r["data"]["total"] == total_all, total_all)

st, r = api("/order/page?pageNum=1&pageSize=10&status=4", token=T)
check("按状态筛选：全部是已完成",
      sorted({x["status"] for x in r["data"]["records"]}), [4])
check_true("筛选 total 小于全部", r["data"]["total"] < total_all,
           "%s < %s" % (r["data"]["total"], total_all))

st, r = api("/order/page?pageNum=1&pageSize=10&customerName=%s" % urllib.parse.quote("张"), token=T)
fuzzy = r["data"]["records"]
check_true("客户名模糊查询命中", len(fuzzy) >= 2, "%d 条" % len(fuzzy))
check_true("模糊结果都含关键字", all("张" in x["customerName"] for x in fuzzy),
           sorted({x["customerName"] for x in fuzzy}))

st, r = api("/order/page?pageNum=1&pageSize=10&orderNo=NOT-EXIST-XYZ", token=T)
check("查不到时返回空列表而非报错", (code_of(r), len(r["data"]["records"])), (200, 0))

st, r = api("/order/page?pageNum=999&pageSize=10", token=T)
check("超出范围的页码返回空列表", (code_of(r), len(r["data"]["records"])), (200, 0))

print()
print("=" * 80)
print("第 5 步：非法 id 与统计一致性")
print("=" * 80)
check("查不存在的订单 -> 2008", code_of(api("/order/999999", token=T)[1]), 2008)
check("改不存在的订单 -> 2008",
      code_of(api("/order", "PUT", {"id": 999999, "orderNo": "NOPE", "customerName": "x",
                                    "orderAmount": 1}, T)[1]), 2008)
check("删不存在的订单 -> 2008", code_of(api("/order/999999", "DELETE", None, T)[1]), 2008)

st, r = api("/order/stat", token=T)
stat = r["data"]
total_from_rows = sum(row["count"] for row in stat["statusCounts"])
amount_from_rows = round(sum(float(row["amount"]) for row in stat["statusCounts"]), 2)
check("统计总数 == 各状态单数之和", stat["totalCount"], total_from_rows)
check("统计金额 == 各状态金额之和", round(float(stat["totalAmount"]), 2), amount_from_rows)
check_true("状态都在 1~5 内", all(1 <= row["status"] <= 5 for row in stat["statusCounts"]),
           sorted(row["status"] for row in stat["statusCounts"]))

print()
print("=" * 80)
print("第 6 步：修改与删除的边界")
print("=" * 80)
st, r = api("/order/page?pageNum=1&pageSize=10&orderNo=E-EDGE-01", token=T)
oid = r["data"]["records"][0]["id"]

st, r = api("/order", "PUT", {"id": oid, "orderNo": "E-EDGE-01", "customerName": "同名不改编号",
                              "orderAmount": 55.55, "status": 5}, T)
check("改单但保持原编号 -> 200", code_of(r), 200)
st, r = api("/order/%d" % oid, token=T)
check("修改生效", (r["data"]["customerName"], r["data"]["status"]), ("同名不改编号", 5))

st, r = api("/order", "PUT", {"id": oid, "orderNo": "E-EDGE-02", "customerName": "撞已存在编号",
                              "orderAmount": 1, "status": 1}, T)
check("改成别的订单已占用的编号 -> 2007", code_of(r), 2007)

# 更新时未传 status / orderTime：应保持服务端默认行为而不是写 null
st, r = api("/order", "PUT", {"id": oid, "orderNo": "E-EDGE-01", "customerName": "只改名字",
                              "orderAmount": 66.66}, T)
check("更新时不传状态 -> 200", code_of(r), 200)
st, r = api("/order/%d" % oid, token=T)
check("默认状态回落到 1待支付", r["data"]["status"], 1)

check("删除 -> 200", code_of(api("/order/%d" % oid, "DELETE", None, T)[1]), 200)
check("删除后查 -> 2008", code_of(api("/order/%d" % oid, token=T)[1]), 2008)

print()
print("=" * 80)
print("第 7 步：清理本轮产生的数据")
print("=" * 80)
removed = 0
for oid in created_ids:
    st, d = api("/order/%d" % oid, "DELETE", None, T)
    # 2008 = 第 6 步已经删过了，同样算清理干净
    removed += code_of(d) in (200, 2008)
check_true("已删除本轮创建的订单", removed == len(created_ids),
           "%d/%d" % (removed, len(created_ids)))

st, r = api("/order/stat", token=T)
check("清理后剩余订单数回到 6 条种子", r["data"]["totalCount"], 6)

print()
print("=" * 80)
print("结果：%d 项通过，%d 项失败" % (PASS, FAIL))
print("=" * 80)
sys.exit(1 if FAIL else 0)
