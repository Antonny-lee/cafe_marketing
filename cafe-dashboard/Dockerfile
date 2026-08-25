# ==============================
# 1. Spring Boot Build
# ==============================
FROM eclipse-temurin:21-jdk AS build

WORKDIR /src/cafe-dashboard

COPY cafe-dashboard/ .

RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests


# ==============================
# 2. Runtime
# Java + Python + Crawler
# ==============================
FROM eclipse-temurin:21-jre

USER root

# Python 환경 
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       python3 \
       python3-pip \
       python3-venv \
       ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Python 가상환경
RUN python3 -m venv /opt/venv

ENV PATH="/opt/venv/bin:${PATH}"

# 크롤러 Python 라이브러리
COPY crawler-requirements.txt /tmp/crawler-requirements.txt

RUN pip install --no-cache-dir -r /tmp/crawler-requirements.txt

# Scrapling browser / 시스템 의존성 설치
RUN scrapling install


# ==============================
# 3. Application Files
# ==============================

WORKDIR /app

# Spring Boot
RUN mkdir -p /app/cafe-dashboard

COPY --from=build \
     /src/cafe-dashboard/target/cafe-dashboard-0.0.1-SNAPSHOT.jar \
     /app/cafe-dashboard/app.jar

# Python crawler 전체
COPY ["Web crawling", "/app/Web crawling"]

# DB 갱신 Python
COPY Database /app/Database


# 중요:
# CrawlRefreshService가 user.dir의 .. 을 기준으로
# Web crawling / Database를 찾으므로
# 실행 위치를 cafe-dashboard로 맞춘다.
WORKDIR /app/cafe-dashboard

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]