#!/usr/bin/env python3
"""로컬 주선사 콘솔 시연 데이터: python scripts/seed-console.py.
별도 [시연] 회사에만 생성한다. 기존 데이터 삭제 없음. 고정 태그로 중복을 피한다.
정상 API로 생성하며, 과거 이력의 시각만 해당 시드 ID 범위에서 SQL로 보정한다.
로컬 시연 인증과 localhost:5432/ieo DB가 필요하다. 인증정보는 출력/저장하지 않는다.
"""
import argparse
import importlib.util
import base64
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
import zlib
from datetime import datetime, timedelta, timezone
ROOT = Path(__file__).resolve().parents[1]
BASE = os.environ.get('IEO_API_BASE', 'http://localhost:8080')
KST = timezone(timedelta(hours=9))
PHONE = '01098765432'
COMPANY = '[시연] 한빛물류'
SITES = [('[시연] 평택 물류센터', '경기 평택시 포승읍 평택항로 156', 36.9631, 126.8337), ('[시연] 이천 물류창고', '경기 이천시 마장면 프리미엄아울렛로 45', 37.2405, 127.365), ('[시연] 대전 산업단지', '대전 대덕구 대화로 106', 36.37, 127.416), ('[시연] 부산 신항 CY', '부산 강서구 신항로 245', 35.078, 128.832), ('[시연] 김포 한강물류', '경기 김포시 고촌읍 아라육로 42', 37.606, 126.772), ('[시연] 대구 성서공단', '대구 달서구 성서공단로 11길 62', 35.837, 128.507)]

def stamp(v):
    return v.astimezone(timezone.utc).isoformat().replace('+00:00', 'Z')

def q(v):
    return "'" + str(v).replace("'", "''") + "'"

def api(method, path, body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path, method=method, headers=headers, data=None if body is None else json.dumps(body).encode())
    try:
        with urllib.request.urlopen(req, timeout=30) as res:
            text = res.read().decode()
            return json.loads(text) if text else None
    except urllib.error.HTTPError as error:
        try:
            message = json.loads(error.read().decode()).get('message', 'API error')
        except (ValueError, UnicodeError):
            message = 'API error'
        raise RuntimeError(f'{method} {path}: HTTP {error.code}: {message}') from None

def load_documents_seed():
    """scripts/seed-documents.py를 모듈로 읽는다 (운영자 계정 만들기, 회사 서류 올리기·승인)"""
    spec = importlib.util.spec_from_file_location('seed_documents', ROOT / 'scripts' / 'seed-documents.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class Database:

    def __init__(self, psql):
        settings = {}
        for line in (ROOT / '.env').read_text(encoding='utf-8-sig').splitlines():
            if '=' in line and (not line.lstrip().startswith('#')):
                k, v = line.split('=', 1)
                settings[k.strip()] = v.strip()
        if settings.get('DB_URL') != 'jdbc:postgresql://localhost:5432/ieo':
            raise RuntimeError('localhost:5432/ieo DB에서만 실행할 수 있습니다.')
        self.env = dict(os.environ, PGPASSWORD=settings.get('DB_PASSWORD', ''), PGCLIENTENCODING='UTF8')
        self.cmd = [psql, '-X', '-q', '-t', '-A', '-w', '-h', 'localhost', '-p', '5432', '-U', settings.get('DB_USERNAME', 'postgres'), '-d', 'ieo', '-v', 'ON_ERROR_STOP=1']

    def run(self, sql):
        res = subprocess.run(self.cmd, input=sql, encoding='utf-8', capture_output=True, env=self.env)
        if res.returncode:
            raise RuntimeError('로컬 시드 SQL 실패: ' + res.stderr.strip())
        return res.stdout.strip()

    def rows(self, sql):
        return json.loads(self.run("SELECT COALESCE(json_agg(r),'[]'::json) FROM (" + sql + ') r;'))

def login(db, phone, role, name, vehicle=None):
    old = db.rows(f'SELECT name,role FROM users WHERE phone={q(phone)}')
    if old and (not old[0]['name'].startswith('[시연]') or old[0]['role'] != role):
        raise RuntimeError('시연 번호가 기존 계정과 겹쳐 변경 없이 중단합니다.')
    last = db.rows(f'SELECT last_sent_at FROM phone_verifications WHERE phone={q(phone)}')
    if last and last[0]['last_sent_at']:
        sent_at = datetime.fromisoformat(last[0]['last_sent_at'])
        remaining = 61 - (datetime.now(timezone.utc) - sent_at).total_seconds()
        if remaining > 0:
            print('로컬 인증 재발급 간격 대기 중', flush=True)
        while remaining > 0:
            time.sleep(min(30, remaining))
            remaining = 61 - (datetime.now(timezone.utc) - sent_at).total_seconds()
    sent = api('POST', '/api/auth/phone/code', {'phone': phone})
    if not sent or not sent.get('demoCode'):
        raise RuntimeError('local 프로필의 시연 인증번호 응답이 필요합니다.')
    device = 'console-seed-' + str(uuid.uuid4())
    verified = api('POST', '/api/auth/phone/verify', {'phone': phone, 'code': sent['demoCode'], 'deviceId': device})
    body = {'phoneToken': verified['phoneToken'], 'deviceId': device}
    if verified['accountExists']:
        return api('POST', '/api/auth/phone/login', body)['accessToken']
    body.update(role=role, name=name, consents=[{'type': t, 'version': '2026-11', 'agreed': True} for t in (['TERMS', 'PRIVACY', 'LOCATION'] if role == 'DRIVER' else ['TERMS', 'PRIVACY'])])
    if vehicle:
        body['vehicle'] = vehicle
    return api('POST', '/api/auth/signup', body)['accessToken']

def signature():
    width, height = (220, 70)
    pixels = bytearray([255] * width * height * 3)
    points = [(15, 48), (40, 17), (31, 54), (70, 25), (57, 47), (102, 28), (94, 51), (147, 20), (137, 47), (202, 39)]
    for (x1, y1), (x2, y2) in zip(points, points[1:]):
        steps = max(abs(x2 - x1), abs(y2 - y1))
        for i in range(steps + 1):
            x, y = (round(x1 + (x2 - x1) * i / steps), round(y1 + (y2 - y1) * i / steps))
            for dx in range(-1, 2):
                for dy in range(-1, 2):
                    p = ((y + dy) * width + x + dx) * 3
                    pixels[p:p + 3] = bytes([35, 52, 76])

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 4294967295)
    raw = b''.join((b'\x00' + pixels[y * width * 3:(y + 1) * width * 3] for y in range(height)))
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
    return 'data:image/png;base64,' + base64.b64encode(png).decode()

class Seed:

    def __init__(self, db):
        self.db = db
        self.now = datetime.now(KST).replace(second=0, microsecond=0)
        self.owner = login(db, PHONE, 'BROKER', '[시연] 박주선')
        companies = db.rows(f'SELECT b.id,b.name FROM brokers b JOIN broker_members m ON m.broker_id=b.id JOIN users u ON u.id=m.user_id WHERE u.phone={q(PHONE)}')
        if companies:
            if companies[0]['name'] != COMPANY:
                raise RuntimeError('시연 계정의 기존 회사는 변경하지 않습니다.')
            self.bid = int(companies[0]['id'])
        else:
            prefix = '811110001'
            total = sum((int(d) * w for d, w in zip(prefix, [1, 3, 7, 1, 3, 7, 1, 3, 5]))) + int(prefix[-1]) * 5 // 10
            self.bid = api('POST', '/api/brokers', {'name': COMPANY, 'businessNumber': prefix + str((10 - total % 10) % 10), 'licenseNumber': '시연-2026-001', 'representativeName': '시연 대표', 'settlementDays': 30, 'defaultFeeRate': 10}, self.owner)['id']
        api('PUT', '/api/brokers/me/plan', {'plan': 'PRO_PLUS'}, self.owner)
        api('PUT', '/api/brokers/me/wait-policy', {'freeMinutes': 60, 'unitMinutes': 30, 'unitAmount': 10000, 'maxAmount': 60000, 'rounding': 'UP'}, self.owner)
        self.verify_company(db)
        self.second_broker(db)
        self.existing = {r['cargo_type']: r['id'] for r in db.rows(f'SELECT id,cargo_type FROM orders WHERE broker_id={self.bid}')}
        self.drivers = []

    def verify_company(self, db):
        """회사 서류(사업자등록증·운송주선사업 허가증)가 승인돼야 오더를 올릴 수 있다 (07 운영자 서류심사 인계 13장).
        seed-documents.py의 함수로 운영자 계정을 만들고 서류 2종을 올려 승인한다"""
        docs = load_documents_seed()
        docs_db = docs.Database(db.cmd[0])
        docs.ensure_admin(docs_db)
        docs.apply_constraints(docs_db)
        admin = docs.login(docs.ADMIN_PHONE)
        if admin is None:
            raise RuntimeError('운영자 로그인에 실패했어요')
        self.documents, self.admin = docs, admin
        if docs.ensure_broker_verified(self.owner, admin):
            print(f'{COMPANY} 회사 서류 2종 승인 (인증된 주선사)')

    def second_broker(self, db):
        """두 번째 주선사 [시연] 두리운송. 서류 심사 대기 시연용이라 서류는 seed-documents.py가 올리고 오더는 없다"""
        phone = '01098765433'
        owner = login(db, phone, 'BROKER', '[시연] 김주선')
        if db.rows(f'SELECT b.id FROM brokers b JOIN broker_members m ON m.broker_id=b.id JOIN users u ON u.id=m.user_id WHERE u.phone={q(phone)}'):
            return
        prefix = '812110002'
        total = sum((int(d) * w for d, w in zip(prefix, [1, 3, 7, 1, 3, 7, 1, 3, 5]))) + int(prefix[-1]) * 5 // 10
        api('POST', '/api/brokers', {'name': '[시연] 두리운송', 'businessNumber': prefix + str((10 - total % 10) % 10), 'licenseNumber': '시연-2026-002', 'representativeName': '김주선', 'settlementDays': 30, 'defaultFeeRate': 10}, owner)
        print('[시연] 두리운송 회사 등록 (서류 심사 대기 시연용)')

    def people(self):
        for i, name in enumerate(['김민수', '이정호', '박성진', '최동욱', '정우석', '한지훈']):
            phone = f'010880000{i + 1:02d}'
            token = login(self.db, phone, 'DRIVER', '[시연] ' + name, {'plateNumber': f'경기80바{8101 + i}', 'tonnage': 'TON_11' if i == 5 else 'TON_5', 'bodyType': 'CARGO', 'heightM': 3.6})
            me = api('GET', '/api/users/me', token=token)
            self.drivers.append({'token': token, 'id': me['id']})
            # 필수 서류 4종이 승인돼야 배차를 요청한다. 차주 서류 심사 시연(대기·반려)은 seed-documents.py --reset으로 다시 만든다
            if self.documents.ensure_driver_verified(token, self.admin, biz_prefix=f'3{i + 1:02d}120040', plate=f'80-{8101 + i}'):
                print(f'  [시연] {name} 필수 서류 4종 승인 (인증된 차주)')
            if not any((r['brokerId'] == self.bid for r in api('GET', '/api/driver-links', token=token))):
                invites = api('GET', '/api/driver-links/invitations', token=token)
                if not any((r['brokerId'] == self.bid for r in invites)):
                    api('POST', '/api/brokers/me/driver-invitations', {'phone': phone}, self.owner)
                    invites = api('GET', '/api/driver-links/invitations', token=token)
                inv = next((r for r in invites if r['brokerId'] == self.bid))
                api('POST', f"/api/driver-links/invitations/{inv['id']}/accept", token=token)
        for i, name in enumerate(['김배차', '이정산']):
            phone = f'010889900{i + 1:02d}'
            token = login(self.db, phone, 'BROKER', '[시연] ' + name)
            memberships = self.db.rows(f'SELECT m.broker_id FROM broker_members m JOIN users u ON u.id=m.user_id WHERE u.phone={q(phone)}')
            if not memberships:
                invites = api('GET', '/api/broker-invitations', token=token)
                if not invites:
                    api('POST', '/api/brokers/me/invitations', {'phone': phone}, self.owner)
                    invites = api('GET', '/api/broker-invitations', token=token)
                inv = next((r for r in invites if r['brokerId'] == self.bid))
                api('POST', f"/api/broker-invitations/{inv['id']}/accept", token=token)
        for endpoint, phone in [('invitations', '01088990003'), ('driver-invitations', '01088000007')]:
            if not any((r.get('phone') == phone for r in api('GET', f'/api/brokers/me/{endpoint}', token=self.owner))):
                api('POST', f'/api/brokers/me/{endpoint}', {'phone': phone}, self.owner)
        saved = api('GET', '/api/brokers/me/saved-addresses', token=self.owner)
        for i, (name, address, lat, lng) in enumerate(SITES):
            if not any((r['address'] == address for r in saved)):
                api('POST', '/api/brokers/me/saved-addresses', {'name': name, 'address': address, 'detailAddress': ['2동 3번 게이트', '상온 창고 A동', '정문 경비실', '컨테이너 4번 게이트', 'B동 하역장', '지게차 대기구역'][i], 'lat': lat, 'lng': lng}, self.owner)
        print('시연 회사·직원 3명·단골 차주 6명·저장 주소 6곳 준비', flush=True)

    def order(self, code, i, **extra):
        cargo = f'[시연-{code}] ' + ['식품 파레트 12개', '자동차 부품', '가전제품 20박스', '건축자재', '생활용품 15파레트', '포장재'][i % 6]
        if cargo in self.existing:
            return self.existing[cargo]

        def site(n):
            name, address, lat, lng = SITES[n % 6]
            return {'site': dict(name=name, address=address, lat=lat, lng=lng)}
        begin = self.now + timedelta(hours=2 + i % 4)
        fare = 240000 + i % 7 * 55000
        body = dict(pickup=site(i), dropoff=site(i + 2), pickupFrom=stamp(begin), pickupUntil=stamp(begin + timedelta(hours=2)), dropoffBy=stamp(begin + timedelta(hours=9)), cargoCategory='GENERAL', cargoType=cargo, weightKg=3500 + i % 4 * 300, tonnage='TON_5', bodyType='CARGO', loadingMethod='FORKLIFT', fare=fare, billedFare=round(fare / 0.9), customerName=['시연 대한식품', '시연 성원산업', '시연 미래유통'][i % 3], paymentType='RECEIPT', expectedPaymentDays=30, consignmentType='DIRECT', confirmMode='SELECT')
        body.update(extra)
        result = api('POST', '/api/broker/orders', body, self.owner)
        self.existing[cargo] = result['id']
        return result['id']

    def trip(self, oid, driver):
        sql = f'SELECT id,status FROM trips WHERE order_id={int(oid)} AND broker_id={self.bid} ORDER BY id DESC LIMIT 1'
        rows = self.db.rows(sql)
        if rows:
            return rows[0]
        req = api('POST', f'/api/orders/{oid}/requests', {}, driver['token'])
        api('POST', f"/api/broker/orders/{oid}/requests/{req['id']}/confirm", token=self.owner)
        return self.db.rows(sql)[0]

    def timeline(self, oid, tid, pickup):
        created = pickup - timedelta(days=1)
        dropoff = pickup + timedelta(hours=5)
        self.db.run(f"BEGIN;\n   UPDATE orders SET created_at={q(stamp(created))},pickup_from={q(stamp(pickup))},pickup_until={q(stamp(pickup + timedelta(hours=2)))},dropoff_by={q(stamp(dropoff + timedelta(hours=1)))} WHERE id={oid} AND broker_id={self.bid};\n   UPDATE trips SET created_at={q(stamp(created + timedelta(hours=1)))},started_at=CASE WHEN started_at IS NOT NULL THEN {q(stamp(pickup - timedelta(minutes=30)))}::timestamptz ELSE NULL END WHERE id={tid} AND broker_id={self.bid};\n   UPDATE trip_stops SET planned_at=CASE WHEN stop_type='PICKUP' THEN {q(stamp(pickup))}::timestamptz ELSE {q(stamp(dropoff))}::timestamptz END WHERE trip_id IN (SELECT id FROM trips WHERE id={tid} AND broker_id={self.bid});\n   UPDATE dispatch_requests SET created_at={q(stamp(created + timedelta(minutes=15)))} WHERE order_id IN (SELECT id FROM orders WHERE id={oid} AND broker_id={self.bid}); COMMIT;")

    def waits(self, driver, tid, kind, arrived, wait, work, depart=True):
        token = driver['token']
        records = api('GET', f'/api/trips/{tid}/waits', token=token)
        record = next((r for r in records if r['stopType'] == kind), None)
        if not record:
            record = api('POST', f'/api/trips/{tid}/stops/{kind}/wait/arrive', {'at': stamp(arrived)}, token)
        if wait is not None and (not record['workStartedAt']):
            record = api('POST', f'/api/trips/{tid}/stops/{kind}/wait/work-start', {'at': stamp(arrived + timedelta(minutes=wait))}, token)
        if depart and (not record['departedAt']):
            api('POST', f'/api/trips/{tid}/stops/{kind}/wait/depart', {'at': stamp(arrived + timedelta(minutes=(wait or 0) + work))}, token)
        api('POST', f'/api/trips/{tid}/stops/{kind}/wait/proof', token=token)

    def settle(self, tid, pickup, i):
        paid = pickup + timedelta(days=[25, 35, 20, 30][i % 4], hours=8)
        paid_sql = q(stamp(paid)) + '::timestamptz' if i % 5 and paid < self.now else 'NULL'
        self.db.run(f'BEGIN;\n   UPDATE trips SET started_at={q(stamp(pickup - timedelta(minutes=30)))} WHERE id={tid} AND broker_id={self.bid};\n   UPDATE ledger_entries SET work_date={q(pickup.date())},expected_payment_date={q((pickup + timedelta(days=30)).date())},paid_at={paid_sql} WHERE trip_id={tid} AND broker_id={self.bid};\n   UPDATE extra_incomes SET paid_at={paid_sql} WHERE ledger_entry_id IN (SELECT id FROM ledger_entries WHERE trip_id={tid} AND broker_id={self.bid}); COMMIT;')
        for record in api('GET', f'/api/trips/{tid}/waits', token=self.drivers[i % 4]['token']):
            amount = int(record.get('chargeableAmount') or 0)
            if amount > 0:
                rid = int(record['id'])
                self.db.run(f'INSERT INTO extra_incomes (ledger_entry_id,wait_record_id,amount,paid_at,created_at,updated_at)\n      SELECT id,{rid},{amount},paid_at,created_at,updated_at FROM ledger_entries WHERE trip_id={tid} AND broker_id={self.bid}\n      ON CONFLICT (wait_record_id) DO UPDATE SET amount=EXCLUDED.amount,paid_at=EXCLUDED.paid_at;')

    def history(self):
        for i, days in enumerate(list(range(1, 11)) + [17 + 10 * j for j in range(14)]):
            driver = self.drivers[i % 4]
            oid = self.order(f'H{i + 1:02d}', i)
            trip = self.trip(oid, driver)
            tid = trip['id']
            pickup = (self.now - timedelta(days=days)).replace(hour=7, minute=0)
            self.timeline(oid, tid, pickup)
            if trip['status'] == 'COMPLETED':
                self.settle(tid, pickup, i)
                continue
            if trip['status'] == 'SCHEDULED':
                api('POST', f'/api/trips/{tid}/start', token=driver['token'])
            self.waits(driver, tid, 'PICKUP', pickup, [45, 90, 150, 210][i % 4], 30)
            self.waits(driver, tid, 'DROPOFF', pickup + timedelta(hours=5), 20 + i % 3 * 10, 25)
            if i < 6:
                proof = api('POST', f'/api/trips/{tid}/delivery-proof', {}, driver['token'])
                if not proof['signedAt']:
                    api('POST', f"/api/public/delivery-proofs/{proof['token']}/sign", {'signerName': '시연 수하인', 'signature': signature(), 'issueNote': '시연용 인수 확인'})
                self.db.run(f'UPDATE delivery_proofs SET created_at={q(stamp(pickup + timedelta(hours=5)))},signed_at={q(stamp(pickup + timedelta(hours=5, minutes=50 + i % 3 * 10)))} WHERE trip_id IN (SELECT id FROM trips WHERE id={tid} AND broker_id={self.bid});')
            self.settle(tid, pickup, i)
            if (i + 1) % 4 == 0:
                print(f'과거 완료 운행 {i + 1}/24건 준비', flush=True)

    def current(self):
        for i in range(3):
            oid = self.order(f'C{i + 1}', i + 10)
            trip = self.trip(oid, self.drivers[(i + 1) % 4])
            tid = trip['id']
            if trip['status'] != 'CANCELLED':
                api('POST', f'/api/broker/orders/{oid}/cancel', {'reason': ['SHIPPER_CANCELED', 'VEHICLE_PROBLEM', 'SCHEDULE_CONFLICT'][i], 'memo': '시연 취소 이력', 'reopen': False}, self.owner)
                at = self.now - timedelta(days=[2, 12, 35][i])
                self.timeline(oid, tid, at)
                self.db.run(f'UPDATE trips SET canceled_at={q(stamp(at))} WHERE id={tid} AND broker_id={self.bid}; UPDATE cancellations SET created_at={q(stamp(at))} WHERE trip_id IN (SELECT id FROM trips WHERE id={tid} AND broker_id={self.bid});')
        for i in range(4):
            driver = self.drivers[i]
            oid = self.order(f'A{i + 1}', i + 20)
            trip = self.trip(oid, driver)
            tid = trip['id']
            if trip['status'] != 'SCHEDULED':
                continue
            pickup = self.now - timedelta(minutes=[30, 150, 240, 420][i])
            self.timeline(oid, tid, pickup)
            api('POST', f'/api/trips/{tid}/start', token=driver['token'])
            if i == 1:
                self.waits(driver, tid, 'PICKUP', self.now - timedelta(minutes=140), None, 0, False)
            if i >= 2:
                self.waits(driver, tid, 'PICKUP', pickup, 80, 30)
            if i == 3:
                self.waits(driver, tid, 'DROPOFF', self.now - timedelta(minutes=100), 80, 0, False)
                api('POST', f'/api/trips/{tid}/delivery-proof', {}, driver['token'])
            site = SITES[(i + (2 if i == 3 else 0)) % 6]
            api('POST', f'/api/trips/{tid}/locations', {'points': [{'recordedAt': stamp(datetime.now(KST)), 'lat': site[2], 'lng': site[3], 'remainMeters': [28000, 0, 65000, 0][i], 'remainSeconds': [2400, 0, 4200, 0][i]}]}, driver['token'])
            self.timeline(oid, tid, pickup)
        for i in range(3):
            self.trip(self.order(f'S{i + 1}', i + 30), self.drivers[[4, 5, 0][i]])
        for i in range(6):
            oid = self.order(f'O{i + 1}', i + 40, **{'fare': 90, 'billedFare': 100} if i == 5 else {})
            if i < 2:
                for driver in self.drivers[4:6]:
                    if not self.db.rows(f"SELECT id FROM dispatch_requests WHERE order_id={oid} AND driver_id={int(driver['id'])}"):
                        api('POST', f'/api/orders/{oid}/requests', {}, driver['token'])
        for i in range(2):
            oid = self.order(f'X{i + 1}', i + 50)
            if api('GET', f'/api/broker/orders/{oid}', token=self.owner)['status'] == 'OPEN':
                api('POST', f'/api/broker/orders/{oid}/close', token=self.owner)
        print('진행 4건·예약 3건·취소 3건·모집 6건·마감 2건 준비', flush=True)

    def verify(self):
        first = self.db.rows(f"SELECT t.id,t.driver_id FROM trips t JOIN orders o ON o.id=t.order_id WHERE t.broker_id={self.bid} AND o.cargo_type LIKE '[시연-H%' ORDER BY t.id LIMIT 1")[0]
        driver = next((d for d in self.drivers if d['id'] == first['driver_id']))
        record = api('GET', f"/api/trips/{first['id']}/waits", token=driver['token'])[0]
        api('PATCH', f"/api/trips/{first['id']}/stops/{record['stopType']}/wait", {k: record[k] for k in ['arrivedAt', 'workStartedAt', 'departedAt']}, driver['token'])
        checks = {'orders': api('GET', '/api/broker/orders?size=100', token=self.owner)['totalCount'], 'completed': api('GET', '/api/brokers/me/dashboard?unit=MONTH', token=self.owner)['total']['completedCount']}
        for name in ['drivers', 'members', 'saved-addresses']:
            checks[name] = len(api('GET', '/api/brokers/me/' + name, token=self.owner))
        for report, key in [('routes', 'routes'), ('sites', 'sites'), ('drivers', 'drivers')]:
            checks['report-' + report] = len(api('GET', '/api/brokers/me/reports/' + report, token=self.owner)[key])
        for tab in ['IN_PROGRESS', 'SCHEDULED', 'FINISHED']:
            checks[tab] = api('GET', f'/api/brokers/me/trips?tab={tab}&size=100', token=self.owner)['totalCount']
        assert checks['orders'] >= 42 and checks['completed'] >= 24 and (checks['IN_PROGRESS'] >= 4)
        phones = [PHONE] + [f'010880000{i + 1:02d}' for i in range(6)] + [f'010889900{i + 1:02d}' for i in range(2)]
        self.db.run('UPDATE phone_verifications SET send_count=0,last_sent_at=NULL WHERE phone IN (' + ','.join((q(p) for p in phones)) + ") AND phone IN (SELECT phone FROM users WHERE name LIKE '[시연] %');")
        print('검증:', json.dumps(checks, ensure_ascii=False), flush=True)
        print('시연 로그인:', PHONE, '/ 회사:', COMPANY, flush=True)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--psql', default=shutil.which('psql') or 'C:/Program Files/PostgreSQL/18/bin/psql.exe')
    args = parser.parse_args()
    seed = Seed(Database(args.psql))
    seed.people()
    seed.history()
    seed.current()
    seed.verify()
if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, AssertionError) as error:
        sys.exit(str(error) or '시연 데이터 검증 실패')
