"""Re-parses report.docx for a richer set of market_report_metric rows
(day/time/gender/age highlights, rankings, apartment info, etc.) plus a
standalone 종합의견 opinion_text, without touching the other 8 tables."""
import html
import os
import re
import zipfile

import oracledb
from dotenv import load_dotenv

load_dotenv()

REPORT_DOCX = os.path.join(os.path.dirname(__file__), "..", "report.docx")


def extract_docx_text(path):
    with zipfile.ZipFile(path) as z:
        xml = z.read("word/document.xml").decode("utf-8")
    text = re.sub(r"<[^>]+>", "", xml)
    return html.unescape(text)


def extract_opinion(text):
    m = re.search(r"종합의견(.*?)주요항목 분석", text, re.DOTALL)
    if not m:
        return None
    opinion = m.group(1)
    opinion = re.sub(r"\d{6,}", "", opinion)  # strip EMU position-offset noise
    return opinion.strip()


METRIC_PATTERNS = [
    ("점포수", r"점포수는\s*([\d,]+)\s*개\s*입니다", "개",
     r"점포수[\s\S]{0,40}?전분기 대비\s*([+-][\d.]+개)", r"점포수[\s\S]{0,40}?전년 동분기 대비\s*([+-][\d.]+개)"),
    ("생존율(3년)", r"신생기업 생존율은\s*([\d.]+)%\s*입니다", "%",
     r"생존율[\s\S]{0,60}?전분기 대비\s*([+-][\d.]+%)", r"생존율[\s\S]{0,60}?전년 동분기 대비\s*([+-][\d.]+%)"),
    ("평균영업기간", r"평균\s*영업기간은\s*([\d.]+)\s*년\s*입니다", "년", None, None),
    ("개업수", r"개업수은\s*([\d,]+)\s*개\s*입니다", "개",
     r"개업수[\s\S]{0,40}?전분기 대비\s*([+-][\d.]+개)", r"개업수[\s\S]{0,40}?전년 동분기 대비\s*([+-][\d.]+개)"),
    ("폐업수", r"페업수는\s*([\d,]+)\s*개\s*입니다", "개",
     r"페업수[\s\S]{0,40}?전분기 대비\s*([+-][\d.]+개)", r"페업수[\s\S]{0,40}?전년 동분기 대비\s*([+-][\d.]+개)"),
    ("매출액(월평균)", r"점포당 월평균 매출액은\s*([\d,]+)\s*만원\s*입니다", "만원",
     r"매출액[\s\S]{0,60}?전분기 대비\s*([+-][\d.]+만원)", r"매출액[\s\S]{0,60}?전년 동분기 대비\s*([+-][\d.]+만원)"),
    ("매출건수(월평균)", r"점포당 월평균 매출건수는\s*([\d,]+)\s*건\s*입니다", "건",
     r"매출건수[\s\S]{0,60}?전분기 대비\s*([+-][\d.]+건)", r"매출건수[\s\S]{0,60}?전년 동분기 대비\s*([+-][\d.]+건)"),
    ("유동인구(일평균)", r"유동인구\s*수는\s*일평균\s*([\d,]+)\s*명", "명",
     r"유동인구\s*수는[\s\S]{0,80}?전분기 대비\s*([+-][\d.]+명)", r"유동인구\s*수는[\s\S]{0,80}?전년 동분기 대비\s*([+-][\d.]+명)"),
    ("유동인구밀도", r"밀도는\s*([\d,]+)\s*명\s*/\s*ha\s*입니다", "명/ha", None, None),
    ("주거인구", r"주거인구는\s*([\d,]+)\s*명", "명", None, None),
    ("직장인구", r"직장인구\s*수는\s*([\d,]+)\s*명", "명", None, None),
    ("가구세대수", r"가구세대\s*수는\s*([\d,]+)\s*가구\s*입니다", "가구", None, None),
    ("임대시세(1층, 3.3㎡당)", r"1층\s*임대료가\s*3\.3㎡당\s*([\d,]+)\s*원입니다", "원", None, None),
    ("소득수준", r"소득수준은\s*0?(\d+)\s*분위입니다", "분위", None, None),
]

TEXT_PATTERNS = [
    ("요일별 매출 1위", r"요일별 매출(\S+\(\s*[\d.]+%\s*\)) 매출이 가장 높아요"),
    ("시간대별 매출 1위", r"시간대별 매출(\d+\s*~\s*\d+시) 매출이 가장 높아요"),
    ("성별 매출 1위", r"성별 매출(\S+\(\s*[\d.]+%\s*\)) 매출이 높아요"),
    ("요일별 유동인구 1위", r"요일별 유동인구(\S+\s*\(\s*[\d.]+%\s*\)) 유동인구가 가장 높아요"),
    ("시간대별 유동인구 1위", r"시간대별 유동인구(\d+\s*~\s*\d+시) 유동인구가 가장 높아요"),
    ("성별·연령별 유동인구 1위", r"성별, 연령별 유동인구(\S+,\s*\d+대\s*\(\s*[\d.]+%\s*\)) 유동인구가 가장 많아요"),
    ("성별·연령별 주거인구 1위", r"성별, 연령별 주거인구(\S+,\s*\d+대\(\s*[\d.]+%\s*\)) 주거인구가 가장 많아요"),
    ("성별·연령별 직장인구 1위", r"성별, 연령별 직장인구(\S+,\s*\d+대\(\s*[\d.]+%\s*\)) 직장인구가 가장 많아요"),
    ("업종분포", r"업종분포(외식업이 가장 많고 소매업이 증가 추세입니다)"),
    ("소비트렌드 1위", r"소비트렌드(선택상권은 음식비율이 가장 많습니다)"),
    ("아파트 현황", r"아파트 현황(\S+ 미만 아파트가 가장 많으며 [^.]+\.)"),
]

RANK_PATTERN = re.compile(
    r"자치구 내 행정동 (\d+)개 중 [^,]+의 점포수는\s*(\d+)위,\s*매출액\s*(\d+)위,\s*유동인구\s*(\d+)위\s*입니다"
)

FACILITY_PATTERN = re.compile(
    r"관공서(\d{1,3})유통점(\d{1,3})금융기관(\d{1,3})극장(\d{1,3})병원(\d{1,3})숙박시설(\d{1,3})학교(\d{1,3})교통시설(\d{1,3})"
)


def connect():
    dsn = oracledb.makedsn(
        os.environ["ORACLE_HOST"],
        int(os.environ.get("ORACLE_PORT", 1521)),
        service_name=os.environ["ORACLE_SERVICE_NAME"],
    )
    return oracledb.connect(user=os.environ["ORACLE_USER"], password=os.environ["ORACLE_PASSWORD"], dsn=dsn)


def main():
    text = extract_docx_text(REPORT_DOCX)
    opinion = extract_opinion(text)

    conn = connect()
    cur = conn.cursor()

    cur.execute("SELECT report_id FROM market_report ORDER BY report_id DESC FETCH FIRST 1 ROWS ONLY")
    row = cur.fetchone()
    if not row:
        print("market_report에 리포트가 없습니다. load_data.py를 먼저 실행하세요.")
        return
    report_id = row[0]

    cur.execute("UPDATE market_report SET opinion_text = :1 WHERE report_id = :2", [opinion, report_id])

    cur.execute("DELETE FROM market_report_metric WHERE report_id = :1", [report_id])

    metrics = []
    for name, pattern, unit, qoq_pat, yoy_pat in METRIC_PATTERNS:
        m = re.search(pattern, text)
        if not m:
            continue
        qoq = re.search(qoq_pat, text).group(1) if qoq_pat and re.search(qoq_pat, text) else None
        yoy = re.search(yoy_pat, text).group(1) if yoy_pat and re.search(yoy_pat, text) else None
        metrics.append((report_id, name, m.group(1).replace(",", ""), unit, qoq, yoy, None))

    for name, pattern in TEXT_PATTERNS:
        m = re.search(pattern, text)
        if m:
            metrics.append((report_id, name, m.group(1), None, None, None, None))

    rank_m = RANK_PATTERN.search(text)
    if rank_m:
        total, store_rank, sales_rank, foot_rank = rank_m.groups()
        note = f"자치구 내 행정동 {total}개 중"
        metrics.append((report_id, "점포수 순위", store_rank, "위", None, None, note))
        metrics.append((report_id, "매출액 순위", sales_rank, "위", None, None, note))
        metrics.append((report_id, "유동인구 순위", foot_rank, "위", None, None, note))

    fac_m = FACILITY_PATTERN.search(text)
    if fac_m:
        labels = ["관공서", "유통점", "금융기관", "극장", "병원", "숙박시설", "학교", "교통시설"]
        note = ", ".join(f"{label} {count}" for label, count in zip(labels, fac_m.groups()))
        metrics.append((report_id, "주요 시설", note, "개", None, None, None))

    cur.executemany(
        "INSERT INTO market_report_metric (report_id, metric_name, value, unit, qoq_change, yoy_change, note) "
        "VALUES (:1, :2, :3, :4, :5, :6, :7)",
        metrics,
    )
    conn.commit()
    print(f"리포트 #{report_id}: 종합의견 {'있음' if opinion else '없음'}, 지표 {len(metrics)}건 반영 완료")

    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
