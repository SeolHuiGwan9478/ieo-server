-- 화물 종류 추가: 중량물, 장척물, 건자재, 농수산물, 폐기물 (2026-11-11, DSP-001, REC-004)
-- Hibernate가 만든 enum CHECK 제약은 ddl-auto update로 바뀌지 않는다. 운영·로컬 모두 새 서버를 올리기 전에 한 번 돌린다
BEGIN;
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_cargo_category_check;
ALTER TABLE orders ADD CONSTRAINT orders_cargo_category_check CHECK (cargo_category IN
    ('GENERAL', 'COLD', 'MOVING', 'HAZARDOUS', 'HEAVY', 'LONG', 'CONSTRUCTION', 'FARM', 'WASTE'));
ALTER TABLE order_templates DROP CONSTRAINT IF EXISTS order_templates_cargo_category_check;
ALTER TABLE order_templates ADD CONSTRAINT order_templates_cargo_category_check CHECK (cargo_category IN
    ('GENERAL', 'COLD', 'MOVING', 'HAZARDOUS', 'HEAVY', 'LONG', 'CONSTRUCTION', 'FARM', 'WASTE'));
ALTER TABLE recommendation_excluded_cargo DROP CONSTRAINT IF EXISTS recommendation_excluded_cargo_cargo_category_check;
ALTER TABLE recommendation_excluded_cargo ADD CONSTRAINT recommendation_excluded_cargo_cargo_category_check CHECK (cargo_category IN
    ('GENERAL', 'COLD', 'MOVING', 'HAZARDOUS', 'HEAVY', 'LONG', 'CONSTRUCTION', 'FARM', 'WASTE'));
COMMIT;
