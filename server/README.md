# Сервер «media player» (Ktor)

Серверная часть приложения: каталог треков, лайки, комментарии, плейлисты (QR-ссылка, импорт),
персональные рекомендации. Контракт — `../openapi.yaml`, описание — `../docs/08-backend.md`.

Распознавание (`/recognition`) и «Дуэт» (`/duets`) требуют внешнего ИИ — отвечают 503 до практики 10.

## Запуск (нужен Docker)

```bash
cd server
cp .env.example .env
docker compose up --build
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

## Тесты

`gradle test` — интеграционные тесты на встроенной базе H2, Docker не нужен.

## Структура

`routes/` — HTTP-маршруты · `auth/` — JWT, refresh-токены, bcrypt · `db/` — таблицы Exposed ·
`service/` — сопоставление импорта · `errors/` — единый формат ошибок.
