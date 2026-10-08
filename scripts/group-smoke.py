#!/usr/bin/env python3
"""Exercise real HTTP routes against the isolated demo started by group-local.py.
This intentionally uses only loopback and the synthetic local accounts.
"""
import json, urllib.request, urllib.error, uuid
BASE = "http://127.0.0.1:18080"

def request(path, token=None, method="GET", body=None, expected=200):
    headers={"Content-Type":"application/json"}
    if token: headers["Authorization"]="Bearer "+token
    req=urllib.request.Request(BASE+path, headers=headers, method=method, data=json.dumps(body).encode() if body is not None else None)
    try:
        with urllib.request.urlopen(req,timeout=10) as response: status=response.status; content=response.read()
    except urllib.error.HTTPError as e: status=e.code; content=e.read()
    assert status==expected, f"{method} {path}: expected {expected}, got {status}: {content[:200]}"
    return json.loads(content) if content else None

def login(name): return request("/api/auth/login",method="POST",body={"email":name+"@local.test","password":"StudyLocal123!"})
alice,bob,carol,admin=[login(name) for name in ["alice","bob","carol","admin"]]
a,b,c,d=[s["accessToken"] for s in [alice,bob,carol,admin]]
actor=alice["user"]["userId"]; bobid=bob["user"]["userId"]
name="API verification "+uuid.uuid4().hex[:6]
body={"groupName":name,"description":"Local route verification", "courseCode":"IS442", "maxGroupSize":3,"meetingMode":"ONLINE","studyGoals":["CONCEPT_REVIEW"],"availability":[]}
g=request("/api/groups",a,"POST",body,201);gid=g["groupId"];path="/api/groups/"+gid
assert g["memberCount"]==1 and g["isLeader"]
request(path,None,expected=401);request(path,d,expected=403)
request(path+"/close?leaderId="+actor,c,"POST",expected=403)
request(path,a,"PUT",{**body,"groupName":"   "},400)
request(path,a,"PUT",{**body,"availability":[{"day":"MONDAY","startTime":"19:00","endTime":"18:00"}]},400)
br=request(path+"/join-requests",b,"POST",expected=201);cr=request(path+"/join-requests",c,"POST",expected=201)
request(path+"/join-requests",b,"POST",expected=409)
request(path+"/join-requests",c,expected=403)
assert any(r["groupJoinRequestId"]==br["groupJoinRequestId"] for r in request("/api/groups/join-requests/mine",b))
request("/api/groups/join-requests/"+br["groupJoinRequestId"]+"/accept",c,"POST",expected=403)
request("/api/groups/join-requests/"+br["groupJoinRequestId"]+"/accept",a,"POST")
request("/api/groups/join-requests/"+br["groupJoinRequestId"]+"/reject",a,"POST",expected=409)
request("/api/groups/join-requests/"+cr["groupJoinRequestId"]+"/reject",a,"POST")
request(path,a,"PUT",{**body,"maxGroupSize":2})
request(path+"/join-requests",c,"POST",expected=409)
assert request(path,b)["isMember"] and request(path,b)["memberCount"]==2
request(path+"/members/"+actor,a,"DELETE",expected=409)
request(path+"/members/"+bobid,a,"DELETE",expected=204)
request(path+"/join-requests",b,"POST",expected=201)
request(path+"/close",a,"POST",expected=204);request(path+"/close",a,"POST",expected=204)
assert request(path,a)["status"]=="CLOSED"
assert all(r["status"]!="PENDING" for r in request(path+"/join-requests",a))
request(path,a,"PUT",body,409)
assert not any(g["groupId"]==gid for g in request("/api/groups",c))
# Existing adjacent contracts should still work through the gateway.
request("/api/students",a);request("/api/courses");request("/api/students/"+actor,a)
request("/api/admin/accounts",d)
print("PASS: real gateway create/edit/request/accept/reject/remove/close, permissions, validation, capacity, history and existing student/course/admin reads.")
