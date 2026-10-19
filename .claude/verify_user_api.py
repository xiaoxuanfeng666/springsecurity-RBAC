import json
import urllib.request

BASE = 'http://localhost:7070'
TOKEN = open('.claude/token.txt', encoding='utf-8').read().strip()


def call(method, path, body=None):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode('utf-8')
    req = urllib.request.Request(
        BASE + path,
        data=data,
        method=method,
        headers={
            'Content-Type': 'application/json; charset=utf-8',
            'Authorization': 'Bearer ' + TOKEN,
        },
    )
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode('utf-8'))


def find_user():
    page = call('GET', '/user/page?pageNum=1&pageSize=20&username=verify_user_1018')
    records = page['data']['records']
    return records[0] if records else None


existing = find_user()
if existing:
    call('DELETE', '/user/%d' % existing['id'])
    print('cleaned existing test user', existing['id'])

payload = {
    'username': 'verify_user_1018',
    'password': 'Test123456',
    'nickname': '验证用户',
    'employeeNo': 'E10086',
    'alias': 'verify',
    'position': '测试工程师',
    'gender': 1,
    'deptId': 2,
    'deptIds': [2, 4],
    'leaderInDept': True,
    'orderInDept': 5,
    'email': 'verify1018@example.com',
    'bizMail': 'verify@corp.example.com',
    'phone': '13900001018',
    'telephone': '010-88886666',
    'directLeader': 'admin',
    'externalPosition': '测试对外职务',
    'externalNickname': '验证昵称',
    'address': '北京市海淀区',
    'extAttr': '{"office":"A座"}',
    'status': 1,
    'remark': '自动化验证',
    'roleIds': [],
    'postIds': [],
}

print('create:', call('POST', '/user', payload))
user = find_user()
print('created id:', user['id'])
detail = call('GET', '/user/%d' % user['id'])['data']
checks = {
    'employeeNo': detail.get('employeeNo') == 'E10086',
    'alias': detail.get('alias') == 'verify',
    'position': detail.get('position') == '测试工程师',
    'gender': detail.get('gender') == 1,
    'bizMail': detail.get('bizMail') == 'verify@corp.example.com',
    'telephone': detail.get('telephone') == '010-88886666',
    'directLeader': detail.get('directLeader') == 'admin',
    'externalPosition': detail.get('externalPosition') == '测试对外职务',
    'externalNickname': detail.get('externalNickname') == '验证昵称',
    'address': detail.get('address') == '北京市海淀区',
    'extAttr': detail.get('extAttr') == '{"office":"A座"}',
    'deptId': detail.get('deptId') == 2,
    'deptIds': sorted(detail.get('deptIds') or []) == [2, 4],
    'leaderInDept': detail.get('leaderInDept') is True,
    'orderInDept': detail.get('orderInDept') == 5,
}
print('detail checks:', checks)
print('detail snapshot:', json.dumps({k: detail.get(k) for k in (
    'id', 'username', 'employeeNo', 'alias', 'position', 'gender', 'deptId', 'deptIds',
    'leaderInDept', 'orderInDept', 'bizMail', 'telephone', 'directLeader',
    'externalPosition', 'externalNickname', 'address', 'extAttr')}, ensure_ascii=False))

payload.update({
    'id': user['id'],
    'nickname': '验证用户改',
    'position': '高级测试工程师',
    'deptIds': [2, 3, 4],
    'leaderInDept': False,
    'orderInDept': 7,
})
print('update:', call('PUT', '/user', payload))
detail2 = call('GET', '/user/%d' % user['id'])['data']
checks2 = {
    'nickname': detail2.get('nickname') == '验证用户改',
    'position': detail2.get('position') == '高级测试工程师',
    'deptIds': sorted(detail2.get('deptIds') or []) == [2, 3, 4],
    'leaderInDept': detail2.get('leaderInDept') is False,
    'orderInDept': detail2.get('orderInDept') == 7,
}
print('update checks:', checks2)
print('all passed:', all(checks.values()) and all(checks2.values()))
