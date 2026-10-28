CREATE TABLE category (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(60) NOT NULL,
    display_order INTEGER     NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX category_name_uq ON category (lower(name));

-- Prices are integer Rappen (design D6).
CREATE TABLE menu_item (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id   BIGINT       NOT NULL REFERENCES category (id) ON DELETE RESTRICT,
    name          VARCHAR(80)  NOT NULL,
    type          VARCHAR(5)   NOT NULL CHECK (type IN ('food', 'drink')),
    price_rappen  INTEGER      NOT NULL CHECK (price_rappen >= 0),
    description   VARCHAR(200),
    display_order INTEGER      NOT NULL DEFAULT 0,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    image_ref     VARCHAR(100),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX menu_item_category_idx ON menu_item (category_id, display_order);
