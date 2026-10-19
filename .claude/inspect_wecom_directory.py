import json
import re
import urllib.parse
import urllib.request

text = open('rbac-backend/src/main/resources/application.yml', encoding='utf-8').read()
block = text.split('outbound:', 1)[1]
corp = re.search(r'wecom-corp-id:\s*\$\{WECOM_CORP_ID:([^}]*)\}', block).group(1)
secret = re.search(r'wecom-secret:\s*\$\{WECOM_SECRET:([^}]*)\}', block).group(1)


def get(url):
    with urllib.request.urlopen(url, timeout=10) as resp:
        return json.loads(resp.read().decode('utf-8'))


token = get('https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid='
            + urllib.parse.quote(corp) + '&corpsecret=' + urllib.parse.quote(secret))['access_token']

departments = get('https://qyapi.weixin.qq.com/cgi-bin/department/list?access_token='
                  + urllib.parse.quote(token))
users = get('https://qyapi.weixin.qq.com/cgi-bin/user/list?access_token='
            + urllib.parse.quote(token) + '&department_id=1&fetch_child=1')

print('department/list errcode:', departments.get('errcode'), departments.get('errmsg'))
print('user/list errcode:', users.get('errcode'), users.get('errmsg'))
print('wecom departments:', len(departments.get('department', [])))
for d in departments.get('department', []):
    print('  dept', d.get('id'), 'parent', d.get('parentid'), d.get('name'))
userlist = users.get('userlist', [])
print('wecom users:', len(userlist))
for u in userlist:
    print('  user', u.get('userid'), u.get('name'), 'depts', u.get('department'))
