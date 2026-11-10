#!/usr/bin/env python3
"""
운영 서버에 공모전 시연용 데이터를 넣는다. 모든 이름에 [시연]을 붙여 실제 데이터와 구분한다.
지울 때는 scripts/clear-demo.sql을 쓴다.

  IEO_API_BASE=https://ieo-server-....run.app python3 scripts/seed-demo.py

- 시연 차주(010-0000-0001, 5톤 카고)와 시연 주선사(010-0000-0002, [시연] 한빛물류, 대기비 정책)를 만든다. 이미 있으면 로그인한다
- 시연 주선사의 회사 서류 2종이 승인돼 있지 않으면 운영자(IEO_ADMIN_PHONE, DB에 ADMIN으로 넣어 둔 계정)로 로그인해 올리고 승인한다.
  승인 전에는 오더를 올릴 수 없다 (07 운영자 서류심사 인계 13장)
- 모집 중인 시연 오더가 없으면 오늘·내일 상차로 4건 올린다 (상차 시간이 지나면 마감되니 시연 전날 다시 돌린다)
- 시연 차주에게 예약된 운행이 없으면 오더 하나를 요청하고 시연 주선사가 확정한다
- 지급일 지난 장부가 없으면 지난 운행 2줄을 넣는다

인증번호: 서버가 시연용 번호(demoCode)를 주면 그것을 쓰고, 아니면 Cloud Logging의 [문자 발송] 기록에서 읽는다
(IEO_GCLOUD_ACCOUNT, IEO_GCLOUD_PROJECT). 번호는 화면에 찍지 않는다.
"""
import importlib.util
import os
import pathlib
import re
import subprocess
import sys
import time
import uuid
from datetime import datetime, timedelta, timezone

spec = importlib.util.spec_from_file_location("seed_local", pathlib.Path(__file__).with_name("seed-local.py"))
base = importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)
call, ApiError, order, site, KST = base.call, base.ApiError, base.order, base.site, base.KST

TAG = "[시연] "
DRIVER_PHONE = "01000000001"
BROKER_PHONE = "01000000002"
ACCOUNT = os.environ.get("IEO_GCLOUD_ACCOUNT", "shg0102kr@artiwealth.com")
PROJECT = os.environ.get("IEO_GCLOUD_PROJECT", "ieo-server-prod")
ADMIN_PHONE = os.environ.get("IEO_ADMIN_PHONE")


def verify_company(broker):
    """회사 서류 2종(사업자등록증·운송주선사업 허가증)이 승인돼야 오더를 올릴 수 있다. 운영자 번호가 있으면 서류를 올리고 승인한다"""
    if call("GET", "/api/brokers/me/documents", token=broker)["verified"]:
        return
    if not ADMIN_PHONE:
        sys.exit("시연 주선사의 회사 서류가 승인되지 않아 오더를 올릴 수 없어요. 운영자 번호를 IEO_ADMIN_PHONE로 주면 서류를 올리고 승인해요 "
                 "(운영자 계정은 scripts/users-role-admin.sql 적용 뒤 users에 role=ADMIN으로 넣는다)")
    device_id = f"seed-demo-admin-{uuid.uuid4()}"
    verified = phone_token(ADMIN_PHONE, device_id)
    if not verified["accountExists"]:
        sys.exit("IEO_ADMIN_PHONE 계정이 없어요. 운영자는 가입으로 못 만들고 DB에 role=ADMIN으로 넣어요")
    admin = call("POST", "/api/auth/phone/login", {"phoneToken": verified["phoneToken"], "deviceId": device_id})["accessToken"]
    docs_spec = importlib.util.spec_from_file_location("seed_documents", pathlib.Path(__file__).with_name("seed-documents.py"))
    docs = importlib.util.module_from_spec(docs_spec)
    docs_spec.loader.exec_module(docs)
    docs.BASE = base.BASE
    if docs.ensure_broker_verified(broker, admin, company="HANBIT LOGISTICS", biz="999-99-9990", license="0000", opened="2018-05-14"):
        print("시연 주선사 회사 서류 2종 올리고 승인 (인증된 주선사)")


def code_from_cloud_logging(phone, since):
    """Cloud Run 로그에서 이 번호로 보낸 가장 최근 인증번호를 읽는다. 로그가 쌓이는 데 몇 초 걸린다"""
    pattern = re.compile(rf"\[문자 발송\] to={phone} message=.*인증번호 (\d{{6}})")
    query = f'resource.type="cloud_run_revision" AND textPayload:"to={phone}" AND timestamp>="{since}"'
    for _ in range(30):
        out = subprocess.run(
            ["gcloud", "logging", "read", query, "--limit=5", "--format=value(textPayload)",
             f"--account={ACCOUNT}", f"--project={PROJECT}"],
            capture_output=True, text=True).stdout
        codes = pattern.findall(out)
        if codes:
            return codes[0]
        time.sleep(3)
    sys.exit(f"인증번호를 Cloud Logging에서 찾지 못했어요: {phone}")


def phone_token(phone, device_id):
    since = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    try:
        sent = call("POST", "/api/auth/phone/code", {"phone": phone})
    except ApiError as e:
        if e.status != 429:
            raise
        print("인증번호는 1분에 한 번만 받을 수 있어 1분 기다려요")
        time.sleep(61)
        sent = call("POST", "/api/auth/phone/code", {"phone": phone})
    code = sent.get("demoCode") if isinstance(sent, dict) else None
    code = code or code_from_cloud_logging(phone, since)
    return call("POST", "/api/auth/phone/verify", {"phone": phone, "code": code, "deviceId": device_id})


def login_or_signup(phone, role, name, vehicle=None):
    device_id = f"seed-demo-{role.lower()}-{uuid.uuid4()}"
    verified = phone_token(phone, device_id)
    if verified["accountExists"]:
        return call("POST", "/api/auth/phone/login",
                    {"phoneToken": verified["phoneToken"], "deviceId": device_id})["accessToken"], False
    consents = [{"type": t, "version": "2026-11", "agreed": True} for t in ("TERMS", "PRIVACY")]
    if role == "DRIVER":
        consents += [{"type": t, "version": "2026-11", "agreed": True} for t in ("LOCATION", "BROKER_SHARE", "VOICE")]
    body = {"phoneToken": verified["phoneToken"], "deviceId": device_id, "role": role, "name": name, "consents": consents}
    if vehicle:
        body["vehicle"] = vehicle
    return call("POST", "/api/auth/signup", body)["accessToken"], True


def main():
    driver, new = login_or_signup(DRIVER_PHONE, "DRIVER", "시연기사",
                                  {"plateNumber": "99하9999", "tonnage": "TON_5", "bodyType": "CARGO", "heightM": 3.6})
    print("시연 차주", "가입" if new else "로그인", DRIVER_PHONE)

    broker, new = login_or_signup(BROKER_PHONE, "BROKER", "시연주선")
    try:
        call("GET", "/api/brokers/me", token=broker)
        print("시연 주선사 로그인", BROKER_PHONE)
    except ApiError as e:
        if e.status != 404:
            raise
        call("POST", "/api/brokers", {"name": TAG + "한빛물류", "businessNumber": base.business_number("999999990"),
                                      "licenseNumber": "시연-0000", "representativeName": "시연주선"}, token=broker)
        call("PUT", "/api/brokers/me/wait-policy",
             {"freeMinutes": 120, "unitMinutes": 30, "unitAmount": 10000, "maxAmount": 50000, "rounding": "UP"}, token=broker)
        print("시연 주선사 회사 등록:", TAG + "한빛물류")
    verify_company(broker)

    today = datetime.now(KST).date()
    tomorrow = today + timedelta(days=1)
    soon = min(datetime.now(KST).hour + 2, 21)
    pyeongtaek = site(TAG + "평택 물류센터", "경기 평택시 포승읍 평택항로 156", 36.9631, 126.8337)
    icheon = site(TAG + "이천 냉동창고", "경기 이천시 마장면 프리미엄아울렛로 45", 37.2405, 127.3650)
    daejeon = site(TAG + "대전 산업단지", "대전 대덕구 대화로 106", 36.3700, 127.4160)
    busan = site(TAG + "부산 신항 CY", "부산 강서구 신항로 245", 35.0780, 128.8320)
    gimpo = site(TAG + "김포 한강 물류", "경기 김포시 고촌읍 아라육로 42", 37.6060, 126.7720)
    orders = [
        order(pyeongtaek, daejeon, today, soon, min(soon + 2, 23), min(soon + 6, 23), TAG + "파레트 12개 (식품)", 320000,
              billedFare=400000, customerName=TAG + "대한식품"),
        order(icheon, busan, today, soon, min(soon + 3, 23), 9, TAG + "건축 자재", 580000, feeRate=12.5, customerName=TAG + "성원건설"),
        order(gimpo, pyeongtaek, tomorrow, 8, 10, 14, TAG + "가전 박스", 260000, paymentType="PREPAID"),
        order(daejeon, gimpo, tomorrow, 13, 15, 19, TAG + "기계 부품", 300000),
    ]
    existing = call("GET", "/api/broker/orders?page=0&size=50", token=broker)["items"]
    open_ids = [o["id"] for o in existing if o["open"]]
    if open_ids:
        ids = open_ids
        print("모집 중인 시연 오더가 있어 등록은 건너뜀:", len(ids), "건")
    else:
        ids = [call("POST", "/api/broker/orders", body, token=broker)["id"] for body in orders]
        print("시연 오더 등록", len(ids), "건")

    trips = call("GET", "/api/trips", token=driver)
    items = trips if isinstance(trips, list) else trips.get("items", [])
    if any(t.get("status") not in ("COMPLETED", "CANCELED") for t in items):
        print("예약된 시연 운행이 있어 배차 확정은 건너뜀")
    else:
        request = call("POST", f"/api/orders/{ids[0]}/requests", {}, token=driver)
        call("POST", f"/api/broker/orders/{ids[0]}/requests/{request['id']}/confirm", token=broker)
        print("시연 배차 확정 1건")

    if call("GET", "/api/ledger/receivables", token=driver)["overdueFareTotal"] > 0:
        print("지급일 지난 시연 장부가 있어 건너뜀")
    else:
        call("POST", "/api/ledger", {"workDate": str(today - timedelta(days=40)), "description": TAG + "인천 → 광주",
                                     "fare": 420000, "paymentType": "RECEIPT", "brokerName": TAG + "대한운송",
                                     "expectedPaymentDate": str(today - timedelta(days=10))}, token=driver)
        call("POST", "/api/ledger", {"workDate": str(today - timedelta(days=3)), "description": TAG + "수원 → 천안",
                                     "fare": 180000, "paymentType": "PREPAID", "brokerName": TAG + "한빛물류"}, token=driver)
        print("시연 장부 2줄 추가")


if __name__ == "__main__":
    main()
