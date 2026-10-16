"""Generates api-tests/collections/search-by-name.postman_collection.json. Run: python3 -I api-tests/generators/search_by_name.py"""
import json, sys
from pathlib import Path
from urllib.parse import quote

sys.path.insert(0, str(Path(__file__).parent))
from postman import req, unauthorized

def problem(status, title):
    return f'''
pm.test("{status} ProblemDetail: {title}", () => {{
    pm.response.to.have.status({status});
    pm.expect(pm.response.headers.get("Content-Type")).to.include("application/problem+json");
    pm.expect(pm.response.json().title).to.eql("{title}");
}});
'''

def names_exactly(names):
    return f'''
pm.test("200 with exactly {", ".join(names) or "nothing"}, in Pokédex order", () => {{
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().items.map(p => p.name)).to.eql({json.dumps(names)});
    pm.expect(pm.response.json().totalItems).to.eql({len(names)});
}});
'''

def catalog(name, query, test_src, desc=""):
    return req(name, "GET", "/api/v1/pokemon?" + query, test_src, desc=desc)

ASH = "Bearer {{ashToken}}"

def local(name, query, test_src, desc=""):
    return req(name, "GET", "/api/v1/local-pokemon?" + query, test_src, auth=ASH, desc=desc)

catalog_search = [
    catalog("Part of a name matches anywhere in it", "name=char", names_exactly(
        ["charmander", "charmeleon", "charizard", "chimchar", "charjabug", "charcadet", "pecharunt"]) + '''
pm.test("each entry has the full listing contract", () => {
    pm.response.json().items.forEach(p => pm.expect(p).to.have.all.keys("id", "name", "spriteUrl", "category", "weightKg", "abilities"));
});
''', desc="Live PokeAPI: every species whose name contains 'char' (chimchar and pecharunt too)."),
    catalog("Case doesn't matter", "name=CHARIZ", names_exactly(["charizard"])),
    catalog("Typed like a person: 'Mr. Mime' finds mr-mime", "name=" + quote("Mr. Mime"), names_exactly(["mr-mime"])),
    catalog("An apostrophe is dropped: farfetch'd finds farfetchd", "name=" + quote("Farfetch'd"), names_exactly(["farfetchd"])),
    catalog("The matches are paged", "name=saur&page=1&size=2", '''
pm.test("page 2 of the 3 matches holds venusaur", () => {
    pm.response.to.have.status(200);
    const body = pm.response.json();
    pm.expect(body.items.map(p => p.name)).to.eql(["venusaur"]);
    pm.expect(body.totalItems).to.eql(3);
    pm.expect(body.totalPages).to.eql(2);
    pm.expect(body.hasPrevious).to.eql(true);
    pm.expect(body.hasNext).to.eql(false);
});
'''),
    catalog("No match is an empty page, not an error", "name=zzzz", '''
pm.test("200, empty, zero totals", () => {
    pm.response.to.have.status(200);
    const body = pm.response.json();
    pm.expect(body.items).to.eql([]);
    pm.expect(body.totalItems).to.eql(0);
    pm.expect(body.totalPages).to.eql(0);
    pm.expect(body.hasNext).to.eql(false);
});
'''),
    catalog("A blank search is no search", "name=&size=1", '''
pm.test("200 with the whole catalog", () => {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().totalItems).to.eql(1025);
});
'''),
    catalog("A repeated search comes from the cache", "name=saur", '''
pm.test("fast (index and entries cached)", () => {
    pm.response.to.have.status(200);
    pm.expect(pm.response.responseTime).to.be.below(150);
});
'''),
]

catalog_errors = [
    catalog("51 characters → 400", "name=" + "a" * 51, problem(400, "Invalid search")),
    catalog("No letter or digit → 400", "name=...", problem(400, "Invalid search")),
    catalog("A repeated name parameter → 400", "name=char&name=pika", problem(400, "Invalid query parameter")),
]

local_search = [
    req("Log in as ash (USER)", "POST", "/api/v1/auth/login", '''
pm.test("logged in (demo profile)", () => pm.response.to.have.status(200));
pm.collectionVariables.set("ashToken", pm.response.json().accessToken);
''', body={"login": "ash", "password": "pikachu123"}),
    local("By name, any case", "q=CHARM", '''
pm.test("200 with the demo charmander", () => {
    pm.response.to.have.status(200);
    const names = pm.response.json().items.map(p => p.name);
    pm.expect(names).to.include("charmander");
    names.forEach(n => pm.expect(n).to.include("charm"));
});
'''),
    local("By a Japanese localized name", "q=" + quote("ヒトカゲ"), names_exactly(["charmander"])),
    local("By part of a French localized name, accent included", "q=" + quote("SALAMÈ"), names_exactly(["charmander"])),
    local("By the start of a localized name: Bulbassauro (pt-BR)", "q=bulbass", names_exactly(["bulbasaur"])),
    local("No match is an empty page", "q=zzzz", '''
pm.test("200, empty", () => {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().items).to.eql([]);
    pm.expect(pm.response.json().totalItems).to.eql(0);
});
'''),
    local("51 characters → 400", "q=" + "a" * 51, problem(400, "Invalid search")),
    unauthorized("Searching the local store needs a token", "GET", "/api/v1/local-pokemon?q=pika"),
]

collection = {
    "info": {
        "name": "Poke API - Search by name",
        "_postman_id": "4be1c0de-search-poke-name",
        "description": "Black-box tests for searching by name: the Pokédex (`GET /api/v1/pokemon?name=`, over PokeAPI's cached species index) and the local store (`GET /api/v1/local-pokemon?q=`, names and localized names) against the dockerized app with the demo profile and live PokeAPI.\n\nRun: `docker compose run --rm api-tests`.",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": [
        "// Applies to every request",
        "pm.test(\"responds within 10 s\", () => pm.expect(pm.response.responseTime).to.be.below(10000));",
        "pm.test(\"no stack traces or internal hosts leak\", () => {",
        "    pm.expect(pm.response.text()).to.not.match(/Exception|\\tat |127\\.0\\.0\\.1|pokeapi\\.co\\/api/);",
        "});",
    ]}}],
    "variable": [{"key": "ashToken", "value": ""}],
    "item": [
        {"name": "1. Pokédex search", "item": catalog_search},
        {"name": "2. Pokédex search errors", "item": catalog_errors},
        {"name": "3. Local store search", "item": local_search},
    ],
}
OUT = Path(__file__).parent.parent / "collections" / "search-by-name.postman_collection.json"
json.dump(collection, open(OUT, "w"), indent=2, ensure_ascii=False)
print(sum(len(f["item"]) for f in collection["item"]), "requests")
