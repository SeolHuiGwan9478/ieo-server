-- 인수증을 종이 사진 보내기로만 (2026-11-12, POD-009, 011). 수하인 서명·공개 링크를 없앤다
-- 운영(ddl-auto: validate)은 새 서버를 올리기 전에 한 번 돌린다. 로컬도 한 번 돌린다 (update로는 옛 칸을 지우지 못한다)
BEGIN;

-- 예전에는 종이 사진을 올리면 바로 인수가 끝난 것으로 봤다. 그런 인수증은 첫 사진을 올린 때 보낸 것으로 본다
UPDATE delivery_proofs d SET sent_at = p.first_at
FROM (SELECT delivery_proof_id, MIN(created_at) AS first_at FROM proof_photos WHERE type = 'PAPER' GROUP BY delivery_proof_id) p
WHERE p.delivery_proof_id = d.id AND d.sent_at IS NULL;

-- 수하인이 남긴 이상 사항 사진은 지운다 (파일은 저장소에 남는다)
DELETE FROM proof_photos WHERE type = 'ISSUE';
ALTER TABLE proof_photos DROP COLUMN type;

ALTER TABLE delivery_proofs
    DROP COLUMN token,
    DROP COLUMN vehicle_lat,
    DROP COLUMN vehicle_lng,
    DROP COLUMN signer_name,
    DROP COLUMN signature_key,
    DROP COLUMN signed_at,
    DROP COLUMN signer_lat,
    DROP COLUMN signer_lng,
    DROP COLUMN issue_note;

COMMIT;
