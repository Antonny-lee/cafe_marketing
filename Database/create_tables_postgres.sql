-- Naver Map cafe-crawling data + market report + ledger -- Postgres (Supabase) DDL
-- Oracle 버전(create_tables.sql, create_ledger_tables.py, add_insight_tables.py)을
-- 기준으로 변환. market_report_series / market_report_breakdown은 원래 DDL 스크립트가
-- 없어서 엔티티(MarketReportSeries.java, MarketReportBreakdown.java) 기준으로 새로 작성함.
--
-- 실행: Session Pooler로 접속해서 이 파일 전체를 한 번에 실행하면 됨 (Supabase SQL
-- Editor에 붙여넣거나, psql -f create_tables_postgres.sql).

-- ===== 초기화 (역순으로 DROP) =====
DROP TABLE IF EXISTS review_insight_comparison CASCADE;
DROP TABLE IF EXISTS review_insight_item CASCADE;
DROP TABLE IF EXISTS review_insight CASCADE;
DROP TABLE IF EXISTS expenses CASCADE;
DROP TABLE IF EXISTS fixed_costs CASCADE;
DROP TABLE IF EXISTS daily_sales CASCADE;
DROP TABLE IF EXISTS business CASCADE;
DROP TABLE IF EXISTS app_user CASCADE;
DROP TABLE IF EXISTS market_report_breakdown CASCADE;
DROP TABLE IF EXISTS market_report_series CASCADE;
DROP TABLE IF EXISTS market_report_metric CASCADE;
DROP TABLE IF EXISTS market_report CASCADE;
DROP TABLE IF EXISTS ai_briefing CASCADE;
DROP TABLE IF EXISTS store_info_items CASCADE;
DROP TABLE IF EXISTS store_intro CASCADE;
DROP TABLE IF EXISTS review_category_tags CASCADE;
DROP TABLE IF EXISTS reviews CASCADE;
DROP TABLE IF EXISTS menu CASCADE;
DROP TABLE IF EXISTS stores CASCADE;

-- ===== 크롤링 데이터 =====
CREATE TABLE stores (
    store_id        VARCHAR(10)   PRIMARY KEY,
    name            VARCHAR(200),
    address         VARCHAR(500),
    subway_info     VARCHAR(200),
    business_hours  TEXT,
    naver_id        VARCHAR(20) UNIQUE,
    lat             NUMERIC(9,6),
    lng             NUMERIC(9,6)
);

CREATE TABLE menu (
    menu_id         VARCHAR(10)   PRIMARY KEY,
    store_id        VARCHAR(10)   NOT NULL REFERENCES stores(store_id),
    menu_name       VARCHAR(300),
    price_krw       BIGINT,
    price_note      VARCHAR(50)
);
CREATE INDEX idx_menu_store ON menu(store_id);

CREATE TABLE reviews (
    review_id         VARCHAR(20)   PRIMARY KEY,
    store_id          VARCHAR(10)   NOT NULL REFERENCES stores(store_id),
    reviewer_id       VARCHAR(50),
    rating            NUMERIC(2,1),
    visit_time        VARCHAR(20),
    wait_time         VARCHAR(100),
    tags              VARCHAR(1000),
    review_text       TEXT,
    review_date_text  VARCHAR(20),
    review_date       DATE,
    visit_count_text  VARCHAR(30),
    visit_count       BIGINT
);
CREATE INDEX idx_reviews_store ON reviews(store_id);
CREATE INDEX idx_reviews_date ON reviews(review_date);

CREATE TABLE review_category_tags (
    store_id                   VARCHAR(10)  NOT NULL REFERENCES stores(store_id),
    tag_text                   VARCHAR(200) NOT NULL,
    mention_count              BIGINT,
    tag_category               VARCHAR(50),
    store_total_participants   BIGINT,
    PRIMARY KEY (store_id, tag_text)
);

CREATE TABLE store_intro (
    store_id    VARCHAR(10) PRIMARY KEY REFERENCES stores(store_id),
    intro_text  TEXT
);

CREATE TABLE store_info_items (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id    VARCHAR(10)  NOT NULL REFERENCES stores(store_id),
    section     VARCHAR(50)  NOT NULL,
    item_text   VARCHAR(500),
    detail      VARCHAR(1000)
);
CREATE INDEX idx_info_store ON store_info_items(store_id);
CREATE INDEX idx_info_section ON store_info_items(section);

CREATE TABLE ai_briefing (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id    VARCHAR(10) NOT NULL REFERENCES stores(store_id),
    sentence    VARCHAR(1000)
);
CREATE INDEX idx_briefing_store ON ai_briefing(store_id);

-- ===== 상권 리포트 =====
CREATE TABLE market_report (
    report_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    location     VARCHAR(300),
    industry     VARCHAR(100),
    quarter      VARCHAR(20),
    report_date  DATE,
    raw_text     TEXT,
    opinion_text TEXT
);

CREATE TABLE market_report_metric (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    report_id    BIGINT NOT NULL REFERENCES market_report(report_id),
    metric_name  VARCHAR(200),
    value        VARCHAR(300),
    unit         VARCHAR(50),
    qoq_change   VARCHAR(100),
    yoy_change   VARCHAR(100),
    note         VARCHAR(1000)
);
CREATE INDEX idx_metric_report ON market_report_metric(report_id);

-- 원래 DDL 스크립트가 없던 테이블 (엔티티 기준으로 재구성)
CREATE TABLE market_report_series (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    report_id     BIGINT NOT NULL REFERENCES market_report(report_id),
    metric_name   VARCHAR(50),
    quarter_label VARCHAR(20),
    mine_value    DOUBLE PRECISION,
    gu_value      DOUBLE PRECISION,
    seoul_value   DOUBLE PRECISION
);
CREATE INDEX idx_series_report ON market_report_series(report_id);

CREATE TABLE market_report_breakdown (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    report_id  BIGINT NOT NULL REFERENCES market_report(report_id),
    category   VARCHAR(50),
    label      VARCHAR(50),
    value      DOUBLE PRECISION
);
CREATE INDEX idx_breakdown_report ON market_report_breakdown(report_id);

-- ===== 앱 사용자 / 사업자 인증 =====
CREATE TABLE app_user (
    user_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(200) NOT NULL UNIQUE,
    password_hash VARCHAR(200) NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE business (
    biz_reg_no       VARCHAR(10)  PRIMARY KEY,
    owner_user_id    BIGINT REFERENCES app_user(user_id),
    store_id         VARCHAR(10)  REFERENCES stores(store_id),
    ceo_name         VARCHAR(100),
    open_date        VARCHAR(8),
    biz_name         VARCHAR(200),
    phone            VARCHAR(20),
    verified         CHAR(1)      DEFAULT 'N',
    biz_status       VARCHAR(20),
    biz_status_code  VARCHAR(5),
    tax_type         VARCHAR(50),
    tax_type_code    VARCHAR(5),
    verified_at      TIMESTAMP
);
CREATE INDEX idx_business_owner ON business(owner_user_id);

-- ===== 장부(가계부) — 사장님이 직접 입력, 절대 재생성 불가한 데이터 =====
CREATE TABLE daily_sales (
    store_id      VARCHAR(10) NOT NULL REFERENCES stores(store_id),
    sale_date     DATE NOT NULL,
    amount        BIGINT NOT NULL,
    source        VARCHAR(20) DEFAULT 'MANUAL',
    uploaded_file VARCHAR(300),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (store_id, sale_date)
);

CREATE TABLE fixed_costs (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id       VARCHAR(10) NOT NULL REFERENCES stores(store_id),
    category       VARCHAR(50) NOT NULL,
    vendor         VARCHAR(200),
    amount         BIGINT NOT NULL,
    payment_method VARCHAR(20),
    day_of_month   INTEGER NOT NULL,
    memo           VARCHAR(500),
    active         CHAR(1) DEFAULT 'Y',
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_fixedcost_store ON fixed_costs(store_id);

CREATE TABLE expenses (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id       VARCHAR(10) NOT NULL REFERENCES stores(store_id),
    category       VARCHAR(50) NOT NULL,
    vendor         VARCHAR(200),
    amount         BIGINT NOT NULL,
    payment_method VARCHAR(20),
    memo           VARCHAR(500),
    expense_date   DATE NOT NULL,
    is_fixed_cost  CHAR(1) DEFAULT 'N',
    fixed_cost_id  BIGINT REFERENCES fixed_costs(id),
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_expenses_store ON expenses(store_id);
CREATE INDEX idx_expenses_date ON expenses(expense_date);

-- ===== 리뷰 AI 분석 캐시 =====
CREATE TABLE review_insight (
    store_id        VARCHAR(10) PRIMARY KEY REFERENCES stores(store_id),
    positive_ratio  NUMERIC(5,2),
    negative_ratio  NUMERIC(5,2),
    analyzed_count  BIGINT,
    word_summary    VARCHAR(2000),
    analyzed_at     TIMESTAMP
);

CREATE TABLE review_insight_item (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id     VARCHAR(10) NOT NULL REFERENCES review_insight(store_id),
    quote        VARCHAR(1000),
    suggestion   VARCHAR(500)
);
CREATE INDEX idx_insight_item_store ON review_insight_item(store_id);

CREATE TABLE review_insight_comparison (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id         VARCHAR(10) NOT NULL REFERENCES stores(store_id),
    rival_store_id   VARCHAR(10) NOT NULL REFERENCES stores(store_id),
    strength         VARCHAR(2000),
    difference       VARCHAR(2000),
    analyzed_at      TIMESTAMP,
    CONSTRAINT uq_insight_comparison UNIQUE (store_id, rival_store_id)
);
CREATE INDEX idx_insight_comp_store ON review_insight_comparison(store_id);
