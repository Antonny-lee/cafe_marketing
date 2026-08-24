"""Adds lat/lng columns to stores (idempotent) and backfills them from the
lat/lng columns add_coordinates.py added to cafes.xlsx."""
import os

import oracledb
from dotenv import load_dotenv
from openpyxl import load_workbook

load_dotenv()

CAFES_XLSX = os.path.join(os.path.dirname(__file__), "..", "Web crawling", "Home", "cafes.xlsx")


def connect():
    dsn = oracledb.makedsn(
        os.environ["ORACLE_HOST"],
        int(os.environ.get("ORACLE_PORT", 1521)),
        service_name=os.environ["ORACLE_SERVICE_NAME"],
    )
    return oracledb.connect(user=os.environ["ORACLE_USER"], password=os.environ["ORACLE_PASSWORD"], dsn=dsn)


def main():
    conn = connect()
    cur = conn.cursor()

    try:
        cur.execute("ALTER TABLE stores ADD (lat NUMBER(9,6), lng NUMBER(9,6))")
        print("lat/lng 컬럼 추가됨.")
    except oracledb.DatabaseError as e:
        if "ORA-01430" in str(e):
            print("lat/lng 컬럼이 이미 있음, 건너뜀.")
        else:
            raise

    wb = load_workbook(CAFES_XLSX, read_only=True, data_only=True)
    ws = wb.active
    headers = [c.value for c in next(ws.iter_rows(min_row=1, max_row=1))]
    lat_idx, lng_idx = headers.index("lat"), headers.index("lng")

    data = []
    for row in ws.iter_rows(min_row=2, values_only=True):
        store_id, lat, lng = row[0], row[lat_idx], row[lng_idx]
        if lat and lng:
            data.append((float(lat), float(lng), store_id))

    cur.executemany("UPDATE stores SET lat = :1, lng = :2 WHERE store_id = :3", data)
    conn.commit()
    print(f"좌표 {len(data)}건 반영 완료.")

    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
