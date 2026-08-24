-- Naver Map cafe-crawling data + market report -- Oracle DDL
-- Run once (or after DROP) before load_data.py.

BEGIN EXECUTE IMMEDIATE 'DROP TABLE ai_briefing'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP TABLE store_info_items'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP TABLE store_intro'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP TABLE review_category_tags'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP TABLE reviews'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP TABLE menu'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP TABLE market_report_metric'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP TABLE market_report'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP TABLE stores'; EXCEPTION WHEN OTHERS THEN NULL; END;
/

CREATE TABLE stores (
    store_id        VARCHAR2(10)   PRIMARY KEY,
    name            VARCHAR2(200),
    address         VARCHAR2(500),
    subway_info     VARCHAR2(200),
    business_hours  CLOB,
    naver_id        VARCHAR2(20) UNIQUE,
    lat             NUMBER(9,6),
    lng             NUMBER(9,6)
);

CREATE TABLE menu (
    menu_id         VARCHAR2(10)   PRIMARY KEY,
    store_id        VARCHAR2(10)   NOT NULL REFERENCES stores(store_id),
    menu_name       VARCHAR2(300),
    price_krw       NUMBER,
    price_note      VARCHAR2(50)
);
CREATE INDEX idx_menu_store ON menu(store_id);

CREATE TABLE reviews (
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
);
CREATE INDEX idx_reviews_store ON reviews(store_id);
CREATE INDEX idx_reviews_date ON reviews(review_date);

CREATE TABLE review_category_tags (
    store_id                   VARCHAR2(10)  NOT NULL REFERENCES stores(store_id),
    tag_text                   VARCHAR2(200) NOT NULL,
    mention_count              NUMBER,
    tag_category               VARCHAR2(50),
    store_total_participants   NUMBER,
    PRIMARY KEY (store_id, tag_text)
);

CREATE TABLE store_intro (
    store_id    VARCHAR2(10) PRIMARY KEY REFERENCES stores(store_id),
    intro_text  CLOB
);

-- Unifies the 7 non-intro Info.xlsx sheets (편의시설 및 서비스 / 노키즈존 /
-- 반려동물 동반 / 주차 / 좌석.공간 / 결제수단 / SNS) into one table via the
-- `section` column, so a newly-discovered section needs no schema change.
CREATE TABLE store_info_items (
    id          NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id    VARCHAR2(10)  NOT NULL REFERENCES stores(store_id),
    section     VARCHAR2(50)  NOT NULL,
    item_text   VARCHAR2(500),
    detail      VARCHAR2(1000)
);
CREATE INDEX idx_info_store ON store_info_items(store_id);
CREATE INDEX idx_info_section ON store_info_items(section);

CREATE TABLE ai_briefing (
    id          NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id    VARCHAR2(10) NOT NULL REFERENCES stores(store_id),
    sentence    VARCHAR2(1000)
);
CREATE INDEX idx_briefing_store ON ai_briefing(store_id);

-- report.docx (Seoul Credit Guarantee Foundation trade-area report) -- not
-- tied to any single store_id, since it's an area/industry-level report.
CREATE TABLE market_report (
    report_id    NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    location     VARCHAR2(300),
    industry     VARCHAR2(100),
    quarter      VARCHAR2(20),
    report_date  DATE,
    raw_text     CLOB
);

CREATE TABLE market_report_metric (
    id           NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    report_id    NUMBER NOT NULL REFERENCES market_report(report_id),
    metric_name  VARCHAR2(200),
    value        VARCHAR2(300),
    unit         VARCHAR2(50),
    qoq_change   VARCHAR2(100),
    yoy_change   VARCHAR2(100),
    note         VARCHAR2(1000)
);
CREATE INDEX idx_metric_report ON market_report_metric(report_id);

-- 앱 사용자 계정
BEGIN EXECUTE IMMEDIATE 'DROP TABLE app_user'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
CREATE TABLE app_user (
    user_id       NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR2(200) NOT NULL UNIQUE,
    password_hash VARCHAR2(200) NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 사업자등록 인증 (공공데이터포털 국세청_사업자등록정보 진위확인 및 상태조회 서비스)
-- 앱에서 매장을 등록하는 사장님 본인의 사업자 정보. 크롤링한 stores 테이블과는 무관.
BEGIN EXECUTE IMMEDIATE 'DROP TABLE business'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
CREATE TABLE business (
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
);
CREATE INDEX idx_business_owner ON business(owner_user_id);

COMMIT;
