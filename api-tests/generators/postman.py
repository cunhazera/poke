"""Small builders for Postman v2.1 requests, shared by the collection generators/patchers."""
import json

def tests(src):
    return [{"listen": "test", "script": {"type": "text/javascript", "exec": src.strip("\n").split("\n")}}]

def prerequest(src):
    return [{"listen": "prerequest", "script": {"type": "text/javascript", "exec": src.strip("\n").split("\n")}}]

def req(name, method, path, test_src, body=None, headers=None, desc="", pre=None, auth=None):
    hdrs = list(headers) if headers is not None else [{"key": "Accept", "value": "application/json"}]
    if auth:
        hdrs.append({"key": "Authorization", "value": auth})
    if body is not None and not any(h["key"] == "Content-Type" for h in hdrs):
        hdrs.append({"key": "Content-Type", "value": "application/json"})
    path_only, _, query = path.partition("?")
    url = {"raw": "{{baseUrl}}" + path, "host": ["{{baseUrl}}"], "path": path_only.lstrip("/").split("/")}
    if query:
        url["query"] = [dict(zip(("key", "value"), kv.split("=", 1))) for kv in query.split("&")]
    item = {"name": name, "event": (prerequest(pre) if pre else []) + tests(test_src),
            "request": {"method": method, "header": hdrs, "url": url, "description": desc}}
    if body is not None:
        item["request"]["body"] = {"mode": "raw", "raw": body if isinstance(body, str) else json.dumps(body)}
    return item

LOGIN_AS_ASH = req("Log in as the demo user (setup)", "POST", "/api/v1/auth/login", '''
pm.test("demo user ash can log in (needs the demo profile)", () => pm.response.to.have.status(200));
pm.collectionVariables.set("token", pm.response.json().accessToken);
''', body={"login": "ash", "password": "pikachu123"},
    desc="Pre-inserted by the demo seed (compose runs the app with SPRING_PROFILES_ACTIVE=demo).")

def unauthorized(name, method, path):
    return req(name, method, path, '''
pm.test("401 ProblemDetail with a Bearer challenge", () => {
    pm.response.to.have.status(401);
    pm.expect(pm.response.headers.get("WWW-Authenticate")).to.eql("Bearer");
    pm.expect(pm.response.headers.get("Content-Type")).to.include("application/problem+json");
    pm.expect(pm.response.json().title).to.eql("Authentication required");
});
''', desc="Everything outside the public routes needs a token, so the request is rejected before routing.")
