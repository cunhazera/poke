"""Generates api-tests/collections/us04-local-crud.postman_collection.json. Run: python3 api-tests/generators/us04_local_crud.py"""
import json, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from postman import req, unauthorized

def login(name, user, password, var):
    return req(name, "POST", "/api/v1/auth/login", f'''
pm.test("logged in (demo profile)", () => pm.response.to.have.status(200));
pm.collectionVariables.set("{var}", pm.response.json().accessToken);
''', body={"login": user, "password": password})

def problem(status, title, field=None):
    check = f'\n    pm.expect(pm.response.json().field).to.eql("{field}");' if field else ""
    return f'''
pm.test("{status} ProblemDetail: {title}", () => {{
    pm.response.to.have.status({status});
    pm.expect(pm.response.headers.get("Content-Type")).to.include("application/problem+json");
    pm.expect(pm.response.json().title).to.eql("{title}");{check}
}});
'''

ASH = "Bearer {{ashToken}}"
OAK = "Bearer {{oakToken}}"
LOCAL = "/api/v1/local-pokemon"

KANTO = {"localizedNames": {"ja": "フシギソウ", "pt-BR": "Ivysaur"}, "region": "Kanto", "habitat": "grassland",
         "coordinates": {"latitude": 35.6762, "longitude": 139.6503}, "tags": ["gen-1", "newman"]}

def homebrew(name="{{localName}}", **overrides):
    body = {"name": name, "category": "Glitch Pokémon", "description": "Made by the Newman suite.",
            "heightM": 1.0, "weightKg": 1590.8, "spriteUrl": None, "imageUrl": "https://example.com/missingno.png",
            "types": ["normal"], "abilities": [{"name": "pressure", "hidden": False}],
            "stats": {"hp": 33, "attack": 136, "defense": 1, "specialAttack": 6, "specialDefense": 6, "speed": 29},
            "tags": ["glitch"]}
    body.update(overrides)
    return body

def raw(body, **replacements):
    """JSON text, so {{variables}} can stand where numbers go."""
    text = json.dumps(body, ensure_ascii=False)
    for key, value in replacements.items():
        text = text.replace(f'"{key}"', value)
    return text

UNIQUE_NAME = 'pm.collectionVariables.set("localName", "newman-" + Date.now());'

setup = [
    login("Log in as ash (USER)", "ash", "pikachu123", "ashToken"),
    login("Log in as oak (ADMIN)", "oak", "professor123", "oakToken"),
    req("Admin syncs #2 ivysaur to edit (setup)", "POST", f"{LOCAL}/sync/2", '''
pm.test("201 or 200 (already synced by an earlier run)", () => pm.expect(pm.response.code).to.be.oneOf([200, 201]));
pm.collectionVariables.set("ivysaurId", pm.response.json().id);
pm.collectionVariables.set("ivysaurVersion", pm.response.json().version);
''', auth=OAK),
]

synced = [
    req("Edit our data on a synced Pokémon", "PUT", f"{LOCAL}/{{{{ivysaurId}}}}", '''
pm.test("200 with a new version", () => {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().version).to.eql(Number(pm.collectionVariables.get("ivysaurVersion")) + 1);
});
const p = pm.response.json();
pm.test("the proprietary fields are replaced", () => {
    pm.expect(p.localizedNames).to.eql({ "ja": "フシギソウ", "pt-BR": "Ivysaur" });
    pm.expect(p.region).to.eql("Kanto");
    pm.expect(p.coordinates).to.eql({ "latitude": 35.6762, "longitude": 139.6503 });
    pm.expect(p.tags).to.eql(["gen-1", "newman"]);
});
pm.test("the catalog fields are untouched", () => {
    pm.expect(p.name).to.eql("ivysaur");
    pm.expect(p.pokedexNumber).to.eql(2);
    pm.expect(p.types).to.eql(["grass", "poison"]);
});
pm.collectionVariables.set("staleVersion", pm.collectionVariables.get("ivysaurVersion"));
pm.collectionVariables.set("ivysaurVersion", p.version);
''', body=raw({"version": "V", **KANTO}, V="{{ivysaurVersion}}"), auth=OAK),
    req("Re-sync it: our edit survives", "POST", f"{LOCAL}/sync/2", '''
pm.test("200 refresh", () => pm.response.to.have.status(200));
const p = pm.response.json();
pm.test("the edit is still there", () => {
    pm.expect(p.region).to.eql("Kanto");
    pm.expect(p.localizedNames.ja).to.eql("フシギソウ");
    pm.expect(p.tags).to.include("newman");
});
pm.collectionVariables.set("ivysaurVersion", p.version);
''', auth=OAK),
    req("An edit made on a stale version", "PUT", f"{LOCAL}/{{{{ivysaurId}}}}", problem(409, "Concurrent update"),
        body=raw({"version": "V", **KANTO, "region": "Johto"}, V="{{staleVersion}}"), auth=OAK),
    req("Catalog fields of a synced Pokémon", "PUT", f"{LOCAL}/{{{{ivysaurId}}}}", problem(400, "Catalog fields not editable"),
        body=raw({"version": "V", **homebrew("ivysaur")}, V="{{ivysaurVersion}}"), auth=OAK),
    req("The store still has our edit", "GET", f"{LOCAL}/{{{{ivysaurId}}}}", '''
pm.test("neither rejected edit changed anything", () => {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().region).to.eql("Kanto");
    pm.expect(pm.response.json().category).to.not.eql("Glitch Pokémon");
    pm.expect(pm.response.json().version).to.eql(Number(pm.collectionVariables.get("ivysaurVersion")));
});
''', auth=ASH),
]

crud = [
    req("Create a local Pokémon", "POST", LOCAL, '''
pm.test("201 with a Location", () => {
    pm.response.to.have.status(201);
    pm.expect(pm.response.headers.get("Location")).to.match(/\\/api\\/v1\\/local-pokemon\\/\\d+$/);
});
const p = pm.response.json();
pm.test("exists only locally", () => {
    pm.expect(p.pokedexNumber).to.be.null;
    pm.expect(p.syncedAt).to.be.null;
    pm.expect(p.version).to.eql(0);
});
pm.test("units and derived fields", () => {
    pm.expect(p.name).to.eql(pm.collectionVariables.get("localName"));
    pm.expect(p.heightM).to.eql(1.0);
    pm.expect(p.weightKg).to.eql(1590.8);
    pm.expect(p.stats.total).to.eql(211);
    pm.expect(p.localizedNames).to.eql({});
    pm.expect(p.coordinates).to.be.null;
});
pm.collectionVariables.set("localId", p.id);
''', body=homebrew(), pre=UNIQUE_NAME, auth=OAK),
    req("A signed-in user reads it", "GET", f"{LOCAL}/{{{{localId}}}}", '''
pm.test("200, the same Pokémon", () => {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().name).to.eql(pm.collectionVariables.get("localName"));
});
''', auth=ASH),
    req("Replace it whole", "PUT", f"{LOCAL}/{{{{localId}}}}", '''
pm.test("200, version 1", () => {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().version).to.eql(1);
});
pm.test("catalog and proprietary fields replaced", () => {
    const p = pm.response.json();
    pm.expect(p.types).to.eql(["normal", "flying"]);
    pm.expect(p.region).to.eql("Kanto");
    pm.expect(p.tags).to.eql(["gen-1", "newman"]);
});
''', body=raw({"version": 0, **homebrew(types=["normal", "flying"]), **KANTO}), auth=OAK),
    req("A local Pokémon needs its catalog fields", "PUT", f"{LOCAL}/{{{{localId}}}}",
        problem(400, "Invalid Pokémon data", "name"), body=raw({"version": 1, **KANTO}), auth=OAK),
    req("Its name is taken now", "POST", LOCAL, problem(409, "Name taken"), body=homebrew(), auth=OAK),
    req("A synced Pokémon's name is taken, in any case", "POST", LOCAL, problem(409, "Name taken"),
        body=homebrew("pikachu"), auth=OAK),
    req("Delete it", "DELETE", f"{LOCAL}/{{{{localId}}}}", '''
pm.test("204 with no body", () => {
    pm.response.to.have.status(204);
    pm.expect(pm.response.text()).to.eql("");
});
''', auth=OAK),
    req("It's gone", "GET", f"{LOCAL}/{{{{localId}}}}", problem(404, "Local Pokémon not found"), auth=ASH),
    req("Deleting it again", "DELETE", f"{LOCAL}/{{{{localId}}}}", problem(404, "Local Pokémon not found"), auth=OAK),
]

def invalid(name, body, title, field=None, method="POST", path=LOCAL, headers=None):
    return req(name, method, path, problem(400, title, field), body=body, headers=headers, auth=OAK)

validation = [
    req("Missing required fields are listed", "POST", LOCAL, '''
pm.test("400 listing every missing field", () => {
    pm.response.to.have.status(400);
    pm.expect(pm.response.json().title).to.eql("Invalid request body");
    pm.expect(pm.response.json().errors).to.include.keys("heightM", "weightKg", "types", "abilities", "stats");
});
''', body={"name": "incomplete"}, auth=OAK),
    invalid("An unknown field (pokedexNumber)", homebrew("x", pokedexNumber=999), "Unknown field", "pokedexNumber"),
    invalid("A height with two decimals", homebrew("x", heightM=0.25), "Invalid Pokémon data", "heightM"),
    invalid("A misspelled type", homebrew("x", types=["fyre"]), "Invalid Pokémon data", "types"),
    invalid("The same type twice", homebrew("x", types=["fire", "fire"]), "Invalid Pokémon data", "types"),
    invalid("A stat of 256", homebrew("x", stats={"hp": 256, "attack": 1, "defense": 1, "specialAttack": 1, "specialDefense": 1, "speed": 1}),
            "Invalid Pokémon data", "stats"),
    invalid("A fractional stat", raw(homebrew("x")).replace('"hp": 33', '"hp": 33.5'), "Invalid field value", "stats.hp"),
    invalid("A stat that isn't a number", raw(homebrew("x")).replace('"hp": 33', '"hp": "high"'), "Invalid field value", "stats.hp"),
    invalid("A capitalized name", homebrew("MissingNo"), "Invalid Pokémon data", "name"),
    invalid("A javascript: image URL", homebrew("x", imageUrl="javascript:alert(1)"), "Invalid Pokémon data", "imageUrl"),
    invalid("A latitude of 91", homebrew("x", coordinates={"latitude": 91, "longitude": 0}), "Invalid Pokémon data", "coordinates"),
    invalid("A tag with spaces", homebrew("x", tags=["Not A Slug"]), "Invalid Pokémon data", "tags"),
    invalid("Malformed JSON", '{"name": ', "Malformed request body"),
    req("A form body", "POST", LOCAL, '''
pm.test("415 ProblemDetail", () => {
    pm.response.to.have.status(415);
    pm.expect(pm.response.headers.get("Content-Type")).to.include("application/problem+json");
});
''', body="name=x", headers=[{"key": "Content-Type", "value": "application/x-www-form-urlencoded"}], auth=OAK),
    req("An edit without a version", "PUT", f"{LOCAL}/{{{{ivysaurId}}}}", '''
pm.test("400 naming version", () => {
    pm.response.to.have.status(400);
    pm.expect(pm.response.json().errors).to.have.property("version");
});
''', body=KANTO, auth=OAK),
    invalid("Edit id 0", raw({"version": 0, **KANTO}), "Invalid id", method="PUT", path=f"{LOCAL}/0"),
    req("Edit an unknown id", "PUT", f"{LOCAL}/999999999", problem(404, "Local Pokémon not found"),
        body=raw({"version": 0, **KANTO}), auth=OAK),
    req("Delete id abc", "DELETE", f"{LOCAL}/abc", problem(400, "Invalid id"), auth=OAK),
]

access = [
    req("A USER can't create", "POST", LOCAL, problem(403, "Access denied"), body=homebrew("x"), auth=ASH),
    req("A USER can't edit", "PUT", f"{LOCAL}/{{{{ivysaurId}}}}", problem(403, "Access denied"),
        body=raw({"version": 0, **KANTO}), auth=ASH),
    req("A USER can't delete", "DELETE", f"{LOCAL}/{{{{ivysaurId}}}}", problem(403, "Access denied"), auth=ASH),
    unauthorized("Anonymous create", "POST", LOCAL),
    unauthorized("Anonymous edit", "PUT", f"{LOCAL}/1"),
    unauthorized("Anonymous delete", "DELETE", f"{LOCAL}/1"),
]

collection = {
    "info": {
        "name": "Poke API - US04 Local Data Modification (CRUD)",
        "_postman_id": "4b7e2c51-us04-poke-local-crud",
        "description": "Black-box tests for US04 (create, update and delete local Pokémon, with validation) against the dockerized app with the demo profile and live PokeAPI. Re-runnable: it edits #2 ivysaur and creates and deletes its own local Pokémon.\n\nRun: `docker compose run --rm api-tests`.",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": [
        "// Applies to every request",
        "pm.test(\"responds within 10 s\", () => pm.expect(pm.response.responseTime).to.be.below(10000));",
        "pm.test(\"no stack traces or internal hosts leak\", () => {",
        "    pm.expect(pm.response.text()).to.not.match(/Exception|\\tat |127\\.0\\.0\\.1|pokeapi\\.co\\/api/);",
        "});",
    ]}}],
    "variable": [{"key": k, "value": ""} for k in ["ashToken", "oakToken", "ivysaurId", "ivysaurVersion", "staleVersion", "localName", "localId"]],
    "item": [
        {"name": "1. Setup", "item": setup},
        {"name": "2. Editing a synced Pokémon", "item": synced},
        {"name": "3. A local Pokémon, create to delete", "item": crud},
        {"name": "4. Validation", "item": validation},
        {"name": "5. Access control", "item": access},
    ],
}
OUT = Path(__file__).parent.parent / "collections" / "us04-local-crud.postman_collection.json"
json.dump(collection, open(OUT, "w"), indent=2, ensure_ascii=False)
print(sum(len(f["item"]) for f in collection["item"]), "requests")
