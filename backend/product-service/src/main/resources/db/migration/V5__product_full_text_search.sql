-- Tim kiem toan van (full-text search) cho san pham bang tsvector cua PostgreSQL.
--
-- - unaccent: bo dau tieng Viet o CA du lieu va tu khoa -> "dien thoai" khop "Điện thoại".
-- - Cau hinh 'simple': chi tach tu + ha chu thuong, khong stemming (Postgres khong co tu dien
--   tieng Viet; stemming 'english' se cat sai tu tieng Viet).
-- - Cot GENERATED ... STORED: Postgres tu cap nhat khi name/description doi, khong can trigger
--   hay code Java. Ten nang trong so A, mo ta trong so C (ts_rank uu tien khop o ten).
-- - GIN index: tra cuu @@ khong phai quet ca bang.

CREATE EXTENSION IF NOT EXISTS unaccent;

-- unaccent() chi la STABLE (phu thuoc search_path), khong dung duoc trong cot generated/index.
-- Boc lai thanh ham IMMUTABLE, chi ro schema cua ham va tu dien de ket qua co dinh.
CREATE OR REPLACE FUNCTION immutable_unaccent(text)
    RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$ SELECT public.unaccent('public.unaccent'::regdictionary, $1) $$;

ALTER TABLE products
    ADD COLUMN search_vector tsvector
        GENERATED ALWAYS AS (
            setweight(to_tsvector('simple', immutable_unaccent(coalesce(name, ''))), 'A') ||
            setweight(to_tsvector('simple', immutable_unaccent(coalesce(description, ''))), 'C')
        ) STORED;

CREATE INDEX idx_products_search_vector ON products USING GIN (search_vector);
