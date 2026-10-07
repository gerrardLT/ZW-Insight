#!/usr/bin/env bash
# Run on the target host; credentials and fixture role IDs are supplied by environment.
set -euo pipefail
: "${ZWI_USER:?}" "${ZWI_PASS:?}" "${ZWI_ROLE_ID:?}" "${ZWI_FOREIGN_ROLE_ID:?}"
python3 - <<'PY'
import os,json,urllib.request,urllib.error,subprocess,time
base=os.environ.get('ZWI_BASE','http://127.0.0.1:18080')
assert os.environ['ZWI_USER']=='t9999admin', 'isolated administrator required'
role=int(os.environ['ZWI_ROLE_ID']); foreign=int(os.environ['ZWI_FOREIGN_ROLE_ID'])
expected=int(os.environ.get('ZWI_EXPECT_FOREIGN_CODE','400'))
mysql=os.environ.get('ZWI_MYSQL_CT','zwi-mysql'); token=None; ids=[]
name='t9999_role_regression'; phone='13999998881'
def sql(q):
    return subprocess.check_output(['docker','exec','-i',mysql,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -B zw_insight'],input=q.encode()).decode().strip()
def call(method,path,data=None):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    req=urllib.request.Request(base+'/api/v1/'+path,data=None if data is None else json.dumps(data).encode(),headers=headers,method=method)
    try:
        with urllib.request.urlopen(req,timeout=20) as r: return r.status,json.load(r)
    except urllib.error.HTTPError as e: return e.code,json.load(e)
def ok(method,path,data=None):
    status,r=call(method,path,data); assert status==200 and r['code']==200,(method,path,status,r.get('code')); return r.get('data')
def count(uid): return int(sql('SELECT COUNT(*) FROM sys_user_role WHERE user_id=%d;'%uid))
assert sql("SELECT COUNT(*) FROM sys_user WHERE username='%s' AND deleted=0;"%name)=='0', 'preexisting active regression user; stop without writes'
cap=ok('GET','captcha/image')
code=subprocess.check_output(['docker','exec',os.environ.get('ZWI_REDIS_CT','zwi-redis'),'redis-cli','GET','captcha:'+cap['uuid']]).decode().strip().strip('"')
login=ok('POST','auth/login',{'username':os.environ['ZWI_USER'],'password':os.environ['ZWI_PASS'],'captchaUuid':cap['uuid'],'captchaCode':code})
token=login.get('accessToken') or login.get('token'); assert token and int(login['tenantId'])==9999
assert sql('SELECT COUNT(*) FROM sys_role WHERE id=%d AND tenant_id=9999 AND deleted=0;'%role)=='1'
try:
    for generation in range(1,4):
        ok('POST','system/user',{'username':name,'password':os.environ['ZWI_PASS'],'realName':'T9999 regression','phone':phone,'status':1,'tenantId':9999})
        rows=ok('GET','system/user?username='+name+'&size=100')['records']
        rows=[r for r in rows if r['username']==name]; assert len(rows)==1
        uid=int(rows[0]['id']); ids.append(uid)
        assert sql('SELECT tenant_id FROM sys_user WHERE id=%d;'%uid)=='9999'
        for _ in range(2):
            ok('PUT','system/user/%d/roles'%uid,{'roleIds':[role,role]}); assert count(uid)==1
        ok('PUT','system/user/%d/roles'%uid,{'roleIds':[]}); assert count(uid)==0
        ok('PUT','system/user/%d/roles'%uid,{'roleIds':[role]}); assert count(uid)==1
        for invalid in ({},{'roleIds':None},{'roleIds':[None]},{'roleIds':['bad']}):
            status,r=call('PUT','system/user/%d/roles'%uid,invalid)
            assert status==400 or r['code']==400,(status,r['code']); assert count(uid)==1
        status,r=call('PUT','system/user/%d/roles'%uid,{'roleIds':[foreign]})
        print('FOREIGN generation=%d HTTP=%d JSON=%d expected=%d'%(generation,status,r['code'],expected))
        assert r['code']==expected,(status,r['code'],expected)
        assert sql('SELECT role_id FROM sys_user_role WHERE user_id=%d;'%uid)==str(role)
        ok('DELETE','system/user/%d'%uid); assert count(uid)==0
        print('PASS generation=%d duplicate=1 clear=0 reassign=1 invalidDTO=400 deletedLinks=0'%generation)
finally:
    cleanup_errors=[]
    for uid in ids:
        if sql('SELECT COUNT(*) FROM sys_user WHERE id=%d AND tenant_id=9999 AND deleted=0;'%uid)=='1':
            try: ok('DELETE','system/user/%d'%uid)
            except Exception as e: cleanup_errors.append(type(e).__name__)
    active=int(sql("SELECT COUNT(*) FROM sys_user WHERE tenant_id=9999 AND username='%s' AND deleted=0;"%name))
    links=int(sql("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_user u ON u.id=ur.user_id WHERE u.tenant_id=9999 AND u.username='%s';"%name))
    print('CLEANUP active=%d links=%d'%(active,links))
    assert not cleanup_errors and active==0 and links==0,(cleanup_errors,active,links)
    for uid in ids:
        assert sql('SELECT deleted FROM sys_user WHERE id=%d AND tenant_id=9999;'%uid)=='1', 'deletion history missing'
    print('HISTORY deleted=%d'%len(ids))
PY
