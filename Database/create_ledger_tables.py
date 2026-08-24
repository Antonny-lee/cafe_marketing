"""Creates the 장부(가계부) tables: fixed_costs, expenses, daily_sales.

Separate from load_data.py on purpose -- load_data.py drops and rebuilds the
whole crawled dataset from xlsx every run, but ledger data is entered by
store owners through the app and must never be wiped by a re-crawl.

Usage:
    python create_ledger_tables.py
"""
import os

import oracledb
from dotenv import load_dotenv

load_dotenv()

DDL_STATEMENTS = [
    "BEGIN EXECUTE IMMEDIATE 'DROP TABLE expenses'; EXCEPTION WHEN OTHERS THEN NULL; END;",
    "BEGIN EXECUTE IMMEDIATE 'DROP TABLE fixed_costs'; EXCEPTION WHEN OTHERS THEN NULL; END;",
    "BEGIN EXECUTE IMMEDIATE 'DROP TABLE daily_sales'; EXCEPTION WHEN OTHERS THEN NULL; END;",
    """CREATE TABLE daily_sales (
        store_id      VARCHAR2(10) NOT NULL REFERENCES stores(store_id),
        sale_date     DATE NOT NULL,
        amount        NUMBER NOT NULL,
        source        VARCHAR2(20) DEFAULT 'MANUAL',
        uploaded_file VARCHAR2(300),
        created_at    TIMESTAMP DEFAULT SYSTIMESTAMP,
        PRIMARY KEY (store_id, sale_date)
    )""",
    """CREATE TABLE fixed_costs (
        id             NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
        store_id       VARCHAR2(10) NOT NULL REFERENCES stores(store_id),
        category       VARCHAR2(50) NOT NULL,
        vendor         VARCHAR2(200),
        amount         NUMBER NOT NULL,
        payment_method VARCHAR2(20),
        day_of_month   NUMBER NOT NULL,
        memo           VARCHAR2(500),
        active         CHAR(1) DEFAULT 'Y',
        created_at     TIMESTAMP DEFAULT SYSTIMESTAMP
    )""",
    "CREATE INDEX idx_fixedcost_store ON fixed_costs(store_id)",
    """CREATE TABLE expenses (
        id             NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
        store_id       VARCHAR2(10) NOT NULL REFERENCES stores(store_id),
        category       VARCHAR2(50) NOT NULL,
        vendor         VARCHAR2(200),
        amount         NUMBER NOT NULL,
        payment_method VARCHAR2(20),
        memo           VARCHAR2(500),
        expense_date   DATE NOT NULL,
        is_fixed_cost  CHAR(1) DEFAULT 'N',
        fixed_cost_id  NUMBER REFERENCES fixed_costs(id),
        created_at     TIMESTAMP DEFAULT SYSTIMESTAMP
    )""",
    "CREATE INDEX idx_expenses_store ON expenses(store_id)",
    "CREATE INDEX idx_expenses_date ON expenses(expense_date)",
]


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
        for stmt in DDL_STATEMENTS:
            cur.execute(stmt)
        conn.commit()
        print("완료: daily_sales, fixed_costs, expenses 테이블 생성됨")
    finally:
        cur.close()
        conn.close()


if __name__ == "__main__":
    main()
