-- 시연 차주(010-8800-0001~0006)에게 "주선사에 위치·운행 정보 공유" 동의(BROKER_SHARE)를 넣는다.
-- 이 동의가 없으면 주선사 콘솔의 운행 목록·상세에서 위치·ETA·진행률이 전부 "위치 비공개"로 나온다 (BRK-002, ACC-006).
-- seed-console.py는 2026-11-10부터 차주 가입 때 이 동의를 같이 넣으므로, 그 전에 만든 DB에만 한 번 돌리면 된다.
--
--   psql -d ieo -f scripts/seed-driver-broker-share.sql
insert into user_consents (user_id, type, version, agreed, created_at)
select u.id, 'BROKER_SHARE', '2026-11', true, now()
from users u
where u.role = 'DRIVER'
  and u.phone like '0108800000%'
  and not exists (select 1 from user_consents c where c.user_id = u.id and c.type = 'BROKER_SHARE');

select u.phone, c.agreed, c.created_at
from user_consents c join users u on u.id = c.user_id
where c.type = 'BROKER_SHARE'
order by u.phone;
