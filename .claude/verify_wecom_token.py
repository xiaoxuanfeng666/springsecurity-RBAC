import json
import re
import urllib.parse
import urllib.request

text = open('rbac-backend/src/main/resources/application.yml', encoding='utf-8').read()
block = text.split('outbound:', 1)[1]
corp = re.search(r'wecom-corp-id:\s*\$\{WECOM_CORP_ID:([^}]*)\}', block)
secret = re.search(r'wecom-secret:\s*\$\{WECOM_SECRET:([^}]*)\}', block)
corp_id = corp.group(1) if corp else ''
corp_secret = secret.group(1) if secret else ''
print('corp configured:', bool(corp_id), 'secret configured:', bool(corp_secret))

url = ('https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid='
       + urllib.parse.quote(corp_id) + '&corpsecret=' + urllib.parse.quote(corp_secret))
try:
    with urllib.request.urlopen(url, timeout=10) as resp:
        data = json.loads(resp.read().decode('utf-8'))
    print('errcode:', data.get('errcode'), 'errmsg:', data.get('errmsg'),
          'token_received:', bool(data.get('access_token')))
except Exception as e:
    print('request failed:', e)
