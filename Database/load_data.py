"""Creates the Oracle schema and loads every crawled Excel file + report.docx
into it.

Setup:
    1. pip install oracledb python-dotenv openpyxl
    2. cp .env.example .env   (fill in ORACLE_HOST/PORT/SERVICE_NAME/USER/PASSWORD)
    3. python load_data.py
"""
import os
import re
import sys
import zipfile
from datetime import date, datetime

import oracledb
from dotenv import load_dotenv
from openpyxl import load_workbook

load_dotenv()

BASE = os.path.join(os.path.dirname(__file__), "..", "Web crawling")
CAFES_XLSX = os.path.join(BASE, "Home", "cafes.xlsx")
MENU_XLSX = os.path.join(BASE, "Menu", "menu.xlsx")
REVIEW_XLSX = os.path.join(BASE, "Review", "Review.xlsx")
REVIEW_CATEGORY_XLSX = os.path.join(BASE, "Review", "Review_category.xlsx")
INFO_XLSX = os.path.join(BASE, "Info", "Info.xlsx")
AI_BRIEFING_XLSX = os.path.join(BASE, "AIBriefing", "AIBriefing.xlsx")
REPORT_DOCX = os.path.join(os.path.dirname(__file__), "..", "report.docx")

DDL_STATEMENTS = [
    "BEGIN EXECUTE IMMEDIATE 'DROP TABLE ai_briefing'; EXCEPTION WHEN OTHERS THEN NULL; END;",
    "BEGIN EXECUTE IMMEDIATE 'DROP TABLE store_info_items'; EXCEPTION WHEN OTHERS THEN NULL; END;",
    "BEGIN EXECUTE IMMEDIATE 'DROP TABLE store_intro'; EXCEPTION WHEN OTHERS THEN NULL; END;",
    "BEGIN EXECUTE IMMEDIATE 'DROP TABLE review_category_tags'; EXCEPTION WHEN OTHERS THEN NULL; END;",
    "BEGIN EXECUTE IMMEDIATE 'DROP TABLE reviews'; EXCEPTION WHEN OTHERS THEN NULL; END;",
    "BEGIN EXECUTE IMMEDIATE 'DROP TABLE menu'; EXCEPTION WHEN OTHERS THEN NULL; END;",
    "BEGIN EXECUTE IMMEDIATE 'DROP TABLE market_report_metric'; EXCEPTION WHEN OTHERS THEN NULL; END;",
    "BEGIN EXECUTE IMMEDIATE 'DROP TABLE market_report'; EXCEPTION WHEN OTHERS THEN NULL; END;",
    "BEGIN EXECUTE IMMEDIATE 'DROP TABLE stores'; EXCEPTION WHEN OTHERS THEN NULL; END;",
    """CREATE TABLE stores (
        store_id        VARCHAR2(10)   PRIMARY KEY,
        name            VARCHAR2(200),
        address         VARCHAR2(500),
        subway_info     VARCHAR2(200),
        business_hours  CLOB,
        naver_id        VARCHAR2(20) UNIQUE,
        lat             NUMBER(9,6),
        lng             NUMBER(9,6)
    )""",
    """CREATE TABLE menu (
        menu_id         VARCHAR2(10)   PRIMARY KEY,
        store_id        VARCHAR2(10)   NOT NULL REFERENCES stores(store_id),
        menu_name       VARCHAR2(300),
        price_krw       NUMBER,
        price_note      VARCHAR2(50)
    )""",
    "CREATE INDEX idx_menu_store ON menu(store_id)",
    """CREATE TABLE reviews (
        review_id         VARCHAR2(20)   PRIMARY KEY,
        store_id          VARCHAR2(10)   NOT NULL REFERENCES stores(store_id),
        reviewer_id       VARCHAR2(50),
        rating            NUMBER(2,1),
        visit_time        VARCHAR2(20),
        wait_time         VARCHAR2(100),
        tags              VARCHAR2(1000),
        review_text       CLOB,
        review_date_text  VARCHAR2(20),
        review_date       DATE,
        visit_count_text  VARCHAR2(30),
        visit_count       NUMBER
    )""",
    "CREATE INDEX idx_reviews_store ON reviews(store_id)",
    "CREATE INDEX idx_reviews_date ON reviews(review_date)",
    """CREATE TABLE review_category_tags (
        store_id                   VARCHAR2(10)  NOT NULL REFERENCES stores(store_id),
        tag_text                   VARCHAR2(200) NOT NULL,
        mention_count              NUMBER,
        tag_category               VARCHAR2(50),
        store_total_participants   NUMBER,
        PRIMARY KEY (store_id, tag_text)
    )""",
    """CREATE TABLE store_intro (
        store_id    VARCHAR2(10) PRIMARY KEY REFERENCES stores(store_id),
        intro_text  CLOB
    )""",
    """CREATE TABLE store_info_items (
        id          NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
        store_id    VARCHAR2(10)  NOT NULL REFERENCES stores(store_id),
        section     VARCHAR2(50)  NOT NULL,
        item_text   VARCHAR2(500),
        detail      VARCHAR2(1000)
    )""",
    "CREATE INDEX idx_info_store ON store_info_items(store_id)",
    "CREATE INDEX idx_info_section ON store_info_items(section)",
    """CREATE TABLE ai_briefing (
        id          NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
        store_id    VARCHAR2(10) NOT NULL REFERENCES stores(store_id),
        sentence    VARCHAR2(1000)
    )""",
    "CREATE INDEX idx_briefing_store ON ai_briefing(store_id)",
    """CREATE TABLE market_report (
        report_id    NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
        location     VARCHAR2(300),
        industry     VARCHAR2(100),
        quarter      VARCHAR2(20),
        report_date  DATE,
        raw_text     CLOB
    )""",
    """CREATE TABLE market_report_metric (
        id           NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
        report_id    NUMBER NOT NULL REFERENCES market_report(report_id),
        metric_name  VARCHAR2(200),
        value        VARCHAR2(300),
        unit         VARCHAR2(50),
        qoq_change   VARCHAR2(100),
        yoy_change   VARCHAR2(100),
        note         VARCHAR2(1000)
    )""",
    "CREATE INDEX idx_metric_report ON market_report_metric(report_id)",
]


def connect():
    dsn = oracledb.makedsn(
        os.environ["ORACLE_HOST"],
        int(os.environ.get("ORACLE_PORT", 1521)),
        service_name=os.environ["ORACLE_SERVICE_NAME"],
    )
    return oracledb.connect(user=os.environ["ORACLE_USER"], password=os.environ["ORACLE_PASSWORD"], dsn=dsn)


def create_schema(cur):
    print("스키마 생성 중...")
    for stmt in DDL_STATEMENTS:
        cur.execute(stmt)
    print("완료.")


def rows(path, sheet=None, header_row=1):
    wb = load_workbook(path, read_only=True, data_only=True)
    ws = wb[sheet] if sheet else wb.active
    it = ws.iter_rows(values_only=True)
    headers = next(it)
    for r in it:
        if r[0] is None:
            continue
        yield dict(zip(headers, r))


def load_stores(cur):
    print("stores 적재 중...")
    data = [
        (
            r["store_id"], r["가게이름"], r["위치"], r["간편위치"], r["영업시간"], str(r["naver_id"]),
            float(r["lat"]) if r.get("lat") else None, float(r["lng"]) if r.get("lng") else None,
        )
        for r in rows(CAFES_XLSX)
    ]
    cur.executemany(
        "INSERT INTO stores (store_id, name, address, subway_info, business_hours, naver_id, lat, lng) "
        "VALUES (:1, :2, :3, :4, :5, :6, :7, :8)",
        data,
    )
    print(f"  {len(data)}건")


def to_price(value):
    if isinstance(value, (int, float)):
        return int(value), None
    if isinstance(value, str) and value.strip().isdigit():
        return int(value.strip()), None
    return None, (str(value).strip() if value not in (None, "") else None)


def load_menu(cur):
    print("menu 적재 중...")
    data = []
    for r in rows(MENU_XLSX):
        price_krw, price_note = to_price(r["price_krw"])
        data.append((r["menu_id"], r["store_id"], r["menu_name"], price_krw, price_note))
    cur.executemany(
        "INSERT INTO menu (menu_id, store_id, menu_name, price_krw, price_note) VALUES (:1, :2, :3, :4, :5)",
        data,
    )
    print(f"  {len(data)}건")


def parse_review_date(text):
    if not text:
        return None
    parts = [p for p in str(text).strip().split(".") if p]
    try:
        if len(parts) == 4:
            yy, m, d, _wd = parts
            year = 2000 + int(yy)
        elif len(parts) == 3:
            m, d, _wd = parts
            year = date.today().year
        else:
            return None
        return date(year, int(m), int(d))
    except ValueError:
        return None


def parse_visit_count(text):
    if not text:
        return None
    m = re.search(r"(\d+)", str(text))
    return int(m.group(1)) if m else None


def load_reviews(cur):
    print("reviews 적재 중 (시간이 좀 걸립니다)...")
    data = []
    for r in rows(REVIEW_XLSX):
        d = parse_review_date(r["review_date"])
        data.append((
            r["review_id"], r["store_id"], r["reviewer_id"],
            float(r["rating"]) if r["rating"] not in (None, "") else None,
            r["visit_time"], r["wait_time"], r["tags"], r["review_text"],
            r["review_date"], d, r["visit_count"], parse_visit_count(r["visit_count"]),
        ))
    cur.executemany(
        "INSERT INTO reviews (review_id, store_id, reviewer_id, rating, visit_time, wait_time, "
        "tags, review_text, review_date_text, review_date, visit_count_text, visit_count) "
        "VALUES (:1, :2, :3, :4, :5, :6, :7, :8, :9, :10, :11, :12)",
        data,
    )
    print(f"  {len(data)}건")


def load_review_category(cur):
    print("review_category_tags 적재 중...")
    best = {}
    dup_count = 0
    for r in rows(REVIEW_CATEGORY_XLSX):
        key = (r["store_id"], r["tag_text"])
        count = r["mention_count"] or 0
        if key in best:
            dup_count += 1
            if count <= (best[key][2] or 0):
                continue
        best[key] = (r["store_id"], r["tag_text"], count, r["tag_category"], r["store_total_participants"])
    data = list(best.values())
    cur.executemany(
        "INSERT INTO review_category_tags (store_id, tag_text, mention_count, tag_category, "
        "store_total_participants) VALUES (:1, :2, :3, :4, :5)",
        data,
    )
    print(f"  {len(data)}건 (중복 {dup_count}건 중 mention_count 낮은 쪽 제거)")


def load_info(cur):
    print("store_intro / store_info_items 적재 중...")
    intro = [(r["store_id"], r["intro_text"]) for r in rows(INFO_XLSX, sheet="소개")]
    cur.executemany("INSERT INTO store_intro (store_id, intro_text) VALUES (:1, :2)", intro)

    item_count = 0
    for sheet in ["편의시설 및 서비스", "노키즈존", "반려동물 동반", "주차", "좌석.공간", "결제수단", "SNS"]:
        section_rows = [(r["store_id"], sheet, r["item_text"], r["detail"]) for r in rows(INFO_XLSX, sheet=sheet)]
        cur.executemany(
            "INSERT INTO store_info_items (store_id, section, item_text, detail) VALUES (:1, :2, :3, :4)",
            section_rows,
        )
        item_count += len(section_rows)
    print(f"  소개 {len(intro)}건, 나머지 섹션 {item_count}건")


def load_ai_briefing(cur):
    print("ai_briefing 적재 중...")
    data = [(r["store_id"], r["sentence"]) for r in rows(AI_BRIEFING_XLSX)]
    cur.executemany("INSERT INTO ai_briefing (store_id, sentence) VALUES (:1, :2)", data)
    print(f"  {len(data)}건")


def extract_docx_text(path):
    with zipfile.ZipFile(path) as z:
        xml = z.read("word/document.xml").decode("utf-8")
    import html
    text = re.sub(r"<[^>]+>", "", xml)
    return html.unescape(text)


METRIC_PATTERNS = [
    ("점포수", r"점포수는\s*([\d,]+)\s*개\s*입니다", "개"),
    ("생존율(3년)", r"신생기업 생존율은\s*([\d.]+)%\s*입니다", "%"),
    ("평균영업기간", r"평균\s*영업기간은\s*([\d.]+)\s*년\s*입니다", "년"),
    ("개업수", r"개업수은\s*([\d,]+)\s*개\s*입니다", "개"),
    ("폐업수", r"페업수는\s*([\d,]+)\s*개\s*입니다", "개"),
    ("매출액(월평균)", r"점포당 월평균 매출액은\s*([\d,]+)\s*만원\s*입니다", "만원"),
    ("매출건수(월평균)", r"점포당 월평균 매출건수는\s*([\d,]+)\s*건\s*입니다", "건"),
    ("유동인구(일평균)", r"유동인구\s*수는\s*일평균\s*([\d,]+)\s*명", "명"),
    ("유동인구밀도", r"밀도는\s*([\d,]+)\s*명\s*/\s*ha\s*입니다", "명/ha"),
    ("주거인구", r"주거인구는\s*([\d,]+)\s*명", "명"),
    ("직장인구", r"직장인구\s*수는\s*([\d,]+)\s*명", "명"),
    ("가구세대수", r"가구세대\s*수는\s*([\d,]+)\s*가구\s*입니다", "가구"),
    ("임대시세(1층, 3.3㎡당)", r"1층\s*임대료가\s*3\.3㎡당\s*([\d,]+)\s*원입니다", "원"),
    ("소득수준", r"소득수준은\s*0?(\d+)\s*분위입니다", "분위"),
]


def load_market_report(cur):
    if not os.path.exists(REPORT_DOCX):
        print("report.docx 없음, 건너뜀")
        return
    print("market_report 적재 중...")
    text = extract_docx_text(REPORT_DOCX)

    loc_m = re.search(r"위치\s*([^업]+?)업종", text)
    industry_m = re.search(r"업종\s*([가-힣\-·]+)기분준기", text)
    quarter_m = re.search(r"(\d{4}년\s*\d분기)", text)
    date_m = re.search(r"(\d{4})년\s*(\d{2})월\s*(\d{2})일", text)

    location = loc_m.group(1).strip() if loc_m else None
    industry = industry_m.group(1).strip() if industry_m else None
    quarter = quarter_m.group(1) if quarter_m else None
    report_date = datetime(int(date_m.group(1)), int(date_m.group(2)), int(date_m.group(3))) if date_m else None

    cur.execute(
        "INSERT INTO market_report (location, industry, quarter, report_date, raw_text) "
        "VALUES (:1, :2, :3, :4, :5) RETURNING report_id INTO :6",
        [location, industry, quarter, report_date, text, cur.var(int)],
    )
    report_id = cur.bindvars[5].getvalue()[0]

    metrics = []
    for name, pattern, unit in METRIC_PATTERNS:
        m = re.search(pattern, text)
        if m:
            metrics.append((report_id, name, m.group(1).replace(",", ""), unit, None, None, None))
    if metrics:
        cur.executemany(
            "INSERT INTO market_report_metric (report_id, metric_name, value, unit, qoq_change, "
            "yoy_change, note) VALUES (:1, :2, :3, :4, :5, :6, :7)",
            metrics,
        )
    print(f"  리포트 1건 (위치={location}, 업종={industry}, 분기={quarter}), 지표 {len(metrics)}건 추출")


def main():
    conn = connect()
    cur = conn.cursor()
    try:
        create_schema(cur)
        conn.commit()

        load_stores(cur)
        conn.commit()
        load_menu(cur)
        conn.commit()
        load_review_category(cur)
        conn.commit()
        load_info(cur)
        conn.commit()
        load_ai_briefing(cur)
        conn.commit()
        load_market_report(cur)
        conn.commit()
        load_reviews(cur)  # last: by far the largest table
        conn.commit()

        print("\n모든 데이터 적재 완료.")
    finally:
        cur.close()
        conn.close()


if __name__ == "__main__":
    main()
