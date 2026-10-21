import base64
import json
import urllib.error
import urllib.parse
import urllib.request

BASE = 'http://localhost:7071'
CLIENT = 'rbac-third-party'
SECRET = 'client123'
USER = 'zhangsan'
PWD = 'admin123'


def post(path, data, basic=False):
    body = urllib.parse.urlencode(data).encode()
    headers = {'Content-Type': 'application/x-www-form-urlencoded'}
    if basic:
        token = base64.b64encode((CLIENT + ':' + SECRET).encode()).decode()
        headers['Authorization'] = 'Basic ' + token
    req = urllib.request.Request(BASE + path, data=body, method='POST', headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return resp.status, json.loads(resp.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode('utf-8'))
        except Exception:
            return e.code, {}


def jwt_payload(token):
    seg = token.split('.')[1]
    seg += '=' * (-len(seg) % 4)
    return json.loads(base64.urlsafe_b64decode(seg).decode('utf-8'))


def login(label):
    status, data = post('/oauth/token', {
        'grant_type': 'password', 'username': USER, 'password': PWD, 'scope': 'read'
    }, basic=True)
    print(label, 'login http=', status, 'error=', data.get('error'), data.get('error_description'))
    if status != 200:
        return None
    access = data['access_token']
    payload = jwt_payload(access)
    print(label, 'access sid=', payload.get('sid'), 'username=', payload.get('user_name'))
    print(label, 'refresh token exists=', bool(data.get('refresh_token')))
    return data


first = login('A')
second = login('B')
if not first or not second:
    raise SystemExit(1)

for label, tok in [('A', first['access_token']), ('B', second['access_token'])]:
    status, data = post('/oauth/check_token', {'token': tok}, basic=True)
    print('check_token', label, 'http=', status,
          'user=', data.get('user_name'), 'sid=', data.get('sid'),
          'error=', data.get('error'), data.get('error_description'))

for label, tok in [('A', first['refresh_token']), ('B', second['refresh_token'])]:
    status, data = post('/oauth/token', {
        'grant_type': 'refresh_token', 'refresh_token': tok
    }, basic=True)
    sid = jwt_payload(data['access_token']).get('sid') if status == 200 else None
    print('refresh', label, 'http=', status, 'new_sid=', sid,
          'error=', data.get('error'), data.get('error_description'))
