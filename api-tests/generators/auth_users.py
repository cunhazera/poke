"""Generates api-tests/collections/auth-users.postman_collection.json. Run: python3 api-tests/generators/auth_users.py"""
import json, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from postman import req, unauthorized

def problem(status, title, extra=""):
    return f'''
pm.test("{status} ProblemDetail: {title}", () => {{
    pm.response.to.have.status({status});
    pm.expect(pm.response.headers.get("Content-Type")).to.include("application/problem+json");
    pm.expect(pm.response.json().title).to.eql("{title}");
}});
{extra}'''

TOKEN_CONTRACT = '''
pm.test("200 with a bearer token", () => {
    pm.response.to.have.status(200);
    const body = pm.response.json();
    pm.expect(Object.keys(body)).to.have.members(["accessToken", "tokenType", "expiresIn"]);
    pm.expect(body.tokenType).to.eql("Bearer");
    pm.expect(body.expiresIn).to.eql(3600);
    pm.expect(body.accessToken.split(".")).to.have.lengthOf(3);
});
'''

def login(name, login_value, password, save_as, desc=""):
    return req(name, "POST", "/api/v1/auth/login", TOKEN_CONTRACT + f'pm.collectionVariables.set("{save_as}", pm.response.json().accessToken);',
               body={"login": login_value, "password": password}, desc=desc)

def me(name, token_var, expected):
    checks = "\n".join(f'    pm.expect(body.{k}).to.eql({json.dumps(v)});' for k, v in expected.items())
    return req(name, "GET", "/api/v1/auth/me", f'''
pm.test("200: the token's user", () => {{
    pm.response.to.have.status(200);
    const body = pm.response.json();
    pm.expect(Object.keys(body)).to.have.members(["id", "username", "email", "role"]);
{checks}
}});
''', auth="Bearer {{" + token_var + "}}")

SUFFIX = '''
// A fresh user per run, so the collection can be re-run against the same database.
pm.collectionVariables.set("suffix", Date.now().toString(36));
'''

demo = [
    login("Log in as ash by username", "ash", "pikachu123", "ashToken", desc="Demo user pre-inserted by the seed."),
    me("ash is a USER", "ashToken", {"username": "ash", "email": "ash@pallet.town", "role": "USER"}),
    login("Log in as oak by email, in another case", "OAK@Pallet.Town", "professor123", "oakToken"),
    me("oak is an ADMIN", "oakToken", {"username": "oak", "role": "ADMIN"}),
]

registration = [
    req("Register a new trainer", "POST", "/api/v1/auth/register", '''
pm.test("201 with the user, never the password", () => {
    pm.response.to.have.status(201);
    const body = pm.response.json();
    pm.expect(Object.keys(body)).to.have.members(["id", "username", "email", "role"]);
    pm.expect(body.username).to.eql("Trainer-" + pm.collectionVariables.get("suffix"));
    pm.expect(body.role).to.eql("USER");
    pm.expect(pm.response.text()).to.not.include("pikachu123").and.not.include("$2a$");
});
''', body='{ "username": "Trainer-{{suffix}}", "email": "Trainer-{{suffix}}@Pallet.town", "password": "pikachu123" }', pre=SUFFIX),
    req("A client can't make itself ADMIN", "POST", "/api/v1/auth/register", '''
pm.test("201, and the role field is ignored", () => {
    pm.response.to.have.status(201);
    pm.expect(pm.response.json().role).to.eql("USER");
});
''', body='{ "username": "Sneaky-{{suffix}}", "email": "sneaky-{{suffix}}@pallet.town", "password": "pikachu123", "role": "ADMIN" }'),
    req("Same username in another case", "POST", "/api/v1/auth/register", problem(409, "Username taken"),
        body='{ "username": "TRAINER-{{suffix}}", "email": "other-{{suffix}}@pallet.town", "password": "pikachu123" }'),
    req("Same email in another case", "POST", "/api/v1/auth/register", problem(409, "Email already registered"),
        body='{ "username": "Other-{{suffix}}", "email": "trainer-{{suffix}}@PALLET.TOWN", "password": "pikachu123" }'),
    req("Password shorter than 8", "POST", "/api/v1/auth/register",
        problem(400, "Invalid user data", 'pm.test("names the field", () => pm.expect(pm.response.json().field).to.eql("password"));'),
        body={"username": "shorty", "email": "shorty@pallet.town", "password": "pika"}),
    req("Password longer than 72 bytes", "POST", "/api/v1/auth/register",
        problem(400, "Invalid user data", 'pm.test("names the field", () => pm.expect(pm.response.json().field).to.eql("password"));'),
        body={"username": "longpass", "email": "longpass@pallet.town", "password": "é" * 37}),
    req("Username with a space", "POST", "/api/v1/auth/register",
        problem(400, "Invalid user data", 'pm.test("names the field", () => pm.expect(pm.response.json().field).to.eql("username"));'),
        body={"username": "ash ketchum", "email": "ak@pallet.town", "password": "pikachu123"}),
    req("Malformed email", "POST", "/api/v1/auth/register",
        problem(400, "Invalid user data", 'pm.test("names the field", () => pm.expect(pm.response.json().field).to.eql("email"));'),
        body={"username": "noemail", "email": "ash@pallet", "password": "pikachu123"}),
    req("Missing fields", "POST", "/api/v1/auth/register", problem(400, "Invalid request body", '''
pm.test("lists each missing field", () => pm.expect(Object.keys(pm.response.json().errors)).to.have.members(["email", "password"]));
'''), body={"username": "lonely"}),
    req("Malformed JSON", "POST", "/api/v1/auth/register", 'pm.test("400", () => pm.response.to.have.status(400));', body="{ not json"),
    req("Form-encoded body", "POST", "/api/v1/auth/register", 'pm.test("415", () => pm.response.to.have.status(415));',
        body="username=ash&password=pikachu123", headers=[{"key": "Content-Type", "value": "application/x-www-form-urlencoded"}]),
]

login_folder = [
    login("The new trainer logs in by email, in any case", "trainer-{{suffix}}@PALLET.town", "pikachu123", "trainerToken"),
    me("The token belongs to the new trainer", "trainerToken", {"role": "USER"}),
    req("Wrong password", "POST", "/api/v1/auth/login", problem(401, "Invalid credentials", '''
pm.test("challenges for a bearer token", () => pm.expect(pm.response.headers.get("WWW-Authenticate")).to.eql("Bearer"));
pm.collectionVariables.set("wrongPasswordDetail", pm.response.json().detail);
'''), body={"login": "ash", "password": "pikachu124"}),
    req("Unknown user: the same answer as a wrong password", "POST", "/api/v1/auth/login", problem(401, "Invalid credentials", '''
pm.test("indistinguishable from a wrong password", () =>
    pm.expect(pm.response.json().detail).to.eql(pm.collectionVariables.get("wrongPasswordDetail")));
'''), body={"login": "gary", "password": "pikachu124"}),
    req("Passwords are case-sensitive", "POST", "/api/v1/auth/login", problem(401, "Invalid credentials"),
        body={"login": "ash", "password": "PIKACHU123"}),
    req("Blank credentials", "POST", "/api/v1/auth/login", problem(400, "Invalid request body"), body={"login": " ", "password": ""}),
]

protected = [
    unauthorized("/me without a token", "GET", "/api/v1/auth/me"),
    req("/me with a malformed token", "GET", "/api/v1/auth/me", problem(401, "Authentication required"), auth="Bearer not-a-jwt"),
    req("/me with a tampered token", "GET", "/api/v1/auth/me", problem(401, "Authentication required"),
        pre='''
// Flip one character of the signature: the payload is unchanged, so only the signature check can catch it.
// The first character, not the last: the last base64url character of a 32-byte signature carries 2 unused bits,
// so swapping it (A <-> B) can decode to the very same signature and the token stays valid (a 1-in-16 flake).
const [header, payload, signature] = pm.collectionVariables.get("ashToken").split(".");
const first = signature[0] === "A" ? "B" : "A";
pm.collectionVariables.set("tamperedToken", [header, payload, first + signature.slice(1)].join("."));
''', auth="Bearer {{tamperedToken}}"),
    req("/me with Basic auth instead of a bearer token", "GET", "/api/v1/auth/me", problem(401, "Authentication required"),
        auth="Basic YXNoOnBpa2FjaHUxMjM="),
    req("The catalog stays public", "GET", "/api/v1/pokemon?size=1", 'pm.test("200 without a token", () => pm.response.to.have.status(200));'),
    req("Health stays public", "GET", "/actuator/health", '''
pm.test("200 UP without a token", () => {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().status).to.eql("UP");
});
'''),
    unauthorized("Other actuator endpoints are not public", "GET", "/actuator/env"),
]

collection = {
    "info": {
        "name": "Poke API - Users and authentication",
        "_postman_id": "1b7e4c55-auth-poke-users",
        "description": "Black-box tests for registration, login (JWT) and protected routes. Needs the demo profile (pre-inserted users ash/pikachu123 and oak/professor123), which compose enables.\n\nRun: `docker compose run --rm api-tests`, or import into Postman with the `local` environment.",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": [
        "// Applies to every request",
        "pm.test(\"responds within 10 s\", () => pm.expect(pm.response.responseTime).to.be.below(10000));",
        "pm.test(\"no stack traces, internal hosts or password hashes leak\", () => {",
        "    pm.expect(pm.response.text()).to.not.match(/Exception|\\tat |127\\.0\\.0\\.1|\\$2a\\$|passwordHash/);",
        "});",
    ]}}],
    "variable": [{"key": k, "value": ""} for k in ["suffix", "ashToken", "oakToken", "trainerToken", "tamperedToken", "wrongPasswordDetail"]],
    "item": [
        {"name": "1. Demo accounts (pre-inserted)", "item": demo},
        {"name": "2. Registration", "item": registration},
        {"name": "3. Login", "item": login_folder},
        {"name": "4. Protected vs public routes", "item": protected},
    ],
}
OUT = Path(__file__).parent.parent / "collections" / "auth-users.postman_collection.json"
json.dump(collection, open(OUT, "w"), indent=2, ensure_ascii=False)
print(sum(len(f["item"]) for f in collection["item"]), "requests")
