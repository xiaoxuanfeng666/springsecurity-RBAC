import base64
import http.cookiejar
import json
import urllib.error
import urllib.parse
import urllib.request

BASE = 'http://localhost:7071'
CLIENT = 'marketing'
SECRET = 'marketing123'
REDIRECT = 'http://localhost:5175/oauth/callback'
USER = 'zhangsan'
PWD = 'admin123'


def authorize_url(state):
    q = urllib.parse.urlencode({
        'response_type': 'code', 'client_id': CLIENT, 'client_secret': SECRET,
        'redirect_uri': REDIRECT, 'scope': 'read', 'state': state
    })
    return '/oauth/authorize?' + q


def basic():
    return 'Basic ' + base64.b64encode((CLIENT + ':' + SECRET).encode()).decode()


def jwt_payload(token):
    seg = token.split('.')[1]
    seg += '=' * (-len(seg) % 4)
    return json.loads(base64.urlsafe_b64decode(seg).decode('utf-8'))


def login_browser(label, state):
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    data = urllib.parse.urlencode({
        'username': USER, 'password': PWD, 'returnTo': authorize_url(state)
    }).encode()
    resp = opener.open(BASE + '/oauth/login', data=data, timeout=20)
    final_url = resp.geturl()
    code = urllib.parse.parse_qs(urllib.parse.urlparse(final_url).query).get('code', [None])[0]
    auth_cookie = next((c.value for c in jar if c.name == 'RBAC_OAUTH2_AUTH'), None)
    print(label, 'final=', final_url)
    print(label, 'code exists=', bool(code), 'auth_cookie_exists=', bool(auth_cookie))

    body = urllib.parse.urlencode({
        'grant_type': 'authorization_code', 'code': code, 'redirect_uri': REDIRECT
    }).encode()
    req = urllib.request.Request(BASE + '/oauth/token', data=body, method='POST', headers={
        'Content-Type': 'application/x-www-form-urlencoded', 'Authorization': basic()
    })
    with urllib.request.urlopen(req, timeout=15) as r:
        token = json.loads(r.read().decode('utf-8'))
    sid = jwt_payload(token['access_token']).get('sid')
    print(label, 'token sid=', sid, 'sid==auth_cookie=', sid == auth_cookie)
    return token, jar


def check(label, token):
    body = urllib.parse.urlencode({'token': token}).encode()
    req = urllib.request.Request(BASE + '/oauth/check_token', data=body, method='POST', headers={
        'Content-Type': 'application/x-www-form-urlencoded', 'Authorization': basic()
    })
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            data = json.loads(r.read().decode('utf-8'))
            print('check', label, 'http=200 sid=', data.get('sid'))
    except urllib.error.HTTPError as e:
        print('check', label, 'http=', e.code, 'body=', e.read().decode('utf-8')[:120])


a, jar_a = login_browser('A', 'browserA')
b, jar_b = login_browser('B', 'browserB')
check('A', a['access_token'])
check('B', b['access_token'])

# 用 B 的登录态登出，清理测试会话
req = urllib.request.Request(BASE + '/oauth/sso-logout', data=b'', method='POST')
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar_b))
with opener.open(req, timeout=15) as r:
    print('logout B:', r.read().decode('utf-8'))
