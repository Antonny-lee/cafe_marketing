"""Adds review_insight / review_insight_item tables (OpenAI 감정분석 결과 캐시)."""
import os

import oracledb
from dotenv import load_dotenv

load_dotenv()

DDL = [
    "BEGIN EXECUTE IMMEDIATE 'DROP TABLE review_insight_item'; EXCEPTION WHEN OTHERS THEN NULL; END;",
    "BEGIN EXECUTE IMMEDIATE 'DROP TABLE review_insight'; EXCEPTION WHEN OTHERS THEN NULL; END;",
    """CREATE TABLE review_insight (
        store_id         VARCHAR2(10) PRIMARY KEY REFERENCES stores(store_id),
        positive_ratio    NUMBER(5,2),
        negative_ratio    NUMBER(5,2),
        analyzed_count    NUMBER,
        word_summary      VARCHAR2(2000),
        analyzed_at       TIMESTAMP
    )""",
    """CREATE TABLE review_insight_item (
        id           NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
        store_id     VARCHAR2(10) NOT NULL REFERENCES review_insight(store_id),
        quote        VARCHAR2(1000),
        suggestion   VARCHAR2(500)
    )""",
    "CREATE INDEX idx_insight_item_store ON review_insight_item(store_id)",
    "BEGIN EXECUTE IMMEDIATE 'DROP TABLE review_insight_comparison'; EXCEPTION WHEN OTHERS THEN NULL; END;",
    """CREATE TABLE review_insight_comparison (
        id               NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
        store_id         VARCHAR2(10) NOT NULL REFERENCES stores(store_id),
        rival_store_id   VARCHAR2(10) NOT NULL REFERENCES stores(store_id),
        strength         VARCHAR2(2000),
        difference       VARCHAR2(2000),
        analyzed_at      TIMESTAMP,
        CONSTRAINT uq_insight_comparison UNIQUE (store_id, rival_store_id)
    )""",
    "CREATE INDEX idx_insight_comp_store ON review_insight_comparison(store_id)",
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
    print("review_insight / review_insight_item 생성 완료.")


if __name__ == "__main__":
    main()
