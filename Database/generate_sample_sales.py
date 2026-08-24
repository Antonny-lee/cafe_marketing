"""Generates sample monthly sales-report xlsx files for testing the 장부
'매출 리포트 업로드' feature. Each file has a '날짜'/'매출' header pair, one row
per day of that month, with a bit of weekday/weekend variation so the
month-to-month comparison view has something interesting to show.

Usage:
    python generate_sample_sales.py
"""
import calendar
import os
import random
from datetime import date

from openpyxl import Workbook

OUT_DIR = os.path.join(os.path.dirname(__file__), "sample_sales_reports")
os.makedirs(OUT_DIR, exist_ok=True)

# (year, month, base_daily_sales) -- base trends upward month over month
MONTHS = [
    (2026, 6, 950_000),
    (2026, 7, 1_050_000),
    (2026, 8, 1_150_000),
]

random.seed(42)


def make_month_file(year: int, month: int, base: int):
    days_in_month = calendar.monthrange(year, month)[1]

    wb = Workbook()
    ws = wb.active
    ws.title = "매출"
    ws.append(["날짜", "매출"])

    for day in range(1, days_in_month + 1):
        d = date(year, month, day)
        weekday = d.weekday()  # 0=Mon ... 5=Sat, 6=Sun
        weekend_bonus = 1.25 if weekday in (4, 5) else 1.0  # 금·토 매출 up
        noise = random.uniform(0.85, 1.15)
        amount = round(base * weekend_bonus * noise / 10_000) * 10_000
        ws.append([d.strftime("%Y-%m-%d"), amount])

    filename = f"{year}-{month:02d}_매출리포트.xlsx"
    path = os.path.join(OUT_DIR, filename)
    wb.save(path)
    print(f"생성: {path}")


def main():
    for year, month, base in MONTHS:
        make_month_file(year, month, base)
    print(f"\n완료. {OUT_DIR} 폴더에서 파일을 확인하세요.")


if __name__ == "__main__":
    main()
