"""Loads the 5-quarter trend series and day/time/gender/age breakdowns that
were read directly off the chart images embedded in report.docx
(word/media/image11,23,24,26,27,29-32,34.png) — real numbers transcribed by
eye, not fabricated. See conversation history for which image maps to what."""
import os

import psycopg2
from dotenv import load_dotenv

load_dotenv()

QUARTERS = ["2025년 2분기", "2025년 3분기", "2025년 4분기", "2026년 1분기", "2026년 2분기"]

SERIES = {
    "점포수": {"mine": [1118, 1099, 1091, 1080, 1067], "gu": None, "seoul": None},
    "매출액": {"mine": [1498, 1543, 1450, 1394, 1542], "gu": [902, 940, 882, 841, 949], "seoul": [896, 926, 856, 831, 923]},
    "매출건수": {"mine": [1740, 1702, 1522, 1364, 1627], "gu": [1188, 1233, 1101, 1028, 1231], "seoul": [1108, 1212, 979, 899, 1073]},
    "유동인구밀도": {"mine": [90832, 90152, 91001, 90661, 92253], "gu": [46993, 46704, 46616, 46810, 46756], "seoul": [37681, 37485, 37509, 37868, 37818]},
}

BREAKDOWNS = [
    ("요일별매출", [("월요일", 11.1), ("화요일", 12.1), ("수요일", 11.9), ("목요일", 11.9),
                 ("금요일", 15.2), ("토요일", 21.1), ("일요일", 16.8)]),
    ("시간대별매출", [("00~06시", 1.6), ("06~11시", 6.8), ("11~14시", 25.2),
                  ("14~17시", 30.2), ("17~21시", 29.4), ("21~24시", 6.8)]),
    ("성별매출", [("여성", 62.1), ("남성", 37.9)]),
    ("업종별여성매출비중", [("외식업", 65.7), ("서비스업", 50.0), ("소매업", 58.6)]),
    ("연령대별외식업매출", [("10대", 2.8), ("20대", 39.4), ("30대", 28.5),
                     ("40대", 12.4), ("50대", 11.4), ("60대+", 5.6)]),
]


def connect():
    return psycopg2.connect(
        host=os.environ["PG_POOL_HOST"],
        port=os.environ["PG_POOL_PORT"],
        dbname=os.environ["PG_POOL_DATABASE"],
        user=os.environ["PG_POOL_USER"],
        password=os.environ["PG_POOL_PASSWORD"],
    )


def main():
    conn = connect()
    cur = conn.cursor()

    cur.execute("SELECT report_id FROM market_report ORDER BY report_id DESC FETCH FIRST 1 ROWS ONLY")
    row = cur.fetchone()
    if not row:
        print("market_report에 리포트가 없습니다.")
        return
    report_id = row[0]

    cur.execute("DELETE FROM market_report_series WHERE report_id = %s", [report_id])
    cur.execute("DELETE FROM market_report_breakdown WHERE report_id = %s", [report_id])

    series_rows = []
    for metric, vals in SERIES.items():
        for i, q in enumerate(QUARTERS):
            gu = vals["gu"][i] if vals["gu"] else None
            seoul = vals["seoul"][i] if vals["seoul"] else None
            series_rows.append((report_id, metric, q, vals["mine"][i], gu, seoul))
    cur.executemany(
        "INSERT INTO market_report_series (report_id, metric_name, quarter_label, mine_value, gu_value, seoul_value) "
        "VALUES (%s, %s, %s, %s, %s, %s)",
        series_rows,
    )

    breakdown_rows = []
    for category, items in BREAKDOWNS:
        for label, value in items:
            breakdown_rows.append((report_id, category, label, value))
    cur.executemany(
        "INSERT INTO market_report_breakdown (report_id, category, label, value) VALUES (%s, %s, %s, %s)",
        breakdown_rows,
    )

    conn.commit()
    print(f"리포트 #{report_id}: 추이 {len(series_rows)}건, 세부분포 {len(breakdown_rows)}건 반영 완료")
    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
