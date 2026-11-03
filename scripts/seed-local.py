#!/usr/bin/env python3
"""
로컬 서버에 차량 앱 확인용 테스트 데이터를 넣는다.

  python3 scripts/seed-local.py <서버 로그 파일>

- 차주(010-1234-5678, 5톤 카고)와 주선사(010-9876-5432, 회사·대기비 정책)를 만든다. 이미 있으면 로그인한다
- 주선사 오더가 하나도 없으면 오늘·내일 상차로 4건 올린다
- 차주에게 예약된 운행이 없으면 오더 하나를 요청하고 주선사가 확정한다
- 지급일 지난 장부가 없으면 직접 추가한 지난 운행 2줄을 넣는다. 하나는 지급일이 지나 미수금 경고에 나온다

인증번호는 문자 대신 서버 로그의 [문자 발송] 줄에서 읽는다 (local 프로필의 LogSmsSender).
"""
import json
import re
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import date, datetime, timedelta, timezone

BASE = "http://localhost:8080"
DRIVER_PHONE = "01012345678"
BROKER_PHONE = "01098765432"
KST = timezone(timedelta(hours=9))


class ApiError(Exception):
    def __init__(self, status, body):
        super().__init__(f"{status} {body}")
        self.status = status
        self.body = body


def call(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(req, data) as res:
            text = res.read().decode()
            return json.loads(text) if text else None
    except urllib.error.HTTPError as e:
        text = e.read().decode()
        try:
            raise ApiError(e.code, json.loads(text)) from None
        except json.JSONDecodeError:
            raise ApiError(e.code, text) from None


def read_code(log_path, phone, after_size):
    """로그 파일에서 이 번호로 보낸 인증번호 중 가장 최근 것을 읽는다."""
    pattern = re.compile(rf"\[문자 발송\] to={phone} message=.*인증번호 (\d{{6}})")
    for _ in range(20):
        with open(log_path, encoding="utf-8", errors="ignore") as f:
            f.seek(after_size)
            codes = pattern.findall(f.read())
        if codes:
            return codes[-1]
        time.sleep(0.3)
    sys.exit(f"인증번호를 로그에서 찾지 못했어요: {log_path}")


def log_size(log_path):
    with open(log_path, "rb") as f:
        return f.seek(0, 2)


def phone_token(log_path, phone, device_id):
    size = log_size(log_path)
    try:
        call("POST", "/api/auth/phone/code", {"phone": phone})
    except ApiError as e:
        if e.status != 429:
            raise
        print("인증번호는 1분에 한 번만 받을 수 있어 1분 기다려요")
        time.sleep(61)
        call("POST", "/api/auth/phone/code", {"phone": phone})
    code = read_code(log_path, phone, size)
    return call("POST", "/api/auth/phone/verify", {"phone": phone, "code": code, "deviceId": device_id})


def login_or_signup(log_path, phone, role, name, vehicle=None):
    device_id = f"seed-{role.lower()}-{uuid.uuid4()}"
    verified = phone_token(log_path, phone, device_id)
    if verified["accountExists"]:
        return call("POST", "/api/auth/phone/login",
                    {"phoneToken": verified["phoneToken"], "deviceId": device_id})["accessToken"], False
    consents = [{"type": t, "version": "v1", "agreed": True} for t in ("TERMS", "PRIVACY")]
    if role == "DRIVER":
        consents.append({"type": "LOCATION", "version": "v1", "agreed": True})
    body = {"phoneToken": verified["phoneToken"], "deviceId": device_id, "role": role,
            "name": name, "consents": consents}
    if vehicle:
        body["vehicle"] = vehicle
    return call("POST", "/api/auth/signup", body)["accessToken"], True


def business_number(first_nine="123456780"):
    """앞 9자리에 검증 번호(10번째)를 붙인 사업자등록번호. 계산은 Broker.hasValidCheckDigit와 같다."""
    weights = [1, 3, 7, 1, 3, 7, 1, 3, 5]
    total = sum(int(d) * w for d, w in zip(first_nine, weights)) + int(first_nine[8]) * 5 // 10
    return first_nine + str((10 - total % 10) % 10)


def site(name, address, lat, lng):
    return {"site": {"name": name, "address": address, "lat": lat, "lng": lng}}


def kst(day, hour, minute=0):
    return datetime(day.year, day.month, day.day, hour, minute, tzinfo=KST).astimezone(timezone.utc) \
        .isoformat().replace("+00:00", "Z")


def order(pickup, dropoff, day, from_h, until_h, by_h, cargo, fare, **extra):
    body = {"pickup": pickup, "dropoff": dropoff,
            "pickupFrom": kst(day, from_h), "pickupUntil": kst(day, until_h),
            "dropoffBy": kst(day + timedelta(days=1) if by_h < from_h else day, by_h),
            "cargoType": cargo, "tonnage": "TON_5", "bodyType": "CARGO", "loadingMethod": "FORKLIFT",
            "weightKg": 4500, "fare": fare, "paymentType": "RECEIPT", "expectedPaymentDays": 30,
            "consignmentType": "DIRECT"}
    body.update(extra)
    return body


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    log_path = sys.argv[1]

    driver, driver_new = login_or_signup(
        log_path, DRIVER_PHONE, "DRIVER", "김기사",
        {"plateNumber": "경기80바1234", "tonnage": "TON_5", "bodyType": "CARGO", "heightM": 3.6})
    print("차주", "가입" if driver_new else "로그인", DRIVER_PHONE)

    broker, broker_new = login_or_signup(log_path, BROKER_PHONE, "BROKER", "박주선")
    try:
        call("GET", "/api/brokers/me", token=broker)
        print("주선사 로그인", BROKER_PHONE)
    except ApiError as e:
        if e.status != 404:
            raise
        call("POST", "/api/brokers", {"name": "한빛물류", "businessNumber": business_number(),
                                      "licenseNumber": "경기-2024-0123", "representativeName": "박주선"},
             token=broker)
        call("PUT", "/api/brokers/me/wait-policy",
             {"freeMinutes": 120, "unitMinutes": 30, "unitAmount": 10000, "maxAmount": 50000,
              "rounding": "UP"}, token=broker)
        print("주선사 회사 등록: 한빛물류, 대기비 무료 2시간·30분당 1만 원")

    today = datetime.now(KST).date()
    tomorrow = today + timedelta(days=1)
    now_h = datetime.now(KST).hour
    soon = min(now_h + 2, 21)
    pyeongtaek = site("평택 물류센터", "경기 평택시 포승읍 평택항로 156", 36.9631, 126.8337)
    icheon = site("이천 냉동창고", "경기 이천시 마장면 프리미엄아울렛로 45", 37.2405, 127.3650)
    daejeon = site("대전 산업단지", "대전 대덕구 대화로 106", 36.3700, 127.4160)
    busan = site("부산 신항 CY", "부산 강서구 신항로 245", 35.0780, 128.8320)
    gimpo = site("김포 한강 물류", "경기 김포시 고촌읍 아라육로 42", 37.6060, 126.7720)

    orders = [
        order(pyeongtaek, daejeon, today, soon, min(soon + 2, 23), min(soon + 6, 23), "파레트 12개 (식품)", 320000),
        order(icheon, busan, today, soon, min(soon + 3, 23), 9, "건축 자재", 580000),
        order(gimpo, pyeongtaek, tomorrow, 8, 10, 14, "가전 박스", 260000, paymentType="PREPAID"),
        order(daejeon, gimpo, tomorrow, 13, 15, 19, "기계 부품", 0),
    ]
    existing = call("GET", "/api/broker/orders?page=0&size=50", token=broker)["items"]
    if existing:
        ids = [o["id"] for o in existing]
        print("오더가 이미 있어 등록은 건너뜀:", len(ids), "건")
    else:
        ids = [call("POST", "/api/broker/orders", body, token=broker)["id"] for body in orders]
        print("오더 등록", len(ids), "건:", ids)

    trips = call("GET", "/api/trips", token=driver)
    trip_items = trips if isinstance(trips, list) else trips.get("items", [])
    if any(t.get("status") not in ("COMPLETED", "CANCELED") for t in trip_items):
        print("예약된 운행이 이미 있어 배차 확정은 건너뜀")
    else:
        request = call("POST", f"/api/orders/{ids[0]}/requests", {}, token=driver)
        call("POST", f"/api/broker/orders/{ids[0]}/requests/{request['id']}/confirm", token=broker)
        print("배차 확정: 오더", ids[0], "→ 홈 '내 배차'")

    if call("GET", "/api/ledger/receivables", token=driver)["overdueFareTotal"] > 0:
        print("지급일 지난 장부가 이미 있어 장부 추가는 건너뜀")
    else:
        call("POST", "/api/ledger", {"workDate": str(today - timedelta(days=40)), "description": "인천 → 광주",
                                     "fare": 420000, "paymentType": "RECEIPT", "brokerName": "대한운송",
                                     "expectedPaymentDate": str(today - timedelta(days=10))}, token=driver)
        call("POST", "/api/ledger", {"workDate": str(today - timedelta(days=3)), "description": "수원 → 천안",
                                     "fare": 180000, "paymentType": "PREPAID", "brokerName": "한빛물류"},
             token=driver)
        print("장부 2줄 추가 (1줄은 지급일 10일 지남)")


if __name__ == "__main__":
    main()
