-- Pokémon: the primary entity. Synced from PokeAPI (US03) and enriched with proprietary fields.
-- Units are PokeAPI's (decimetres, hectograms); conversion lives in the domain.
CREATE TABLE pokemon (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pokedex_number     INTEGER      UNIQUE CHECK (pokedex_number > 0),   -- NULL = created locally, never collides with a sync
    name               VARCHAR(100) NOT NULL,
    category           VARCHAR(100),
    description        TEXT,
    height_dm          INTEGER      NOT NULL CHECK (height_dm >= 0),
    weight_hg          INTEGER      NOT NULL CHECK (weight_hg >= 0),
    sprite_url         VARCHAR(500),
    image_url          VARCHAR(500),
    hp                 SMALLINT     NOT NULL CHECK (hp              BETWEEN 1 AND 255),
    attack             SMALLINT     NOT NULL CHECK (attack          BETWEEN 1 AND 255),
    defense            SMALLINT     NOT NULL CHECK (defense         BETWEEN 1 AND 255),
    special_attack     SMALLINT     NOT NULL CHECK (special_attack  BETWEEN 1 AND 255),
    special_defense    SMALLINT     NOT NULL CHECK (special_defense BETWEEN 1 AND 255),
    speed              SMALLINT     NOT NULL CHECK (speed           BETWEEN 1 AND 255),
    -- proprietary: geographical metadata
    region             VARCHAR(50),
    habitat            VARCHAR(50),
    latitude           NUMERIC(9,6) CHECK (latitude  BETWEEN -90  AND 90),
    longitude          NUMERIC(9,6) CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_pokemon_coordinates
        CHECK ((latitude IS NULL) = (longitude IS NULL)),                -- a coordinate needs both halves
    -- bookkeeping
    synced_at          TIMESTAMPTZ,                                       -- last PokeAPI sync; NULL if local-only
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0                    -- optimistic locking
);
CREATE UNIQUE INDEX uq_pokemon_name ON pokemon (lower(name));

CREATE TABLE pokemon_type (
    pokemon_id BIGINT      NOT NULL REFERENCES pokemon (id) ON DELETE CASCADE,
    slot       SMALLINT    NOT NULL CHECK (slot BETWEEN 1 AND 2),
    name       VARCHAR(30) NOT NULL,
    PRIMARY KEY (pokemon_id, slot)
);

CREATE TABLE pokemon_ability (
    pokemon_id BIGINT      NOT NULL REFERENCES pokemon (id) ON DELETE CASCADE,
    slot       SMALLINT    NOT NULL CHECK (slot BETWEEN 1 AND 3),
    name       VARCHAR(50) NOT NULL,
    hidden     BOOLEAN     NOT NULL,
    PRIMARY KEY (pokemon_id, slot)
);

-- proprietary: localized nomenclature
CREATE TABLE pokemon_localized_name (
    pokemon_id BIGINT       NOT NULL REFERENCES pokemon (id) ON DELETE CASCADE,
    language   VARCHAR(10)  NOT NULL CHECK (language ~ '^[a-z]{2,3}(-[A-Za-z0-9]{2,8})*$'),  -- BCP 47, e.g. "pt-BR"
    name       VARCHAR(100) NOT NULL,
    PRIMARY KEY (pokemon_id, language)
);

-- proprietary: internal classification tags
CREATE TABLE pokemon_tag (
    pokemon_id BIGINT      NOT NULL REFERENCES pokemon (id) ON DELETE CASCADE,
    tag        VARCHAR(50) NOT NULL CHECK (tag ~ '^[a-z0-9][a-z0-9-]*$'),                     -- e.g. "starter", "legendary"
    PRIMARY KEY (pokemon_id, tag)
);
CREATE INDEX ix_pokemon_tag_tag ON pokemon_tag (tag);                 -- "all Pokémon tagged X"
