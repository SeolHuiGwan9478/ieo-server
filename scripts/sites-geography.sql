-- 상하차지 좌표를 lat, lng 두 칸에서 PostGIS geography(Point, 4326) 한 칸으로 옮긴다.
-- 이미 lat, lng로 데이터가 쌓인 DB에서, 새 서버를 띄우기 전에 한 번 돌린다. 여러 번 돌려도 된다.
--   psql -d ieo -f scripts/sites-geography.sql
-- 새로 만드는 DB(배포 DB 포함)는 서버가 location 칸을 만들므로, 서버를 한 번 띄운 뒤 마지막 인덱스만 있으면 된다.

CREATE EXTENSION IF NOT EXISTS postgis;

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'sites' AND column_name = 'lat') THEN
		ALTER TABLE sites ADD COLUMN IF NOT EXISTS location geography(Point, 4326);
		UPDATE sites SET location = ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography WHERE location IS NULL;
		ALTER TABLE sites ALTER COLUMN location SET NOT NULL;
		DROP INDEX IF EXISTS idx_sites_lat_lng;
		ALTER TABLE sites DROP COLUMN lat, DROP COLUMN lng;
	END IF;
END $$;

-- 반경 찾기(ST_DWithin)를 빠르게
CREATE INDEX IF NOT EXISTS idx_sites_location ON sites USING GIST (location);
