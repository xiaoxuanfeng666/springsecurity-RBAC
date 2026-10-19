import json
import re
import urllib.parse
import urllib.request

BASE = 'https://qyapi.weixin.qq.com'
TARGET_NAME = '柴诗银'


def get(url):
    with urllib.request.urlopen(url, timeout=15) as resp:
        return json.loads(resp.read().decode('utf-8'))


def extract(text, key):
    m = re.search(key + r':\s*\$\{[^:}]+:([^}]*)\}', text)
    return m.group(1) if m else ''


def token(corp, secret):
    data = get(BASE + '/cgi-bin/gettoken?corpid=' + urllib.parse.quote(corp)
               + '&corpsecret=' + urllib.parse.quote(secret))
    if data.get('errcode') != 0:
        return None, data
    return data.get('access_token'), data


def try_secret(label, corp, secret):
    print('=' * 60)
    print(label, 'corp=', corp, 'secret_set=', bool(secret))
    tk, tok_resp = token(corp, secret)
    if not tk:
        print('gettoken failed:', json.dumps(tok_resp, ensure_ascii=False))
        return
    print('gettoken: errcode=', tok_resp.get('errcode'), tok_resp.get('errmsg'))
    for api, qs in [
        ('/cgi-bin/user/list', '&department_id=1&fetch_child=1'),
        ('/cgi-bin/user/simplelist', '&department_id=1&fetch_child=1'),
    ]:
        url = BASE + api + '?access_token=' + urllib.parse.quote(tk) + qs
        data = get(url)
        print('-' * 40)
        print(api, 'errcode=', data.get('errcode'), data.get('errmsg'))
        users = data.get('userlist')
        if users is None:
            continue
        print('user count:', len(users))
        matches = [u for u in users if u.get('name') == TARGET_NAME or u.get('alias') == TARGET_NAME]
        if matches:
            print('MATCHED:', json.dumps(matches, ensure_ascii=False, indent=2))
            userid = matches[0].get('userid')
            detail = get(BASE + '/cgi-bin/user/get?access_token=' + urllib.parse.quote(tk)
                         + '&userid=' + urllib.parse.quote(userid))
            print('user/get:', json.dumps(detail, ensure_ascii=False, indent=2))
            return
        else:
            print('no user named', TARGET_NAME)


front = open('rbac-oauth/src/main/resources/application.yml', encoding='utf-8').read()
# 只取 wecom: 下的配置，避免命中其他 secret
front_wecom = front.split('wecom:', 1)[1].split('# 多 IdP', 1)[0]
corp = extract(front_wecom, 'corp-id')
secret = extract(front_wecom, 'secret')
try_secret('rbac-oauth 自建应用 Secret', corp, secret)

back = open('rbac-backend/src/main/resources/application.yml', encoding='utf-8').read()
back_out = back.split('outbound:', 1)[1]
back_corp = extract(back_out, 'wecom-corp-id')
back_secret = extract(back_out, 'wecom-secret')
if back_corp and back_secret:
    try_secret('rbac-backend 通讯录同步 Secret', back_corp, back_secret)
