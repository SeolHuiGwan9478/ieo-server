#!/usr/bin/env python3
"""운영자 서류 심사 시연 데이터: python scripts/seed-documents.py [--psql psql경로] [--reset]

seed-console.py가 만든 [시연] 차주 6명과 주선사 두 곳([시연] 한빛물류·두리운송)에 서류 사진(가짜 PNG)을 올리고, 운영자 계정으로 일부를 승인·반려해 둔다.
- 한빛물류는 서류 2종을 승인해 인증된 주선사로 둔다 (승인 전에는 오더를 못 올린다). 두리운송은 심사 대기로 둔다
- 운영자 계정 010-0000-0099 ([시연] 운영자)가 없으면 DB에 직접 만든다. 가입으로는 운영자를 만들 수 없다
- 이미 서류를 낸 차주는 건너뛴다. [시연] 차주가 없으면 먼저 seed-console.py를 실행한다
- --reset: [시연] 차주의 서류·이력과 파일을 지우고 처음부터 다시 올린다 (사업자 정보 없이 올린 예전 서류를 바꿀 때)
- 사업자등록증은 사업자번호·개업일을 같이 보낸다. 서버가 NTS_STUB=true면 번호 아홉째 자리로 국세청 결과가 정해진다 (0~6 계속사업자, 7 휴업, 8 폐업, 9 불일치)
- 서버 주소는 IEO_API_BASE (기본 http://localhost:8080). local 프로필의 시연 인증번호(demoCode) 응답이 필요하다
- DB는 .env의 DB_URL이 localhost:5432/ieo일 때만 건드린다 (seed-console.py와 같은 규칙)

운영자 페이지: 콘솔의 /admin/login 에서 010-0000-0099 로 로그인한다.
"""
import json
import os
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
import zlib
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BASE = os.environ.get('IEO_API_BASE', 'http://localhost:8080')
ADMIN_PHONE = '01000000099'
ADMIN_NAME = '[시연] 운영자'
DRIVERS = ['김민수', '이정호', '박성진', '최동욱', '정우석', '한지훈']
DOCS = {
    'BUSINESS_REGISTRATION': ('BUSINESS REGISTRATION', (37, 99, 235)),
    'VEHICLE_REGISTRATION': ('VEHICLE REGISTRATION', (22, 163, 74)),
    'DRIVER_LICENSE': ('DRIVER LICENSE', (217, 119, 6)),
    'FREIGHT_QUALIFICATION': ('FREIGHT QUALIFICATION', (124, 58, 237)),
    'BROKER_LICENSE': ('BROKER LICENSE', (8, 145, 178)),
}
DRIVER_DOCS = ['BUSINESS_REGISTRATION', 'VEHICLE_REGISTRATION', 'DRIVER_LICENSE', 'FREIGHT_QUALIFICATION']
# seed-console.py가 만든 주선사의 대표 담당자. 사업자번호 아홉째 자리가 1·2라 스텁 국세청이 계속사업자로 답한다
BROKER_PHONE = '01098765432'    # [시연] 한빛물류 (811-11-00013). 서류 2종 승인 → 인증된 주선사
BROKER2_PHONE = '01098765433'   # [시연] 두리운송 (812-11-00020). 서류 2종 심사 대기
BROKER_OPENED = '2018-05-14'
# 사업자등록증 입력값 (차주 index: 사업자번호 앞 9자리, 개업일). 아홉째 자리가 스텁 국세청의 결과를 정한다. 검증 숫자는 business_number()가 붙인다
BUSINESS = {
    0: ('301120040', '2017-03-02'),  # 김민수: 계속사업자
    1: ('302120041', '2019-11-15'),  # 이정호: 계속사업자
    2: ('303120042', '2015-06-01'),  # 박성진: 계속사업자
    3: ('304120043', '2021-01-20'),  # 최동욱: 계속사업자
    4: ('305120044', '2016-09-09'),  # 정우석: 계속사업자
    5: ('306120048', '2014-02-11'),  # 한지훈: 폐업자 (눈여겨볼 건)
}


def business_number(prefix9):
    """사업자등록번호 검증 숫자를 붙인다 (가중치 1,3,7,1,3,7,1,3,5). 서버가 같은 규칙으로 검사한다"""
    weights = [1, 3, 7, 1, 3, 7, 1, 3, 5]
    total = sum(int(d) * w for d, w in zip(prefix9, weights)) + int(prefix9[8]) * 5 // 10
    digits = prefix9 + str((10 - total % 10) % 10)
    return f'{digits[:3]}-{digits[3:5]}-{digits[5:]}'


# ---------- 서버 호출 ----------

def api(method, path, body=None, token=None, data=None, content_type='application/json'):
    headers = {'Content-Type': content_type}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    payload = data if data is not None else (None if body is None else json.dumps(body).encode())
    req = urllib.request.Request(BASE + path, method=method, headers=headers, data=payload)
    try:
        with urllib.request.urlopen(req, timeout=30) as res:
            text = res.read().decode()
            return json.loads(text) if text else None
    except urllib.error.HTTPError as error:
        try:
            parsed = json.loads(error.read().decode())
        except (ValueError, UnicodeError):
            parsed = {}
        raise ApiError(error.code, parsed.get('code', ''), parsed.get('message', 'API error'), method, path) from None


class ApiError(RuntimeError):
    def __init__(self, status, code, message, method, path):
        super().__init__(f'{method} {path}: HTTP {status} {code}: {message}')
        self.status = status
        self.code = code


def send_code(phone):
    for _ in range(3):
        try:
            return api('POST', '/api/auth/phone/code', {'phone': phone})
        except ApiError as error:
            if error.status != 429 or error.code == 'AUTH_429_DAILY':
                # 하루 한도(5번)는 기다려도 안 풀린다. scripts/reset-sms-limit.sh 로 되돌린 뒤 다시 돌린다
                raise
            print(f'  {phone}: 인증번호는 1분에 한 번만 받을 수 있어 1분 기다려요', flush=True)
            time.sleep(61)
    raise RuntimeError('인증번호를 받지 못했어요')


def login(phone):
    """이미 가입된 번호로 로그인한다. 가입은 하지 않는다"""
    sent = send_code(phone)
    if not sent or not sent.get('demoCode'):
        raise RuntimeError('local 프로필의 시연 인증번호 응답이 필요합니다.')
    device = 'document-seed-' + str(uuid.uuid4())
    verified = api('POST', '/api/auth/phone/verify', {'phone': phone, 'code': sent['demoCode'], 'deviceId': device})
    if not verified['accountExists']:
        return None
    return api('POST', '/api/auth/phone/login', {'phoneToken': verified['phoneToken'], 'deviceId': device})['accessToken']


def upload(token, doc_type, png_bytes, fields=None, path='/api/drivers/me/documents'):
    """multipart로 서류를 올린다. fields는 사업자등록증의 businessNumber·openedOn 같은 글자 항목. path는 차주/주선사 API"""
    boundary = 'ieo' + uuid.uuid4().hex
    parts = {'type': doc_type, **(fields or {})}
    head = ''.join(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n' for name, value in parts.items())
    head += (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{doc_type.lower()}.png"\r\n'
             f'Content-Type: image/png\r\n\r\n')
    body = head.encode() + png_bytes + f'\r\n--{boundary}--\r\n'.encode()
    return api('POST', path, token=token, data=body, content_type=f'multipart/form-data; boundary={boundary}')


# ---------- DB (운영자 계정 만들기) ----------

class Database:
    def __init__(self, psql):
        settings = {}
        for line in (ROOT / '.env').read_text(encoding='utf-8-sig').splitlines():
            if '=' in line and not line.lstrip().startswith('#'):
                key, value = line.split('=', 1)
                settings[key.strip()] = value.strip()
        if settings.get('DB_URL') != 'jdbc:postgresql://localhost:5432/ieo':
            raise RuntimeError('localhost:5432/ieo DB에서만 실행할 수 있습니다.')
        # LC_ALL=C: psql 오류 문구를 영어로 받는다 (Windows는 한글 문구를 콘솔 코드페이지로 찍어 읽기 어렵다)
        self.settings = settings
        self.env = dict(os.environ, PGPASSWORD=settings.get('DB_PASSWORD', ''), PGCLIENTENCODING='UTF8', LC_ALL='C')
        self.cmd = [psql, '-X', '-q', '-t', '-A', '-w', '-h', 'localhost', '-p', '5432', '-U', settings.get('DB_USERNAME', 'postgres'),
                    '-d', 'ieo', '-v', 'ON_ERROR_STOP=1']

    def run(self, sql):
        # Windows psql은 오류 문구를 콘솔 코드페이지로 찍으므로 UTF-8로 못 읽는 글자는 바꿔서 읽는다
        result = subprocess.run(self.cmd, input=sql, encoding='utf-8', errors='replace', capture_output=True, env=self.env)
        if result.returncode:
            raise RuntimeError('SQL 실패: ' + result.stderr.strip())
        return result.stdout.strip()


def ensure_admin(db):
    found = db.run(f"SELECT role FROM users WHERE phone = '{ADMIN_PHONE}';")
    if found == 'ADMIN':
        print(f'운영자 계정 있음: {ADMIN_PHONE}')
        return
    if found:
        raise RuntimeError(f'{ADMIN_PHONE} 번호가 운영자가 아닌 계정({found})으로 이미 있어요. 변경 없이 중단합니다.')
    # 처음 만들어진 users 테이블은 role CHECK 제약에 ADMIN이 없다. 제약을 고치는 SQL을 먼저 적용한다
    db.run((ROOT / 'scripts' / 'users-role-admin.sql').read_text(encoding='utf-8'))
    # 한글 이름은 psql 클라이언트 인코딩과 상관없이 들어가도록 UTF-8 바이트(hex)로 넘긴다
    name_hex = ADMIN_NAME.encode('utf-8').hex()
    db.run(f"INSERT INTO users (phone, name, role, status, created_at, updated_at) "
           f"VALUES ('{ADMIN_PHONE}', convert_from(decode('{name_hex}', 'hex'), 'UTF8'), 'ADMIN', 'ACTIVE', now(), now());")
    print(f'운영자 계정 생성: {ADMIN_PHONE} ({ADMIN_NAME})')


def apply_constraints(db):
    """반려 사유·이력 종류에 값이 늘었다. Hibernate가 만든 CHECK 제약은 ddl-auto: update가 안 고치므로 여기서 적용한다 (몇 번 실행해도 된다)"""
    db.run((ROOT / 'scripts' / 'driver-documents-business-check.sql').read_text(encoding='utf-8'))


def reset_documents(db):
    """[시연] 차주와 [시연] 한빛물류의 서류·이력을 지우고 저장 폴더의 파일도 지운다. 다른 차주·회사는 건드리지 않는다"""
    where = "driver_id IN (SELECT id FROM users WHERE phone LIKE '010880000__')"
    phones = ', '.join(f"'{p}'" for p in [BROKER_PHONE, BROKER2_PHONE, *EXTRA_PHONES])
    broker_where = (f"broker_id IN (SELECT m.broker_id FROM broker_members m JOIN users u ON u.id = m.user_id "
                    f"WHERE u.phone IN ({phones}))")
    keys = db.run(f"SELECT file_key FROM driver_documents WHERE {where};").split()
    keys += db.run(f"SELECT file_key FROM broker_documents WHERE {broker_where};").split()
    db.run(f"DELETE FROM driver_document_events WHERE {where}; DELETE FROM driver_documents WHERE {where};")
    db.run(f"DELETE FROM broker_document_events WHERE {broker_where}; DELETE FROM broker_documents WHERE {broker_where};")
    storage = Path(db.settings.get('IEO_STORAGE_DIR', 'data/files'))
    if not storage.is_absolute():
        storage = ROOT / storage
    removed = 0
    for key in keys:
        path = storage / key
        if path.is_file():
            path.unlink()
            removed += 1
    print(f'[시연] 차주·주선사 서류 초기화: {len(keys)}건 삭제 (파일 {removed}개)')


# ---------- 가짜 서류 사진 (순수 파이썬 PNG) ----------

FONT = {
    'A': ['.###.', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'],
    'B': ['####.', '#...#', '#...#', '####.', '#...#', '#...#', '####.'],
    'C': ['.####', '#....', '#....', '#....', '#....', '#....', '.####'],
    'D': ['####.', '#...#', '#...#', '#...#', '#...#', '#...#', '####.'],
    'E': ['#####', '#....', '#....', '####.', '#....', '#....', '#####'],
    'F': ['#####', '#....', '#....', '####.', '#....', '#....', '#....'],
    'G': ['.####', '#....', '#....', '#.###', '#...#', '#...#', '.####'],
    'H': ['#...#', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'],
    'I': ['#####', '..#..', '..#..', '..#..', '..#..', '..#..', '#####'],
    'J': ['#####', '...#.', '...#.', '...#.', '...#.', '#..#.', '.##..'],
    'K': ['#...#', '#..#.', '#.#..', '##...', '#.#..', '#..#.', '#...#'],
    'L': ['#....', '#....', '#....', '#....', '#....', '#....', '#####'],
    'M': ['#...#', '##.##', '#.#.#', '#.#.#', '#...#', '#...#', '#...#'],
    'N': ['#...#', '##..#', '#.#.#', '#..##', '#...#', '#...#', '#...#'],
    'O': ['.###.', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'],
    'P': ['####.', '#...#', '#...#', '####.', '#....', '#....', '#....'],
    'Q': ['.###.', '#...#', '#...#', '#...#', '#.#.#', '#..#.', '.##.#'],
    'R': ['####.', '#...#', '#...#', '####.', '#.#..', '#..#.', '#...#'],
    'S': ['.####', '#....', '#....', '.###.', '....#', '....#', '####.'],
    'T': ['#####', '..#..', '..#..', '..#..', '..#..', '..#..', '..#..'],
    'U': ['#...#', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'],
    'V': ['#...#', '#...#', '#...#', '#...#', '#...#', '.#.#.', '..#..'],
    'W': ['#...#', '#...#', '#...#', '#.#.#', '#.#.#', '##.##', '#...#'],
    'X': ['#...#', '#...#', '.#.#.', '..#..', '.#.#.', '#...#', '#...#'],
    'Y': ['#...#', '#...#', '.#.#.', '..#..', '..#..', '..#..', '..#..'],
    'Z': ['#####', '....#', '...#.', '..#..', '.#...', '#....', '#####'],
    '0': ['.###.', '#...#', '#..##', '#.#.#', '##..#', '#...#', '.###.'],
    '1': ['..#..', '.##..', '..#..', '..#..', '..#..', '..#..', '.###.'],
    '2': ['.###.', '#...#', '....#', '...#.', '..#..', '.#...', '#####'],
    '3': ['####.', '....#', '....#', '.###.', '....#', '....#', '####.'],
    '4': ['...#.', '..##.', '.#.#.', '#..#.', '#####', '...#.', '...#.'],
    '5': ['#####', '#....', '####.', '....#', '....#', '#...#', '.###.'],
    '6': ['.###.', '#....', '#....', '####.', '#...#', '#...#', '.###.'],
    '7': ['#####', '....#', '...#.', '..#..', '.#...', '.#...', '.#...'],
    '8': ['.###.', '#...#', '#...#', '.###.', '#...#', '#...#', '.###.'],
    '9': ['.###.', '#...#', '#...#', '.####', '....#', '....#', '.###.'],
    '-': ['.....', '.....', '.....', '#####', '.....', '.....', '.....'],
    '.': ['.....', '.....', '.....', '.....', '.....', '.##..', '.##..'],
    ':': ['.....', '.##..', '.##..', '.....', '.##..', '.##..', '.....'],
    '/': ['....#', '...#.', '...#.', '..#..', '.#...', '.#...', '#....'],
    ' ': ['.....'] * 7,
}


class Canvas:
    def __init__(self, width, height, background=(255, 255, 255)):
        self.width, self.height = width, height
        self.rows = [bytearray(bytes(background) * width) for _ in range(height)]

    def rect(self, x, y, width, height, color):
        pixel = bytes(color)
        x0, x1 = max(0, x), min(self.width, x + width)
        if x1 <= x0:
            return
        for yy in range(max(0, y), min(self.height, y + height)):
            self.rows[yy][x0 * 3:x1 * 3] = pixel * (x1 - x0)

    def text(self, x, y, string, color, scale=3):
        cursor = x
        for char in string.upper():
            for gy, line in enumerate(FONT.get(char, FONT[' '])):
                for gx, cell in enumerate(line):
                    if cell == '#':
                        self.rect(cursor + gx * scale, y + gy * scale, scale, scale, color)
            cursor += 6 * scale

    def png(self):
        raw = b''.join(b'\x00' + bytes(row) for row in self.rows)

        def chunk(tag, data):
            return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

        header = struct.pack('>IIBBBBB', self.width, self.height, 8, 2, 0, 0, 0)
        return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', header) + chunk(b'IDAT', zlib.compress(raw, 6)) + chunk(b'IEND', b'')


def document_png(doc_type, lines, attempt):
    """서류처럼 보이는 가짜 사진. 종류별 색 띠, 제목, 몇 줄의 항목(lines: 차주 번호·차량 번호, 사업자번호·개업일, 상호·허가 번호), SAMPLE 표시"""
    title, color = DOCS[doc_type]
    canvas = Canvas(640, 880, (226, 232, 240))
    canvas.rect(40, 40, 560, 800, (255, 255, 255))
    canvas.rect(40, 40, 560, 90, color)
    canvas.text(64, 70, title, (255, 255, 255), 4)
    canvas.text(64, 160, 'IEO DEMO - SAMPLE DOCUMENT', (100, 116, 139), 2)
    rows = [*lines, f'ISSUED: {date.today().isoformat()}', f'COPY: {attempt}']
    for i, line in enumerate(rows):
        canvas.text(64, 220 + i * 52, line, (30, 41, 59), 3)
    for i in range(7):
        canvas.rect(64, 548 + i * 34, 380 + (i * 53) % 130, 14, (203, 213, 225))
    canvas.rect(40, 790, 560, 50, (241, 245, 249))
    canvas.text(64, 804, 'NOT A REAL DOCUMENT', (148, 163, 184), 3)
    return canvas.png()


# ---------- 시나리오 ----------

def driver_token(index):
    phone = f'010880000{index + 1:02d}'
    token = login(phone)
    if token is None:
        print(f'  [시연] {DRIVERS[index]} ({phone}) 계정이 없어요. 먼저 seed-console.py를 실행해 주세요. 건너뜀')
    return token


def has_documents(token):
    return any(item['latest'] for item in api('GET', '/api/drivers/me/documents', token=token)['documents'])


def submit(token, index, doc_type, attempt=1):
    plate_digits = 8101 + index
    fields, lines = None, [f'DRIVER NO: {index + 1}', f'PLATE: 80-{plate_digits}']
    if doc_type == 'BUSINESS_REGISTRATION':
        number, opened = business_number(BUSINESS[index][0]), BUSINESS[index][1]
        fields = {'businessNumber': number, 'openedOn': opened}
        lines += [f'BIZ NO: {number}', f'OPENED: {opened}']
    return upload(token, doc_type, document_png(doc_type, lines, attempt), fields)['id']


# ---------- 주선사 서류 (설정 > 회사 서류) ----------

def has_broker_documents(token):
    return any(item['latest'] for item in api('GET', '/api/brokers/me/documents', token=token)['documents'])


def submit_broker(token, doc_type, attempt=1, company='HANBIT LOGISTICS', biz='811-11-00013', license='2026-001', opened=BROKER_OPENED):
    """사업자번호·대표자는 회사 등록값을 쓰므로 개업일만 보낸다. 나머지 인자는 가짜 사진에 적히는 글자"""
    fields, lines = None, [f'COMPANY: {company}', f'LICENSE NO: {license}']
    if doc_type == 'BUSINESS_REGISTRATION':
        fields = {'openedOn': opened}
        lines = [f'COMPANY: {company}', f'BIZ NO: {biz}', f'OPENED: {opened}']
    return upload(token, doc_type, document_png(doc_type, lines, attempt), fields, path='/api/brokers/me/documents')['id']


def approve_broker(admin, document_id):
    api('POST', f'/api/admin/broker-documents/{document_id}/approve', token=admin)


def ensure_broker_verified(broker_token, admin_token, **company):
    """회사 서류 2종이 모두 승인되게 한다 (승인 전에는 오더를 못 올린다). 없으면 올리고, 심사 대기면 승인한다.
    몇 번 불러도 되고, 바뀐 게 있으면 True. seed-console.py와 seed-demo.py가 오더를 만들기 전에 부른다"""
    status = api('GET', '/api/brokers/me/documents', token=broker_token)
    if status['verified']:
        return False
    for item in status['documents']:
        latest = item['latest']
        if latest and latest['status'] == 'APPROVED':
            continue
        document_id = latest['id'] if latest and latest['status'] == 'PENDING' else submit_broker(broker_token, item['type'], **company)
        approve_broker(admin_token, document_id)
    return True


def ensure_driver_verified(driver_token, admin_token, biz_prefix='309120040', opened='2019-03-01', plate=''):
    """필수 서류 4종이 모두 승인되게 한다 (승인 전에는 배차를 요청할 수 없다). 없으면 올리고, 심사 대기면 승인한다.
    몇 번 불러도 되고, 바뀐 게 있으면 True. seed-console.py와 seed-demo.py가 배차를 요청하기 전에 부른다.
    biz_prefix의 아홉째 자리가 스텁 국세청 결과를 정한다(0이면 계속사업자). 운영 국세청에서는 조회 결과만 다르고 승인은 된다"""
    status = api('GET', '/api/drivers/me/documents', token=driver_token)
    if status['verified']:
        return False
    for item in status['documents']:
        if not item['required']:
            continue
        latest = item['latest']
        if latest and latest['status'] == 'APPROVED':
            continue
        if latest and latest['status'] == 'PENDING':
            approve(admin_token, latest['id'])
            continue
        fields, lines = None, [f'PLATE: {plate}']
        if item['type'] == 'BUSINESS_REGISTRATION':
            number = business_number(biz_prefix)
            fields = {'businessNumber': number, 'openedOn': opened}
            lines += [f'BIZ NO: {number}', f'OPENED: {opened}']
        approve(admin_token, upload(driver_token, item['type'], document_png(item['type'], lines, 1), fields)['id'])
    return True


def reject_broker(admin, document_id, reason, note=None):
    api('POST', f'/api/admin/broker-documents/{document_id}/reject', {'reasonCode': reason, 'note': note}, token=admin)


# 운영자 심사 화면을 채우는 추가 주선사 (2026-11-10). 전화번호·회사는 없으면 만든다 (가입 → 회사 등록).
# 사업자번호 아홉째 자리가 스텁 국세청 결과를 정한다: 7 휴업, 8 폐업, 9 불일치, 나머지 계속사업자
EXTRA_BROKERS = [
    # phone, 회사, 대표, 사업자번호 앞 9자리, 허가번호, 개업일, 사진 글자(영문), 상태
    ('01098765434', '[시연] 세종화물주선', '정세종', '813110003', '시연-2026-003', '2019-04-01', 'SEJONG FREIGHT', 'RESUBMITTED'),  # 사업자등록증 반려 뒤 재제출 → 심사 대기 + 재제출 꼬리표
    ('01098765435', '[시연] 남부운송', '남기준', '814110007', '시연-2026-004', '2016-10-10', 'NAMBU TRANSPORT', 'PENDING'),  # 국세청 휴업 → 빨간 꼬리표
    ('01098765436', '[시연] 동해로지스', '이동해', '815110009', '시연-2026-005', '2022-02-02', 'DONGHAE LOGIS', 'HALF'),  # 사업자등록증만 제출, 국세청 불일치
    ('01098765437', '[시연] 한강익스프레스', '강한수', '816110004', '시연-2026-006', '2013-07-07', 'HANGANG EXPRESS', 'REJECTED'),  # 허가증 반려, 아직 다시 안 냄 → 반려 탭
    ('01098765438', '[시연] 호남물류', '호남진', '817110005', '시연-2026-007', '2011-03-15', 'HONAM LOGIS', 'APPROVED'),  # 둘 다 승인 → 승인 탭
]
EXTRA_PHONES = [row[0] for row in EXTRA_BROKERS]


def signup_or_login(phone, name):
    """주선사 담당자 번호로 로그인하고, 계정이 없으면 가입한다 (seed-console.py의 login과 같은 흐름)"""
    sent = send_code(phone)
    device = 'document-seed-' + str(uuid.uuid4())
    verified = api('POST', '/api/auth/phone/verify', {'phone': phone, 'code': sent['demoCode'], 'deviceId': device})
    body = {'phoneToken': verified['phoneToken'], 'deviceId': device}
    if verified['accountExists']:
        if verified['existingAccount']['role'] != 'BROKER' or not verified['existingAccount']['name'].startswith('[시연]'):
            raise RuntimeError(f'{phone}: 시연 번호가 기존 계정과 겹쳐 건너뜁니다.')
        return api('POST', '/api/auth/phone/login', body)['accessToken']
    body.update(role='BROKER', name=name, consents=[{'type': t, 'version': '2026-11', 'agreed': True} for t in ('TERMS', 'PRIVACY')])
    return api('POST', '/api/auth/signup', body)['accessToken']


def ensure_company(token, name, representative, biz9, license_no):
    """소속 회사가 없으면 등록한다. 등록한 계정이 대표가 된다"""
    try:
        api('GET', '/api/brokers/me', token=token)
        return False
    except ApiError as error:
        if error.code != 'BROKER_404_MEMBER':
            raise
    api('POST', '/api/brokers', {'name': name, 'businessNumber': business_number(biz9), 'licenseNumber': license_no,
                                 'representativeName': representative, 'settlementDays': 30, 'defaultFeeRate': 10}, token)
    return True


def seed_extra_brokers(admin):
    for phone, name, representative, biz9, license_no, opened, label, state in EXTRA_BROKERS:
        try:
            # 계정 이름은 담당자(대표) 이름. 상호를 넣으면 운영자 화면의 "대표 담당자"에 상호가 보인다
            token = signup_or_login(phone, f'[시연] {representative}')
        except RuntimeError as error:
            print(f'{name}: {error}')
            continue
        created = ensure_company(token, name, representative, biz9, license_no)
        if has_broker_documents(token):
            print(f'{name}: 이미 서류가 있어 건너뜀')
            continue
        company = dict(company=label, biz=business_number(biz9), license=license_no.replace('시연-', ''), opened=opened)
        biz_id = submit_broker(token, 'BUSINESS_REGISTRATION', **company)
        if state == 'HALF':
            print(f'{name}: 사업자등록증만 제출 (국세청 불일치, 1/2 심사 대기)' + (' · 회사 등록' if created else ''))
            continue
        lic_id = submit_broker(token, 'BROKER_LICENSE', **company)
        if state == 'RESUBMITTED':
            reject_broker(admin, biz_id, 'COMPANY_MISMATCH', '사진의 상호가 등록한 상호와 달라요')
            submit_broker(token, 'BUSINESS_REGISTRATION', attempt=2, **company)
            print(f'{name}: 사업자등록증 반려 → 재제출, 허가증 심사 대기')
        elif state == 'REJECTED':
            approve_broker(admin, biz_id)
            reject_broker(admin, lic_id, 'LICENSE_MISMATCH', '허가증의 번호가 등록한 허가 번호와 달라요')
            print(f'{name}: 사업자등록증 승인, 허가증 반려 (아직 다시 안 냄)')
        elif state == 'APPROVED':
            approve_broker(admin, biz_id)
            approve_broker(admin, lic_id)
            print(f'{name}: 서류 2종 승인 (인증된 주선사)')
        else:
            print(f'{name}: 서류 2종 제출, 심사 대기 (국세청 휴업)')


def approve(admin, document_id):
    api('POST', f'/api/admin/driver-documents/{document_id}/approve', token=admin)


def reject(admin, document_id, reason, note=None):
    api('POST', f'/api/admin/driver-documents/{document_id}/reject', {'reasonCode': reason, 'note': note}, token=admin)


def main():
    psql = 'psql'
    args = sys.argv[1:]
    reset = '--reset' in args
    args = [arg for arg in args if arg != '--reset']
    if args[:1] == ['--psql'] and len(args) == 2:
        psql = args[1]
    elif args:
        sys.exit(__doc__)

    db = Database(psql)
    ensure_admin(db)
    apply_constraints(db)
    if reset:
        reset_documents(db)
    admin = login(ADMIN_PHONE)
    if admin is None:
        raise RuntimeError('운영자 로그인에 실패했어요')
    print('운영자 로그인')

    all_types = DRIVER_DOCS

    # 이정호: 4종 모두 심사 대기 (가장 오래 기다린 차주가 되도록 먼저 올린다)
    token = driver_token(1)
    if token and not has_documents(token):
        for doc_type in all_types:
            submit(token, 1, doc_type)
        print(f'{DRIVERS[1]}: 4종 제출, 모두 심사 대기')
    elif token:
        print(f'{DRIVERS[1]}: 이미 서류가 있어 건너뜀')

    # 김민수: 4종 모두 승인 → 인증됨
    token = driver_token(0)
    if token and not has_documents(token):
        for doc_type in all_types:
            approve(admin, submit(token, 0, doc_type))
        print(f'{DRIVERS[0]}: 4종 제출·승인, 인증됨')
    elif token:
        print(f'{DRIVERS[0]}: 이미 서류가 있어 건너뜀')

    # 박성진: 면허증이 글씨 안 보임으로 반려됐다가 다시 낸 상태 (재제출, 심사 대기)
    token = driver_token(2)
    if token and not has_documents(token):
        approve(admin, submit(token, 2, 'BUSINESS_REGISTRATION'))
        approve(admin, submit(token, 2, 'VEHICLE_REGISTRATION'))
        reject(admin, submit(token, 2, 'DRIVER_LICENSE'), 'UNREADABLE')
        approve(admin, submit(token, 2, 'FREIGHT_QUALIFICATION'))
        submit(token, 2, 'DRIVER_LICENSE', attempt=2)
        print(f'{DRIVERS[2]}: 3종 승인, 면허증 반려 뒤 다시 제출 (심사 대기)')
    elif token:
        print(f'{DRIVERS[2]}: 이미 서류가 있어 건너뜀')

    # 최동욱: 2종 승인, 면허증 심사 대기, 자격증 미제출
    token = driver_token(3)
    if token and not has_documents(token):
        approve(admin, submit(token, 3, 'BUSINESS_REGISTRATION'))
        approve(admin, submit(token, 3, 'VEHICLE_REGISTRATION'))
        submit(token, 3, 'DRIVER_LICENSE')
        print(f'{DRIVERS[3]}: 2종 승인, 면허증 심사 대기, 자격증 미제출')
    elif token:
        print(f'{DRIVERS[3]}: 이미 서류가 있어 건너뜀')

    # 정우석: 사업자등록증 승인, 자동차등록증은 차량 번호 불일치로 반려 (아직 다시 안 냄)
    token = driver_token(4)
    if token and not has_documents(token):
        approve(admin, submit(token, 4, 'BUSINESS_REGISTRATION'))
        reject(admin, submit(token, 4, 'VEHICLE_REGISTRATION'), 'VEHICLE_MISMATCH', '등록증의 번호가 경기80바8199로 보여요')
        print(f'{DRIVERS[4]}: 사업자등록증 승인, 자동차등록증 반려')
    elif token:
        print(f'{DRIVERS[4]}: 이미 서류가 있어 건너뜀')

    # [시연] 한빛물류 (주선사): 서류 2종 승인 → 인증된 주선사. 승인 전에는 오더를 못 올려서 seed-console.py도 같은 함수로 먼저 인증한다
    token = login(BROKER_PHONE)
    if token:
        try:
            print('[시연] 한빛물류: ' + ('서류 2종 올리고 승인 (인증됨)' if ensure_broker_verified(token, admin) else '이미 인증됨'))
        except ApiError as error:
            # QA 중에 대표가 회사를 나갔거나 내보내진 경우(BROKER_403_REMOVED 등). 다른 시연 데이터는 계속 넣는다
            print(f'[시연] 한빛물류: 건너뜀 ({error.code} {error})')
    else:
        print('[시연] 한빛물류: 대표 담당자 계정이 없어 건너뜀 (seed-console.py 먼저)')

    # [시연] 두리운송 (주선사): 서류 2종 심사 대기 → 운영자 페이지의 주선사 심사 탭에 한 건
    token = login(BROKER2_PHONE)
    if token and not has_broker_documents(token):
        for doc_type in ('BUSINESS_REGISTRATION', 'BROKER_LICENSE'):
            submit_broker(token, doc_type, company='DURI TRANSPORT', biz='812-11-00020', license='2026-002', opened='2021-09-01')
        print('[시연] 두리운송: 서류 2종 제출, 심사 대기')
    elif token:
        print('[시연] 두리운송: 이미 서류가 있어 건너뜀')
    else:
        print('[시연] 두리운송: 대표 담당자 계정이 없어 건너뜀 (seed-console.py 먼저)')

    # 한지훈: 사업자등록증만 냈는데 국세청 조회가 폐업자로 나와 눈여겨볼 건 (심사 대기)
    token = driver_token(5)
    if token and not has_documents(token):
        submit(token, 5, 'BUSINESS_REGISTRATION')
        print(f'{DRIVERS[5]}: 사업자등록증 제출, 국세청 조회 폐업자 (심사 대기)')
    elif token:
        print(f'{DRIVERS[5]}: 이미 서류가 있어 건너뜀')
    # 운영자 심사 화면을 채우는 추가 주선사 5곳: 재제출·휴업·불일치·반려·승인이 하나씩
    seed_extra_brokers(admin)
    print(f'\n운영자 페이지: 콘솔 /admin/login 에서 {ADMIN_PHONE[:3]}-{ADMIN_PHONE[3:7]}-{ADMIN_PHONE[7:]} 로 로그인')


if __name__ == '__main__':
    main()
