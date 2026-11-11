# media-player

Android-приложение на Kotlin: музыкальный плеер со стеклянным интерфейсом, офлайн-кешем, серверным каталогом свободной музыки, импортом плейлистов из файла, персональными рекомендациями на ИИ и управлением жестами телефона.


## Документация

| Этап | Документ |
|---|---|
| 1. Идея | [docs/01-idea.docx](docs/01-idea.docx) |
| 1. Техническое задание | [docs/02-tz.docx](docs/02-tz.docx) |
| 1. Стек и архитектура | [docs/03-stack.docx](docs/03-stack.docx) |
| 1. Диаграмма use case | [docs/usecase.drawio](docs/usecase.drawio) |
| 2. Архитектура | [docs/04-architecture.docx](docs/04-architecture.docx) |
| 2. Диаграммы | [docs/layer-component-class.drawio](docs/layer-component-class.drawio) |
| 2. Дизайн система | [docs/05-design-system.docx](docs/05-design-system.docx) |
| 2. Схема навигации | [docs/navigation.drawio](docs/navigation.drawio) |
| 3. Серверная часть | [docs/08-backend.docx](docs/08-backend.docx) |
| 3. Модель данных | [docs/ER-diagram(DB).drawio](docs/ER-diagram(DB).drawio) |


## Дизайн
[Макет Figma](https://www.figma.com/design/VM6764bV5Eod4UTJr6rD5n/media-player-UI?node-id=96-897&t=SgTKflOMsVM89kJo-1)
[Прототип Figma](https://www.figma.com/proto/VM6764bV5Eod4UTJr6rD5n/media-player-UI?node-id=10-955&p=f&t=VOtl2I4dqJ63heBQ-1&scaling=min-zoom&content-scaling=fixed&page-id=0%3A1&starting-point-node-id=10%3A955)


## Стек

Kotlin · Jetpack Compose · Clean Architecture + MVVM · Hilt · Coroutines/Flow · Room · DataStore · Media3 · Retrofit · Coil · Ktor (сервер) · Kover

## Статус

- [x] Этап 1. Идея и ТЗ (`v0.1-spec`)
- [x] Этап 2. Архитектура и дизайн (`v0.2-design`)
- [ ] Этап 3. Серверная часть (`v0.3-backend`)
- [ ] Этап 4. Приложение (`v0.9-mvp`)
- [ ] Этап 5. Тестирование (`v1.0-rc`)
- [ ] Этап 6. Релиз (`v1.0`)
