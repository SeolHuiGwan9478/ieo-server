-- seed-demo.py가 넣은 시연 데이터를 지운다. 기본은 무엇이 지워질지 세기만 하고 되돌린다.
--   psql "<접속 정보>" -f scripts/clear-demo.sql              -- 미리 보기 (아무것도 안 지움)
--   psql "<접속 정보>" -v apply=1 -f scripts/clear-demo.sql    -- 실제로 지우기
-- 시연 차주·주선사 계정에서 외래키를 따라 내려가며 지운다. 실제 기사가 시연 오더에 요청·운행한 기록도 함께 지워지니
-- 미리 보기 숫자를 먼저 확인한다. [시연] 상하차지는 다른 데이터가 쓰지 않을 때만 지운다.
\set ON_ERROR_STOP on
\if :{?apply}
\else
\set apply 0
\endif
BEGIN;

CREATE TEMP TABLE purge_log(tbl text, n bigint) ON COMMIT DROP;

CREATE FUNCTION pg_temp.purge(tbl regclass, keys text[]) RETURNS void LANGUAGE plpgsql AS $$
DECLARE
	pk text;
	fk record;
	child_keys text[];
	n bigint;
BEGIN
	IF keys IS NULL OR cardinality(keys) = 0 THEN RETURN; END IF;
	SELECT a.attname INTO pk FROM pg_index i JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = i.indkey[0]
	WHERE i.indrelid = tbl AND i.indisprimary;
	FOR fk IN
		SELECT c.conrelid::regclass AS child, ca.attname AS col, pa.attname AS refcol
		FROM pg_constraint c
		JOIN pg_attribute ca ON ca.attrelid = c.conrelid AND ca.attnum = c.conkey[1]
		JOIN pg_attribute pa ON pa.attrelid = c.confrelid AND pa.attnum = c.confkey[1]
		WHERE c.contype = 'f' AND c.confrelid = tbl
	LOOP
		IF fk.child = tbl THEN CONTINUE; END IF;
		EXECUTE format('SELECT array_agg(%I::text) FROM %s WHERE %I::text IN (SELECT %I::text FROM %s WHERE %I::text = ANY($1))',
			(SELECT a.attname FROM pg_index i JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = i.indkey[0]
			 WHERE i.indrelid = fk.child AND i.indisprimary), fk.child, fk.col, fk.refcol, tbl, pk)
			INTO child_keys USING keys;
		PERFORM pg_temp.purge(fk.child, child_keys);
	END LOOP;
	EXECUTE format('DELETE FROM %s WHERE %I::text = ANY($1)', tbl, pk) USING keys;
	GET DIAGNOSTICS n = ROW_COUNT;
	INSERT INTO purge_log VALUES (tbl::text, n);
END $$;

SELECT pg_temp.purge('brokers', (SELECT array_agg(DISTINCT m.broker_id::text) FROM broker_members m JOIN users u ON u.id = m.user_id
	WHERE u.phone IN ('01000000001', '01000000002')));
SELECT pg_temp.purge('users', (SELECT array_agg(id::text) FROM users WHERE phone IN ('01000000001', '01000000002')));
DELETE FROM phone_verifications WHERE phone IN ('01000000001', '01000000002');

-- 다른 데이터가 아직 쓰는 [시연] 상하차지는 남긴다
DO $$
DECLARE s bigint; n bigint := 0;
BEGIN
	FOR s IN SELECT id FROM sites WHERE name LIKE '[시연]%' LOOP
		BEGIN
			DELETE FROM sites WHERE id = s;
			n := n + 1;
		EXCEPTION WHEN foreign_key_violation THEN
			RAISE NOTICE '상하차지 %는 다른 데이터가 써서 남김', s;
		END;
	END LOOP;
	INSERT INTO purge_log VALUES ('sites ([시연])', n);
END $$;

SELECT tbl AS "지울 표", sum(n) AS "줄 수" FROM purge_log WHERE n > 0 GROUP BY tbl ORDER BY tbl;

\if :apply
COMMIT;
\echo 지웠어요.
\else
ROLLBACK;
\echo 미리 보기만 했어요. 지우려면 -v apply=1 을 붙여 다시 실행하세요.
\endif
