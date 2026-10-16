"""Generates api-tests/collections/us03-data-sync.postman_collection.json. Run: python3 api-tests/generators/us03_data_sync.py"""
import json, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from postman import req, unauthorized

def login(name, user, password, var):
    return req(name, "POST", "/api/v1/auth/login", f'''
pm.test("logged in (demo profile)", () => pm.response.to.have.status(200));
pm.collectionVariables.set("{var}", pm.response.json().accessToken);
''', body={"login": user, "password": password})

def problem(status, title):
    return f'''
pm.test("{status} ProblemDetail: {title}", () => {{
    pm.response.to.have.status({status});
    pm.expect(pm.response.headers.get("Content-Type")).to.include("application/problem+json");
    pm.expect(pm.response.json().title).to.eql("{title}");
}});
'''

ASH = "Bearer {{ashToken}}"
OAK = "Bearer {{oakToken}}"
KEYS = '["id", "pokedexNumber", "name", "category", "description", "heightM", "weightKg", "spriteUrl", "imageUrl", "types", "abilities", "stats", "localizedNames", "region", "habitat", "coordinates", "tags", "syncedAt", "version"]'

demo = [
    login("Log in as ash (USER)", "ash", "pikachu123", "ashToken"),
    login("Log in as oak (ADMIN)", "oak", "professor123", "oakToken"),
    req("The local store has the pre-inserted demo Pokémon", "GET", "/api/v1/local-pokemon?size=50", f'''
pm.test("200 page in the shared envelope", () => {{
    pm.response.to.have.status(200);
    pm.expect(pm.response.json()).to.include.keys("items", "page", "size", "totalItems", "totalPages", "hasNext", "hasPrevious");
}});
const items = pm.response.json().items;
pm.test("the six demo Pokémon, in Pokédex order", () => {{
    const numbers = items.map(p => p.pokedexNumber);
    pm.expect(numbers).to.include.members([1, 4, 7, 25, 133, 150]);
    pm.expect(numbers).to.eql([...numbers].sort((a, b) => a - b));
}});
pm.test("every item has the full contract", () => items.forEach(p => pm.expect(Object.keys(p)).to.have.members({KEYS})));
const pikachu = items.find(p => p.pokedexNumber === 25);
pm.collectionVariables.set("pikachuId", pikachu.id);
pm.collectionVariables.set("pikachuVersion", pikachu.version);
''', auth=ASH),
    req("Pikachu carries our proprietary data", "GET", "/api/v1/local-pokemon/{{pikachuId}}", f'''
pm.test("200 with the full contract", () => {{
    pm.response.to.have.status(200);
    pm.expect(Object.keys(pm.response.json())).to.have.members({KEYS});
}});
const p = pm.response.json();
pm.test("catalog fields came from PokeAPI", () => {{
    pm.expect(p.name).to.eql("pikachu");
    pm.expect(p.category).to.eql("Mouse Pokémon");
    pm.expect(p.types).to.eql(["electric"]);
    pm.expect(p.stats.total).to.eql(320);
    pm.expect(Date.parse(p.syncedAt)).to.be.a("number").and.not.NaN;
}});
pm.test("localized nomenclature, geography and classification tags", () => {{
    pm.expect(p.localizedNames).to.include({{ "ja": "ピカチュウ", "pt-BR": "Pikachu" }});
    pm.expect(p.region).to.eql("Kanto");
    pm.expect(p.habitat).to.eql("forest");
    pm.expect(p.coordinates.latitude).to.be.within(-90, 90);
    pm.expect(p.tags).to.include("mascot");
}});
''', auth=ASH),
]

sync = [
    req("Admin syncs a new Pokémon (#2 ivysaur)", "POST", "/api/v1/local-pokemon/sync/2", '''
pm.test("201 with a Location on the first run (200 if a previous run already synced it)", () => {
    pm.expect(pm.response.code).to.be.oneOf([200, 201]);
    if (pm.response.code === 201) {
        pm.expect(pm.response.headers.get("Location")).to.match(/\\/api\\/v1\\/local-pokemon\\/\\d+$/);
    }
});
const p = pm.response.json();
pm.test("a copy of the catalog entry", () => {
    pm.expect(p.pokedexNumber).to.eql(2);
    pm.expect(p.name).to.eql("ivysaur");
    pm.expect(p.types).to.eql(["grass", "poison"]);
});
''', auth=OAK),
    req("Admin re-syncs Pikachu: our data survives", "POST", "/api/v1/local-pokemon/sync/25", '''
pm.test("200: a refresh, not a new copy", () => {
    pm.response.to.have.status(200);
    pm.expect(pm.response.headers.get("Location")).to.be.undefined;
});
const p = pm.response.json();
pm.test("same local Pokémon, new version", () => {
    pm.expect(p.id).to.eql(Number(pm.collectionVariables.get("pikachuId")));
    pm.expect(p.version).to.eql(Number(pm.collectionVariables.get("pikachuVersion")) + 1);
});
pm.test("proprietary data untouched", () => {
    pm.expect(p.localizedNames.ja).to.eql("ピカチュウ");
    pm.expect(p.region).to.eql("Kanto");
    pm.expect(p.tags).to.include("mascot");
});
''', auth=OAK),
    req("Sync an unknown species (#1026)", "POST", "/api/v1/local-pokemon/sync/1026", problem(404, "Pokémon not found"), auth=OAK),
    req("Sync an alternate form (#10001, no species)", "POST", "/api/v1/local-pokemon/sync/10001", problem(404, "Pokémon not found"), auth=OAK),
    req("Sync #0", "POST", "/api/v1/local-pokemon/sync/0", problem(400, "Invalid Pokémon id"), auth=OAK),
    req("Sync #abc", "POST", "/api/v1/local-pokemon/sync/abc", problem(400, "Invalid Pokémon id"), auth=OAK),
]

access = [
    req("A USER can't sync", "POST", "/api/v1/local-pokemon/sync/25", problem(403, "Access denied"), auth=ASH),
    unauthorized("Anonymous sync", "POST", "/api/v1/local-pokemon/sync/25"),
    unauthorized("Anonymous list", "GET", "/api/v1/local-pokemon"),
    unauthorized("Anonymous read", "GET", "/api/v1/local-pokemon/1"),
]

errors = [
    req("Unknown local id", "GET", "/api/v1/local-pokemon/999999999", problem(404, "Local Pokémon not found"), auth=ASH),
    req("Local id 0", "GET", "/api/v1/local-pokemon/0", problem(400, "Invalid id"), auth=ASH),
    req("Local id abc", "GET", "/api/v1/local-pokemon/abc", problem(400, "Invalid id"), auth=ASH),
    req("Page size 51", "GET", "/api/v1/local-pokemon?size=51", problem(400, "Invalid pagination"), auth=ASH),
]

collection = {
    "info": {
        "name": "Poke API - US03 Data Synchronization",
        "_postman_id": "9d3f1a20-us03-poke-sync",
        "description": "Black-box tests for US03 (sync into the local store, read local copies) against the dockerized app with the demo profile and live PokeAPI.\n\nRun: `docker compose run --rm api-tests`.",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": [
        "// Applies to every request",
        "pm.test(\"responds within 10 s\", () => pm.expect(pm.response.responseTime).to.be.below(10000));",
        "pm.test(\"no stack traces or internal hosts leak\", () => {",
        "    pm.expect(pm.response.text()).to.not.match(/Exception|\\tat |127\\.0\\.0\\.1|pokeapi\\.co\\/api/);",
        "});",
    ]}}],
    "variable": [{"key": k, "value": ""} for k in ["ashToken", "oakToken", "pikachuId", "pikachuVersion"]],
    "item": [
        {"name": "1. Pre-inserted demo data", "item": demo},
        {"name": "2. Sync (ADMIN)", "item": sync},
        {"name": "3. Access control", "item": access},
        {"name": "4. Errors", "item": errors},
    ],
}
OUT = Path(__file__).parent.parent / "collections" / "us03-data-sync.postman_collection.json"
json.dump(collection, open(OUT, "w"), indent=2, ensure_ascii=False)
print(sum(len(f["item"]) for f in collection["item"]), "requests")
