"""Проверка сценария защиты КР3 на живом стеке. Аргументы: путь к .env, путь к examples/."""
import json, sys, uuid, urllib.request, urllib.error

BASE = "http://localhost:8080"
env = dict(l.split("=", 1) for l in open(sys.argv[1], encoding="utf-8").read().splitlines() if "=" in l and not l.startswith("#"))
EX = sys.argv[2]
NEW_ADMIN_PASSWORD = "Hotel-Defense-2026!"
ok_count = 0


def call(method, path, body=None, token=None, headers=None, raw=None, ctype=None):
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    h = {"Accept": "application/json", **(headers or {})}
    if data is not None:
        h["Content-Type"] = ctype or "application/json"
    if token:
        h["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req) as r:
            text = r.read().decode("utf-8-sig")
            return r.status, (json.loads(text) if "json" in (r.headers.get("Content-Type") or "") and text else text), r.headers
    except urllib.error.HTTPError as e:
        text = e.read().decode()
        return e.code, (json.loads(text) if text.startswith("{") else text), e.headers


def check(name, cond, info=""):
    global ok_count
    print(("OK   " if cond else "FAIL ") + name + ("" if cond else f"  -> {info}"))
    if cond:
        ok_count += 1
    else:
        raise SystemExit(1)


def q(s):
    return urllib.parse.quote(s)


import urllib.parse

s, b, h = call("GET", "/actuator/health")
check("health UP", s == 200 and b["status"] == "UP", b)
s, b, h = call("GET", "/api/bookings")
check("401 без токена + correlation id", s == 401 and b["code"] == "UNAUTHORIZED" and h.get("X-Correlation-ID"), b)

# вход администратора (временный или уже постоянный пароль)
s, b, _ = call("POST", "/api/auth/login", {"email": env["BOOTSTRAP_ADMIN_EMAIL"], "password": env["BOOTSTRAP_ADMIN_PASSWORD"]})
if s == 200:
    admin = b["accessToken"]
    check("вход с временным паролем", b["user"]["passwordChangeRequired"], b)
    s, b, _ = call("GET", "/api/bookings", token=admin)
    check("403 PASSWORD_CHANGE_REQUIRED", s == 403 and b["code"] == "PASSWORD_CHANGE_REQUIRED", b)
    s, b, _ = call("POST", "/api/auth/change-initial-password", {"newPassword": NEW_ADMIN_PASSWORD}, admin)
    check("смена временного пароля", s == 204, b)
else:
    s, b, _ = call("POST", "/api/auth/login", {"email": env["BOOTSTRAP_ADMIN_EMAIL"], "password": NEW_ADMIN_PASSWORD})
    check("вход с постоянным паролем", s == 200, b)
    admin = b["accessToken"]

s, b, _ = call("POST", "/api/auth/login", {"email": env["BOOTSTRAP_ADMIN_EMAIL"], "password": NEW_ADMIN_PASSWORD})
refresh = b["refreshToken"]
s, b, _ = call("POST", "/api/auth/refresh", {"refreshToken": refresh})
check("refresh выдаёт новую пару", s == 200 and b["refreshToken"] != refresh, b)
s, b, _ = call("POST", "/api/auth/refresh", {"refreshToken": refresh})
check("повторный refresh отклонён", s == 401, b)
s, b, _ = call("GET", "/api/auth/me", token=admin)
check("me", s == 200 and "ADMIN" in b["roles"], b)

# данные из миграций
s, b, _ = call("GET", "/api/bookings?size=100", token=admin)
check("20 броней из V4, 19 активных", s == 200 and b["totalElements"] == 19, b.get("totalElements"))
s, b, _ = call("GET", "/api/bookings?q=" + q("архив:все") + "&size=100", token=admin)
check("архив:все = 20", b["totalElements"] == 20, b["totalElements"])
byCode = {x["code"]: x for x in b["content"]}
check("риск V4 совпадает с Java (BK-000015 CRITICAL, BK-000019 HIGH, BK-000018 LOW)",
      byCode["BK-000015"]["riskLevel"] == "CRITICAL" and byCode["BK-000019"]["riskLevel"] == "HIGH"
      and byCode["BK-000018"]["riskLevel"] == "LOW", {k: byCode[k]["riskLevel"] for k in ("BK-000015", "BK-000019", "BK-000018")})

# фильтры, сортировка, пагинация
for query, expect in [("номер:301", 3), ("тип:люкс", 5), ("статус:отменено архив:все", 3), ("предоплата:да", 10),
                      ("тег:vip", 4), ("сумма>=30000", 2), ("иванов", 4), ("риск:критический", 2),
                      ("заезд>=2026-12-01", 4), ("архив:да", 1)]:
    s, b, _ = call("GET", "/api/bookings?size=100&q=" + q(query), token=admin)
    check(f"фильтр «{query}» = {expect}", s == 200 and b["totalElements"] == expect, (s, b.get("totalElements"), b.get("message")))
s, b, _ = call("GET", "/api/bookings?size=5&page=1&sort=price,desc", token=admin)
prices = [float(x["totalPrice"]) for x in b["content"]]
check("пагинация+сортировка по сумме", s == 200 and b["page"] == 1 and len(prices) == 5 and prices == sorted(prices, reverse=True) and b["totalPages"] == 4, b)
s, b, _ = call("GET", "/api/bookings?sort=guest,asc&size=3", token=admin)
check("сортировка по гостю", s == 200, b)
s, b, _ = call("GET", "/api/bookings?size=101", token=admin)
check("size>100 -> 400", s == 400, b)
s, b, _ = call("GET", "/api/bookings?sort=password,asc", token=admin)
check("недопустимая сортировка -> 400", s == 400, b)
s, b, _ = call("GET", "/api/bookings?q=" + q("статус:потеряно"), token=admin)
check("неизвестный статус -> 400", s == 400, b)

# пользователи и роли
users = {}
for email, role in [("manager@hotel.local", "MANAGER"), ("viewer@hotel.local", "VIEWER")]:
    s, b, _ = call("POST", "/api/admin/users", {"email": email, "displayName": role.title(), "roles": [role]}, admin)
    if s == 409:
        s, page, _ = call("GET", "/api/admin/users?q=" + email, token=admin)
        s, b, _ = call("POST", f"/api/admin/users/{page['content'][0]['id']}/reset-password", token=admin)
    check(f"создан/сброшен {role} с временным паролем", s in (200, 201) and b["temporaryPassword"], b)
    s, l, _ = call("POST", "/api/auth/login", {"email": email, "password": b["temporaryPassword"]})
    tok = l["accessToken"]
    s, _, _ = call("POST", "/api/auth/change-initial-password", {"newPassword": "Strong-Hotel-2026#"}, tok)
    check(f"{role} сменил временный пароль", s == 204)
    users[role] = tok

rooms = call("GET", "/api/rooms", token=admin)[1]
room104 = next(r for r in rooms if r["roomNumber"] == 104)
room103 = next(r for r in rooms if r["roomNumber"] == 103)
guest = call("GET", "/api/guests?q=volkova", token=admin)[1]["content"][0]
new = {"guestId": guest["id"], "roomId": room104["id"], "checkIn": "2027-06-10", "checkOut": "2027-06-13",
       "guestsCount": 2, "status": "CREATED", "prepaid": False, "specialRequests": "Проверка API", "tags": ["Demo"]}
s, b, _ = call("POST", "/api/bookings", new, users["VIEWER"])
check("VIEWER не может создать бронь (403)", s == 403 and b["code"] == "ACCESS_DENIED", b)
s, b, _ = call("GET", "/api/admin/users", token=users["MANAGER"])
check("MANAGER не видит пользователей (403)", s == 403, b)
s, b, _ = call("POST", "/api/rooms", {"roomNumber": 999, "roomType": "SUITE", "capacity": 2, "pricePerNight": 100}, users["MANAGER"])
check("MANAGER не меняет справочник (403)", s == 403, b)

# CRUD и правила
s, bk, _ = call("POST", "/api/bookings", new, users["MANAGER"])
check("MANAGER создал бронь, цена и риск посчитаны", s == 201 and float(bk["totalPrice"]) == 9000 and bk["riskLevel"] == "HIGH"
      and bk["code"].startswith("BK-") and bk["tags"] == ["demo"], bk)
s, b, _ = call("POST", "/api/bookings", new, users["MANAGER"])
check("пересечение дат в том же номере -> 409", s == 409, b)
s, b, _ = call("POST", "/api/bookings", {**new, "roomId": room103["id"]}, users["MANAGER"])
check("гостей больше вместимости -> 400", s == 400, b)
s, b, _ = call("POST", "/api/bookings", {**new, "checkOut": "2027-06-01"}, users["MANAGER"])
check("выезд раньше заезда -> 400", s == 400, b)
s, b, _ = call("POST", "/api/bookings", {**new, "guestsCount": 0, "guestId": None}, users["MANAGER"])
check("Bean Validation -> 400 с полями", s == 400 and b["code"] == "VALIDATION_FAILED" and "guestId" in b["validationErrors"], b)
s, b, _ = call("GET", f"/api/bookings/{uuid.uuid4()}", token=admin)
check("нет записи -> 404", s == 404, b)

upd = {**new, "status": "CONFIRMED", "prepaid": True, "version": bk["version"]}
s, b2, _ = call("PUT", f"/api/bookings/{bk['id']}", upd, users["MANAGER"])
check("изменение: статус CONFIRMED, риск LOW, version+1", s == 200 and b2["riskLevel"] == "LOW" and b2["version"] == bk["version"] + 1, b2)
s, b, _ = call("PUT", f"/api/bookings/{bk['id']}", upd, users["MANAGER"])
check("старая version -> 409", s == 409, b)
s, b, _ = call("PUT", f"/api/bookings/{bk['id']}", {**upd, "status": "COMPLETED", "version": b2["version"]}, users["MANAGER"])
check("завершить до заезда нельзя -> 400", s == 400, b)

# заметки
s, n, _ = call("POST", f"/api/bookings/{bk['id']}/notes", {"body": "Позвонить за день до заезда", "pinned": True}, users["MANAGER"])
check("заметка создана, автор сохранён", s == 201 and n["authorName"] == "Manager", n)
s, n2, _ = call("PUT", f"/api/bookings/{bk['id']}/notes/{n['id']}", {"body": "Позвонили", "pinned": False, "version": n["version"]}, users["MANAGER"])
check("заметка изменена", s == 200 and n2["body"] == "Позвонили", n2)
s, l, _ = call("GET", f"/api/bookings/{bk['id']}/notes", token=users["VIEWER"])
check("VIEWER читает заметки", s == 200 and len(l) == 1, l)
s, _, _ = call("DELETE", f"/api/bookings/{bk['id']}/notes/{n['id']}", token=users["MANAGER"])
check("заметка удалена", s == 204)

# архив, восстановление, окончательное удаление
s, b, _ = call("DELETE", f"/api/bookings/{bk['id']}", token=users["MANAGER"])
check("архивирование", s == 200 and b["archived"], b)
s, b, _ = call("DELETE", f"/api/bookings/{bk['id']}/permanent", token=users["MANAGER"], headers={"X-Confirm-Delete": bk["code"]})
check("MANAGER не удаляет окончательно (403)", s == 403, b)
s, b, _ = call("POST", f"/api/bookings/{bk['id']}/restore", token=users["MANAGER"])
check("восстановление", s == 200 and not b["archived"], b)
call("DELETE", f"/api/bookings/{bk['id']}", token=admin)
s, b, _ = call("DELETE", f"/api/bookings/{bk['id']}/permanent", token=admin)
check("удаление без подтверждения -> 400", s == 400, b)
s, b, _ = call("DELETE", f"/api/bookings/{bk['id']}/permanent", token=admin, headers={"X-Confirm-Delete": bk["code"]})
check("ADMIN удалил окончательно", s == 204, b)

# массовая операция с частичными ошибками
ids = [byCode[c]["id"] for c in ("BK-000007", "BK-000008", "BK-000001")]  # CREATED, CREATED, COMPLETED
s, b, _ = call("POST", "/api/bookings/bulk", {"ids": ids + [str(uuid.uuid4())], "action": "SET_STATUS", "status": "CONFIRMED"}, users["MANAGER"])
check("bulk SET_STATUS: 2 успеха, 2 ошибки", s == 200 and b["succeeded"] == 2 and len(b["failures"]) == 2, b)
s, b, _ = call("POST", "/api/bookings/bulk", {"ids": ids, "action": "ADD_TAG", "tag": "checked"}, users["MANAGER"])
check("bulk ADD_TAG", s == 200 and b["succeeded"] == 3, b)
s, b, _ = call("POST", "/api/bookings/bulk", {"ids": ids, "action": "REMOVE_TAG", "tag": "checked"}, users["MANAGER"])
check("bulk REMOVE_TAG", s == 200 and b["succeeded"] == 3, b)

# CSV


def multipart(path):
    content = open(path, "rb").read()
    bnd = "----hotel" + uuid.uuid4().hex
    body = (f"--{bnd}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"f.csv\"\r\nContent-Type: text/csv\r\n\r\n").encode() \
        + content + f"\r\n--{bnd}--\r\n".encode()
    return body, "multipart/form-data; boundary=" + bnd


body, ct = multipart(EX + "/bookings-import.csv")
s, b, _ = call("POST", "/api/bookings/import/preview?mode=CREATE_ONLY", raw=body, ctype=ct, token=users["MANAGER"])
check("CSV preview: 7 строк, 3 корректных, 4 ошибочных", s == 200 and b["totalRows"] == 7 and b["validRows"] == 3 and b["invalidRows"] == 4, b)
s, b, _ = call("POST", "/api/bookings/import/apply?mode=CREATE_ONLY", raw=body, ctype=ct, token=users["MANAGER"])
check("CSV apply: создано 3, пропущено 4", s == 200 and b["created"] == 3 and b["skipped"] == 4, b)
s, b, _ = call("POST", "/api/bookings/import/preview?mode=CREATE_ONLY", raw=body, ctype=ct, token=users["MANAGER"])
# коды уже заняты, а строка без кода теперь пересекается с только что импортированной бронью
check("повторный preview: всё отклонено", s == 200 and b["validRows"] == 0 and b["invalidRows"] == 7, b)
body2, ct2 = multipart(EX + "/bookings-update.csv")
s, b, _ = call("POST", "/api/bookings/import/apply?mode=UPSERT_BY_CODE", raw=body2, ctype=ct2, token=users["MANAGER"])
check("CSV upsert: 1 создана, 2 обновлены", s == 200 and b["created"] == 1 and b["updated"] == 2, b)
s, text, hdr = call("GET", "/api/bookings/export?q=" + q("статус:подтверждено"), token=users["MANAGER"])
lines = [l for l in text.splitlines() if l]
check("CSV export по фильтру", s == 200 and lines[0].startswith("code,guestName") and all("CONFIRMED" in l for l in lines[1:]), lines[:2])
s, b, _ = call("GET", "/api/bookings/export", token=users["VIEWER"])
check("VIEWER не экспортирует (403)", s == 403, b)

# справочник
s, r, _ = call("PATCH", f"/api/rooms/{room103['id']}/active?active=false&version={room103['version']}", token=admin)
check("номер отключён", s == 200 and not r["active"], r)
s, b, _ = call("POST", "/api/bookings", {**new, "roomId": room103["id"], "guestsCount": 1, "checkIn": "2027-08-01", "checkOut": "2027-08-02"}, users["MANAGER"])
check("на отключённый номер бронь не создаётся", s == 400, b)
s, r, _ = call("PATCH", f"/api/rooms/{room103['id']}/active?active=true&version={r['version']}", token=admin)
check("номер включён обратно", s == 200 and r["active"], r)

# аналитика, аудит, swagger
s, a, _ = call("GET", "/api/analytics/summary", token=users["VIEWER"])
check("аналитика", s == 200 and a["total"] == a["active"] + a["archived"] and a["byRoom"] and a["byRisk"], a)
s, au, _ = call("GET", "/api/admin/audit?q=BOOKING&size=5&sort=occurred,desc", token=admin)
check("аудит: поиск, сортировка, пагинация", s == 200 and au["totalElements"] > 5 and au["content"][0]["correlationId"], au.get("totalElements"))
s, b, _ = call("GET", "/api/admin/audit", token=users["MANAGER"])
check("аудит только ADMIN", s == 403, b)
s, b, _ = call("GET", "/v3/api-docs", token=admin)
check("OpenAPI для ADMIN", s == 200 and "/api/bookings" in b["paths"], s)
s, b, _ = call("GET", "/v3/api-docs", token=users["MANAGER"])
check("OpenAPI для MANAGER закрыт", s == 403, s)

# блокировка после 5 неудачных попыток
for i in range(5):
    call("POST", "/api/auth/login", {"email": "viewer@hotel.local", "password": "Wrong-Password-1!"})
s, b, _ = call("POST", "/api/auth/login", {"email": "viewer@hotel.local", "password": "Strong-Hotel-2026#"})
check("после 5 ошибок вход заблокирован даже с верным паролем", s == 401, b)
s, u, _ = call("GET", "/api/admin/users?q=viewer", token=admin)
check("блокировка видна администратору", u["content"][0]["lockedUntil"] is not None, u)
print(f"\nВСЕ ПРОВЕРКИ ПРОЙДЕНЫ: {ok_count}")
