import json
import re
import urllib.parse
import urllib.request

BASE = 'https://qyapi.weixin.qq.com'


def request(url, body=None):
    data = None if body is None else json.dumps(body).encode('utf-8')
    req = urllib.request.Request(url, data=data, method='GET' if body is None else 'POST',
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.loads(resp.read().decode('utf-8'))


def extract(text, key):
    m = re.search(key + r':\s*\$\{[^:}]+:([^}]*)\}', text)
    return m.group(1) if m else ''


def token(corp, secret):
    return request(BASE + '/cgi-bin/gettoken?corpid=' + urllib.parse.quote(corp)
                   + '&corpsecret=' + urllib.parse.quote(secret)).get('access_token')


front = open('rbac-oauth/src/main/resources/application.yml', encoding='utf-8').read()
front_wecom = front.split('wecom:', 1)[1].split('# 多 IdP', 1)[0]
corp = extract(front_wecom, 'corp-id')
app_secret = extract(front_wecom, 'secret')

back = open('rbac-backend/src/main/resources/application.yml', encoding='utf-8').read()
back_out = back.split('outbound:', 1)[1]
contact_secret = extract(back_out, 'wecom-secret')

for label, secret in [('app', app_secret), ('contact', contact_secret)]:
    tk = token(corp, secret)
    print('=' * 50)
    print(label, 'token', bool(tk))
    q = urllib.parse.quote(tk)
    print('user/list_id:', json.dumps(request(
        BASE + '/cgi-bin/user/list_id?access_token=' + q,
        {'cursor': '', 'limit': 10}), ensure_ascii=False))
    print('user/get admin:', json.dumps(request(
        BASE + '/cgi-bin/user/get?access_token=' + q + '&userid=admin'), ensure_ascii=False))
