import json
import re
import sys
import time

from openpyxl import Workbook
from openpyxl.styles import Alignment
from scrapling.fetchers import Fetcher

SEARCH_URL = (
    "https://pcmap.place.naver.com/restaurant/list"
    "?query=%ED%99%8D%EB%8C%80%EC%9E%85%EA%B5%AC%EC%97%AD%20%EC%B9%B4%ED%8E%98"
    "&x=126.941811&y=37.564092&clientX=126.941811&clientY=37.564092"
    "&display=70&locale=ko&svcName=map_pcv5"
    "&searchText=%ED%99%8D%EB%8C%80%EC%9E%85%EA%B5%AC%EC%97%AD%20%EC%B9%B4%ED%8E%98"
)
DETAIL_URL_TMPL = "https://m.place.naver.com/place/{pid}/home"
OUTPUT_XLSX = "cafes.xlsx"
REQUEST_DELAY_SEC = 0.6


def extract_apollo_state(html: str):
    marker = "window.__APOLLO_STATE__ = "
    idx = html.find(marker)
    if idx == -1:
        return None
    start = idx + len(marker)
    i = start
    depth = 0
    in_str = False
    esc = False
    while i < len(html):
        c = html[i]
        if in_str:
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == '"':
                in_str = False
        else:
            if c == '"':
                in_str = True
            elif c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    i += 1
                    break
        i += 1
    return json.loads(html[start:i])


def deref(apollo: dict, obj):
    if isinstance(obj, dict) and "__ref" in obj:
        return apollo.get(obj["__ref"])
    return obj


def get_search_list(url: str):
    page = Fetcher.get(url, stealthy_headers=True)
    apollo = extract_apollo_state(page.body.decode("utf-8"))
    root = apollo.get("ROOT_QUERY", {})
    place_list_key = None
    for k in root:
        if k.startswith("placeList(") and '"display":70' in k:
            place_list_key = k
            break
    if place_list_key is None:
        raise RuntimeError("placeList(display:70) not found in search page")

    items = root[place_list_key]["businesses"]["items"]
    results = []
    for ref in items:
        item = deref(apollo, ref)
        if item is None:
            continue
        results.append({"id": item["id"], "name": item["name"]})
    return results


def find_place_detail(apollo: dict, pid: str):
    root = apollo.get("ROOT_QUERY", {})
    for k, v in root.items():
        if k.startswith("placeDetail(") and f'"id":"{pid}"' in k:
            return v
    for k, v in root.items():
        if k.startswith("placeDetail("):
            return v
    return None


def get_business_hours_text(place_detail: dict):
    key = None
    for k in place_detail:
        if k.startswith("newBusinessHours("):
            key = k
            break
    if not key or not place_detail[key]:
        return ""

    entries = place_detail[key]
    store_entry = next((e for e in entries if e.get("name") == "매장"), entries[0])

    lines = []
    for wh in store_entry.get("businessHours") or []:
        day = re.sub(r"\(.*?\)", "", wh.get("day", "")).strip()
        bh = wh.get("businessHours")
        if bh:
            lines.append(f"{day} {bh['start']} - {bh['end']}")
        elif wh.get("description"):
            lines.append(f"{day} {wh['description']}")
        else:
            lines.append(f"{day} 정보없음")
    if not lines and store_entry.get("freeText"):
        return store_entry["freeText"]
    return "\n".join(lines)


def get_subway_text(apollo: dict, place_detail: dict):
    stations = place_detail.get("subwayStations") or []
    best = None
    for st in stations:
        info = deref(apollo, st.get("station"))
        if not info or info.get("walkingDistance") is None:
            continue
        if best is None or info["walkingDistance"] < best["walkingDistance"]:
            best = info
    if not best:
        return ""
    return f"{best['displayName']} {best['nearestExit']}번 출구에서 {best['walkingDistance']}m"


def get_place_detail_row(pid: str):
    url = DETAIL_URL_TMPL.format(pid=pid)
    page = Fetcher.get(url, stealthy_headers=True)
    apollo = extract_apollo_state(page.body.decode("utf-8"))
    if apollo is None:
        return None

    base = apollo.get(f"PlaceDetailBase:{pid}")
    place_detail = find_place_detail(apollo, pid)
    if base is None or place_detail is None:
        return None

    coord = base.get("coordinate") or {}

    return {
        "가게이름": base.get("name", ""),
        "위치": base.get("roadAddress") or base.get("address") or "",
        "간편위치": get_subway_text(apollo, place_detail),
        "영업시간": get_business_hours_text(place_detail),
        "naver_id": pid,
        "lat": coord.get("y"),
        "lng": coord.get("x"),
    }


def save_to_excel(rows: list[dict], path: str):
    wb = Workbook()
    ws = wb.active
    ws.title = "카페목록"
    headers = ["store_id", "가게이름", "위치", "간편위치", "영업시간", "naver_id"]
    ws.append(headers)

    wrap = Alignment(wrap_text=True, vertical="top")
    for i, row in enumerate(rows, 1):
        store_id = f"S{i:03d}"
        ws.append([store_id] + [row.get(h, "") for h in headers[1:]])

    for col_letter, width in zip("ABCDEF", [10, 22, 34, 30, 24, 14]):
        ws.column_dimensions[col_letter].width = width
    for r in ws.iter_rows(min_row=2):
        for cell in r:
            cell.alignment = wrap

    wb.save(path)


def main():
    limit = int(sys.argv[1]) if len(sys.argv) > 1 else 5

    print("검색 목록 가져오는 중...")
    listing = get_search_list(SEARCH_URL)
    print(f"목록에서 총 {len(listing)}개 카페 발견. {limit}개 처리합니다.")

    rows = []
    for i, item in enumerate(listing[:limit], 1):
        pid = item["id"]
        print(f"[{i}/{limit}] {item['name']} ({pid}) 상세정보 가져오는 중...")
        try:
            row = get_place_detail_row(pid)
        except Exception as e:
            print(f"  실패: {e}")
            row = None
        if row:
            rows.append(row)
        time.sleep(REQUEST_DELAY_SEC)

    save_to_excel(rows, OUTPUT_XLSX)
    print(f"완료: {len(rows)}개 저장 -> {OUTPUT_XLSX}")


if __name__ == "__main__":
    main()
