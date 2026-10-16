# -*- coding: utf-8 -*-
"""SSO 实测：同一个浏览器会话，先用 marketing 走完授权码流程，再发起 rbac-third-party 的授权。"""
import json
import urllib.request
import urllib.error
import urllib.parse
import http.cookiejar

OAUTH = "http://localhost:7071"
jar = http.cookiejar.CookieJar()


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


opener = urllib.request.build_opener(NoRedirect(), urllib.request.HTTPCookieProcessor(jar))


def go(url, method="GET", form=None):
    headers = {}
    data = None
    if form is not None:
        data = urllib.parse.urlencode(form).encode("utf-8")
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with opener.open(req) as r:
            return r.status, dict(r.headers)
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers)


def authorize_url(client_id, secret, redirect_uri, state):
    return (OAUTH + "/oauth/authorize?response_type=code"
            + "&client_id=" + client_id + "&client_secret=" + secret
            + "&redirect_uri=" + urllib.parse.quote(redirect_uri, safe="")
            + "&scope=read&state=" + state)


def cookies():
    return "; ".join("%s=%s" % (c.name, c.value[:12] + "...") for c in jar) or "(无)"


print("=" * 74)
print("第 1 步：未登录，发起 marketing 的授权")
print("=" * 74)
st, hdr = go(authorize_url("marketing", "marketing123", "http://localhost:5175/oauth/callback", "s1"))
print("  HTTP %s -> %s" % (st, hdr.get("Location")))
print("  Cookie: %s" % cookies())

print()
print("=" * 74)
print("第 2 步：在授权服务器登录页登录（一次性）")
print("=" * 74)
st, hdr = go(OAUTH + "/oauth/login", method="POST", form={"username": "admin", "password": "admin123"})
print("  HTTP %s -> %s" % (st, hdr.get("Location")))
print("  Cookie: %s" % cookies())

print()
print("=" * 74)
print("第 3 步：回到 marketing 的授权请求")
print("=" * 74)
st, hdr = go(authorize_url("marketing", "marketing123", "http://localhost:5175/oauth/callback", "s1"))
loc = hdr.get("Location", "")
print("  HTTP %s -> %s" % (st, loc))
print("  %s" % ("已登录：直接进确认页，不再要求登录" if "/approve" in loc else "仍要求登录"))

print()
print("=" * 74)
print("第 4 步：同意授权 marketing")
print("=" * 74)
st, hdr = go(OAUTH + "/oauth/authorize", method="POST", form={"user_oauth_approval": "true"})
loc = hdr.get("Location", "")
q = urllib.parse.parse_qs(urllib.parse.urlparse(loc).query)
print("  HTTP %s -> %s" % (st, loc[:100]))
print("  code = %s" % (q.get("code", [""])[0][:20] + "..."))

print()
print("=" * 74)
print("第 5 步：★ 同一浏览器，访问另一个子系统（rbac-third-party）的授权地址")
print("=" * 74)
st, hdr = go(authorize_url("rbac-third-party", "client123", "http://localhost:7071/oauth/test-callback", "s2"))
loc = hdr.get("Location", "")
print("  HTTP %s -> %s" % (st, loc[:130]))
if "/login" in loc:
    print("  >>> 结论：需要重新登录（没有 SSO）")
elif "/approve" in loc:
    print("  >>> 结论：不需要重新登录，但要再点一次「同意授权」（认证已共享，授权按客户端各算一次）")
elif "code=" in loc:
    print("  >>> 结论：连确认页都跳过了，直接发码（既有授权记录被复用）")
else:
    print("  >>> 其他：%s" % loc[:200])

print()
print("=" * 74)
print("第 6 步：同意授权 rbac-third-party")
print("=" * 74)
st, hdr = go(OAUTH + "/oauth/authorize", method="POST", form={"user_oauth_approval": "true"})
loc = hdr.get("Location", "")
q = urllib.parse.parse_qs(urllib.parse.urlparse(loc).query)
print("  HTTP %s -> %s" % (st, loc[:120]))
print("  code = %s" % (q.get("code", [""])[0][:20] + "..." if q.get("code") else "(无)"))

print()
print("=" * 74)
print("第 7 步：同一客户端(rbac-third-party)再授权一次，看是否复用既有授权")
print("=" * 74)
st, hdr = go(authorize_url("rbac-third-party", "client123", "http://localhost:7071/oauth/test-callback", "s3"))
loc = hdr.get("Location", "")
print("  HTTP %s -> %s" % (st, loc[:130]))
if "/approve" in loc:
    print("  >>> 仍进确认页（授权不缓存）")
elif "code=" in loc:
    print("  >>> 直接发码（复用了既有授权）")
