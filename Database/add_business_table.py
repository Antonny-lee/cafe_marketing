"""Adds just the `business` table (사업자 인증 정보), without touching the other
9 tables that load_data.py already populated. Run once."""
import os

import oracledb
from dotenv import load_dotenv

load_dotenv()

DDL = [
    "BEGIN EXECUTE IMMEDIATE 'DROP TABLE business'; EXCEPTION WHEN OTHERS THEN NULL; END;",
    """CREATE TABLE business (
        biz_reg_no       VARCHAR2(10)  PRIMARY KEY,
        ceo_name         VARCHAR2(100),
        open_date        VARCHAR2(8),
        biz_name         VARCHAR2(200),
        phone            VARCHAR2(20),
        verified         CHAR(1)       DEFAULT 'N',
        biz_status       VARCHAR2(20),
        biz_status_code  VARCHAR2(5),
        tax_type         VARCHAR2(50),
        tax_type_code    VARCHAR2(5),
        verified_at      TIMESTAMP
    )""",
]


def main():
    dsn = oracledb.makedsn(
        os.environ["ORACLE_HOST"],
        int(os.environ.get("ORACLE_PORT", 1521)),
        service_name=os.environ["ORACLE_SERVICE_NAME"],
    )
    conn = oracledb.connect(user=os.environ["ORACLE_USER"], password=os.environ["ORACLE_PASSWORD"], dsn=dsn)
    cur = conn.cursor()
    for stmt in DDL:
        cur.execute(stmt)
    conn.commit()
    cur.close()
    conn.close()
    print("business 테이블 생성 완료.")


if __name__ == "__main__":
    main()
