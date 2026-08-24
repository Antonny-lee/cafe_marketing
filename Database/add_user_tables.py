"""Adds app_user table and links business.owner_user_id -> app_user.user_id.
Safe to run: business table so far only ever received rows from valid NTS
verifications during testing (none succeeded yet), so recreating it is fine."""
import os

import oracledb
from dotenv import load_dotenv

load_dotenv()

DDL = [
    "BEGIN EXECUTE IMMEDIATE 'DROP TABLE business'; EXCEPTION WHEN OTHERS THEN NULL; END;",
    "BEGIN EXECUTE IMMEDIATE 'DROP TABLE app_user'; EXCEPTION WHEN OTHERS THEN NULL; END;",
    """CREATE TABLE app_user (
        user_id       NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
        email         VARCHAR2(200) NOT NULL UNIQUE,
        password_hash VARCHAR2(200) NOT NULL,
        created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )""",
    """CREATE TABLE business (
        biz_reg_no       VARCHAR2(10)  PRIMARY KEY,
        owner_user_id    NUMBER REFERENCES app_user(user_id),
        store_id         VARCHAR2(10)  REFERENCES stores(store_id),
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
    "CREATE INDEX idx_business_owner ON business(owner_user_id)",
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
    print("app_user / business(owner_user_id) 반영 완료.")


if __name__ == "__main__":
    main()
