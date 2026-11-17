# Сервер «media player» (Ktor)

Серверная часть приложения: каталог треков, лайки, комментарии, плейлисты (QR-ссылка, импорт),
персональные рекомендации. Контракт — `../openapi.yaml`, описание — `../docs/08-backend.md`.

Внешние сервисы подключаются ключами из `.env` (в приложении ключей нет):

| Переменная | Что включает | Без неё |
|---|---|---|
| `AI_API_KEY` | объяснения ИИ в «Для вас» и «Дуэте» | подборка и дуэт работают без объяснения |
| `RECOGNITION_API_KEY` | распознавание музыки (`POST /recognition`, сервис AudD) | `503 SERVICE_UNAVAILABLE` |

Ограничение частоты запросов: `RATE_LIMIT_PER_MINUTE` (общее), `AUTH_RATE_LIMIT_PER_MINUTE` (вход и регистрация), `AI_RATE_LIMIT_PER_MINUTE` (ИИ и распознавание). При превышении сервер отвечает `429 RATE_LIMITED`.
Каждый запрос пишется в лог с `requestId` (он же в заголовке `X-Request-Id`): `docker compose logs -f api`.

## Запуск (нужен Docker)

```bash
cd server
cp .env.example .env        # впишите свои DB_PASSWORD и JWT_SECRET
docker compose up --build   # первый запуск 3–5 минут
```

Проверка: `curl http://localhost:8080/api/v1/health` → `{"status":"ok","version":"0.3.0"}`.
При первом старте создаются таблицы и 12 демо-треков.

## Примеры

```bash
B=http://localhost:8080/api/v1
# регистрация
curl -s -X POST $B/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"me@example.com","password":"password123","displayName":"Me"}'
# каталог
curl -s "$B/tracks?query=night&pageSize=5"
# лайк (подставьте accessToken и id трека)
curl -s -X PUT $B/tracks/<id>/like -H "Authorization: Bearer <accessToken>"
```

## Запуск без Docker (для разработки)

Нужны JDK 17 и PostgreSQL. Задайте переменные `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` и выполните `gradle run`.

## Коллекция Bruno

Папка `bruno/` открывается в Bruno (File → Open Collection). Выберите окружение `local` или `production` (адрес сервера в `environments/`) и запустите коллекцию (Run). Запросы идут по порядку, токены и идентификаторы подставляются сами; в каждом запросе есть проверки (tests).

## Выкладка на сервер

См. `../docs/10-deploy.md`.

## Тесты

`gradle test` — интеграционные тесты на встроенной базе H2, Docker не нужен. Внешние сервисы в тестах заменены заглушками.

## Структура

`routes/` — HTTP-маршруты · `auth/` — JWT, refresh-токены, bcrypt · `db/` — таблицы Exposed ·
`service/` — сопоставление импорта · `errors/` — единый формат ошибок.
