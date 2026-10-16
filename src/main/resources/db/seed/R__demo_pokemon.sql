-- Demo Pokémon, loaded only by the `demo` profile (application-demo.properties adds this location).
-- The catalog columns are the app's own output of POST /api/v1/local-pokemon/sync/{n} against live PokeAPI;
-- the proprietary columns (names, region, habitat, coordinates, tags) are demo values.
-- Repeatable migration: re-applied when this file changes; ON CONFLICT keeps it safe and never overwrites a later sync.

-- #1 bulbasaur
INSERT INTO pokemon (pokedex_number, name, category, description, height_dm, weight_hg, sprite_url, image_url,
                     hp, attack, defense, special_attack, special_defense, speed, region, habitat, latitude, longitude, synced_at)
VALUES (1, 'bulbasaur', 'Seed Pokémon', 'While it is young, it uses the nutrients that are stored in the seed on its back in order to grow.', 7, 69, 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/1.png', 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/1.png', 45, 49, 49, 65, 65, 45, 'Kanto', 'grassland', '35.658581', '139.745433', '2026-10-16T01:58:10.591217Z')
ON CONFLICT DO NOTHING;
INSERT INTO pokemon_type SELECT (SELECT id FROM pokemon WHERE pokedex_number = 1), v.* FROM (VALUES (1, 'grass'), (2, 'poison')) AS v ON CONFLICT DO NOTHING;
INSERT INTO pokemon_ability SELECT (SELECT id FROM pokemon WHERE pokedex_number = 1), v.* FROM (VALUES (1, 'overgrow', false), (2, 'chlorophyll', true)) AS v ON CONFLICT DO NOTHING;
INSERT INTO pokemon_localized_name SELECT (SELECT id FROM pokemon WHERE pokedex_number = 1), v.* FROM (VALUES ('fr', 'Bulbizarre'), ('ja', 'フシギダネ'), ('pt-BR', 'Bulbassauro')) AS v ON CONFLICT DO NOTHING;
INSERT INTO pokemon_tag SELECT (SELECT id FROM pokemon WHERE pokedex_number = 1), v.* FROM (VALUES ('gen-1'), ('starter')) AS v ON CONFLICT DO NOTHING;

-- #4 charmander
INSERT INTO pokemon (pokedex_number, name, category, description, height_dm, weight_hg, sprite_url, image_url,
                     hp, attack, defense, special_attack, special_defense, speed, region, habitat, latitude, longitude, synced_at)
VALUES (4, 'charmander', 'Lizard Pokémon', 'From the time it is born, a flame burns at the tip of its tail. Its life would end if the flame were to go out.', 6, 85, 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/4.png', 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/4.png', 39, 52, 43, 60, 50, 65, 'Kanto', 'mountain', '35.360556', '138.727778', '2026-10-16T01:58:10.843442Z')
ON CONFLICT DO NOTHING;
INSERT INTO pokemon_type SELECT (SELECT id FROM pokemon WHERE pokedex_number = 4), v.* FROM (VALUES (1, 'fire')) AS v ON CONFLICT DO NOTHING;
INSERT INTO pokemon_ability SELECT (SELECT id FROM pokemon WHERE pokedex_number = 4), v.* FROM (VALUES (1, 'blaze', false), (2, 'solar-power', true)) AS v ON CONFLICT DO NOTHING;
INSERT INTO pokemon_localized_name SELECT (SELECT id FROM pokemon WHERE pokedex_number = 4), v.* FROM (VALUES ('fr', 'Salamèche'), ('ja', 'ヒトカゲ'), ('pt-BR', 'Charmander')) AS v ON CONFLICT DO NOTHING;
INSERT INTO pokemon_tag SELECT (SELECT id FROM pokemon WHERE pokedex_number = 4), v.* FROM (VALUES ('gen-1'), ('starter')) AS v ON CONFLICT DO NOTHING;

-- #7 squirtle
INSERT INTO pokemon (pokedex_number, name, category, description, height_dm, weight_hg, sprite_url, image_url,
                     hp, attack, defense, special_attack, special_defense, speed, region, habitat, latitude, longitude, synced_at)
VALUES (7, 'squirtle', 'Tiny Turtle Pokémon', 'When it feels threatened, it draws its limbs inside its shell and sprays water from its mouth.', 5, 90, 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/7.png', 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/7.png', 44, 48, 65, 50, 64, 43, 'Kanto', 'waters-edge', '35.308611', '139.550278', '2026-10-16T01:58:11.068622Z')
ON CONFLICT DO NOTHING;
INSERT INTO pokemon_type SELECT (SELECT id FROM pokemon WHERE pokedex_number = 7), v.* FROM (VALUES (1, 'water')) AS v ON CONFLICT DO NOTHING;
INSERT INTO pokemon_ability SELECT (SELECT id FROM pokemon WHERE pokedex_number = 7), v.* FROM (VALUES (1, 'torrent', false), (2, 'rain-dish', true)) AS v ON CONFLICT DO NOTHING;
INSERT INTO pokemon_localized_name SELECT (SELECT id FROM pokemon WHERE pokedex_number = 7), v.* FROM (VALUES ('fr', 'Carapuce'), ('ja', 'ゼニガメ'), ('pt-BR', 'Squirtle')) AS v ON CONFLICT DO NOTHING;
INSERT INTO pokemon_tag SELECT (SELECT id FROM pokemon WHERE pokedex_number = 7), v.* FROM (VALUES ('gen-1'), ('starter')) AS v ON CONFLICT DO NOTHING;

-- #25 pikachu
INSERT INTO pokemon (pokedex_number, name, category, description, height_dm, weight_hg, sprite_url, image_url,
                     hp, attack, defense, special_attack, special_defense, speed, region, habitat, latitude, longitude, synced_at)
VALUES (25, 'pikachu', 'Mouse Pokémon', 'Possesses cheek sacs in which it stores electricity. This clever forest-dweller roasts tough berries with an electric shock before consuming them.', 4, 60, 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/25.png', 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/25.png', 35, 55, 40, 50, 50, 90, 'Kanto', 'forest', '35.676200', '139.650300', '2026-10-16T01:58:11.141975Z')
ON CONFLICT DO NOTHING;
INSERT INTO pokemon_type SELECT (SELECT id FROM pokemon WHERE pokedex_number = 25), v.* FROM (VALUES (1, 'electric')) AS v ON CONFLICT DO NOTHING;
INSERT INTO pokemon_ability SELECT (SELECT id FROM pokemon WHERE pokedex_number = 25), v.* FROM (VALUES (1, 'static', false), (2, 'lightning-rod', true)) AS v ON CONFLICT DO NOTHING;
INSERT INTO pokemon_localized_name SELECT (SELECT id FROM pokemon WHERE pokedex_number = 25), v.* FROM (VALUES ('fr', 'Pikachu'), ('ja', 'ピカチュウ'), ('pt-BR', 'Pikachu')) AS v ON CONFLICT DO NOTHING;
INSERT INTO pokemon_tag SELECT (SELECT id FROM pokemon WHERE pokedex_number = 25), v.* FROM (VALUES ('gen-1'), ('mascot')) AS v ON CONFLICT DO NOTHING;

-- #133 eevee
INSERT INTO pokemon (pokedex_number, name, category, description, height_dm, weight_hg, sprite_url, image_url,
                     hp, attack, defense, special_attack, special_defense, speed, region, habitat, latitude, longitude, synced_at)
VALUES (133, 'eevee', 'Evolution Pokémon', 'Harbors the potential to evolve into manifold forms. Within Eevee lies the key to the mysteries of Pokémon evolution—I''m certain of it.', 3, 65, 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/133.png', 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/133.png', 55, 55, 50, 45, 65, 55, 'Kanto', 'urban', NULL, NULL, '2026-10-16T01:58:11.200498Z')
ON CONFLICT DO NOTHING;
INSERT INTO pokemon_type SELECT (SELECT id FROM pokemon WHERE pokedex_number = 133), v.* FROM (VALUES (1, 'normal')) AS v ON CONFLICT DO NOTHING;
INSERT INTO pokemon_ability SELECT (SELECT id FROM pokemon WHERE pokedex_number = 133), v.* FROM (VALUES (1, 'run-away', false), (2, 'adaptability', false), (3, 'anticipation', true)) AS v ON CONFLICT DO NOTHING;
INSERT INTO pokemon_localized_name SELECT (SELECT id FROM pokemon WHERE pokedex_number = 133), v.* FROM (VALUES ('fr', 'Évoli'), ('ja', 'イーブイ'), ('pt-BR', 'Eevee')) AS v ON CONFLICT DO NOTHING;
INSERT INTO pokemon_tag SELECT (SELECT id FROM pokemon WHERE pokedex_number = 133), v.* FROM (VALUES ('branching-evolution'), ('gen-1')) AS v ON CONFLICT DO NOTHING;

-- #150 mewtwo
INSERT INTO pokemon (pokedex_number, name, category, description, height_dm, weight_hg, sprite_url, image_url,
                     hp, attack, defense, special_attack, special_defense, speed, region, habitat, latitude, longitude, synced_at)
VALUES (150, 'mewtwo', 'Genetic Pokémon', 'Its DNA is almost the same as Mew’s. However, its size and disposition are vastly different.', 20, 1220, 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/150.png', 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/150.png', 106, 110, 90, 154, 90, 130, 'Kanto', 'rare', NULL, NULL, '2026-10-16T01:58:11.281263Z')
ON CONFLICT DO NOTHING;
INSERT INTO pokemon_type SELECT (SELECT id FROM pokemon WHERE pokedex_number = 150), v.* FROM (VALUES (1, 'psychic')) AS v ON CONFLICT DO NOTHING;
INSERT INTO pokemon_ability SELECT (SELECT id FROM pokemon WHERE pokedex_number = 150), v.* FROM (VALUES (1, 'pressure', false), (2, 'unnerve', true)) AS v ON CONFLICT DO NOTHING;
INSERT INTO pokemon_localized_name SELECT (SELECT id FROM pokemon WHERE pokedex_number = 150), v.* FROM (VALUES ('fr', 'Mewtwo'), ('ja', 'ミュウツー'), ('pt-BR', 'Mewtwo')) AS v ON CONFLICT DO NOTHING;
INSERT INTO pokemon_tag SELECT (SELECT id FROM pokemon WHERE pokedex_number = 150), v.* FROM (VALUES ('gen-1'), ('legendary')) AS v ON CONFLICT DO NOTHING;
