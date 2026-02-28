# OrtusCapture — документация для разработки (RU)

Этот файл нужен разработчикам, которые будут дописывать плагин.

## 1. Назначение

`OrtusCapture` — плагин событий захвата регионов на базе `Lands`:
- регионы регистрируются по системному ID (`mine_001`, `fort_017`);
- захват запускается установкой специального флага;
- после успеха плагин меняет доступ через `Lands` и записывает владельца в `regions.yml`;
- визуал захвата: BossBar, частицы, звуки, display-флаг, HP флага.

## 2. Основная архитектура

### Главный класс
- `me.azzimov.ortuscapture.OrtusCapture`
  - `JavaPlugin`
  - `CommandExecutor` и `TabCompleter` для `/oc`
  - управление активными сессиями захвата
  - создание/выдача флагов
  - интеграция с `Lands`, `PlaceholderAPI`, логами и GUI

### Хранилище
- `DataManager`
  - чтение/запись `regions.yml`
  - поиск региона по `systemId`
  - поиск `systemId` по `lands ULID`

- `RegionModel`
  - `systemId`, `landId`, `type`, `captureEnabled`
  - `currentOwner`, `lastCaptureTime`
  - точка `flagpole` (`world/x/y/z`)

### Захват
- `CaptureSession`
  - состояние одного активного захвата
  - режим `TIME/POINTS`
  - прогресс/очки
  - ссылки на `BossBar`, `ArmorStand`, точку флага

- `FlagListener`
  - установка флага
  - ломание флага / коллизии
  - ремонт `Shift+ПКМ`
  - смерть игрока (очки в режиме `POINTS`)
  - выход игрока (очистка debug bypass)

- `ActiveFlagPole`
  - состояние установленного display-флага
  - HP, коллизия, связанные сущности/барьеры

### Интеграции/утилиты
- `LandsHook` — хук к `Lands API`
- `OrtusPlaceholderExpansion` — PAPI плейсхолдеры `%ortus_*%`
- `LangManager` — локализация `lang.yml`
- `CraftManager` — рецепты флагов из `crafts.yml`
- `FlagStyleManager` — внешний вид/цвета/модель/лоры флагов из `flags.yml`
- `SystemOwnerGuard` — блок входа под системным ником

### GUI (фундамент)
- `GuiManager` — построение меню (3 ряда, рамка, кнопки)
- `GuiListener` — обработка кликов/drag
- `OrtusMenuHolder` — тип меню + контекст (`ADMIN` / `REGION`)

## 3. Ключевые файлы конфигов

### `config.yml`
- общие настройки захвата (лимиты, дни, cooldown, звуки)
- `lands-capture-flag.*` — синхронизация флага `capture` в Lands через консольные команды
- консольные команды для назначения роли `captor`

### `regions.yml`
- база зарегистрированных регионов
- хранит `landId` (ULID), тип, владельца, cooldown, `flagpole`

### `lang.yml`
- все пользовательские сообщения (включая GUI)

### `crafts.yml`
- shaped-рецепты флагов по типам (`mine`, `farm`, `fort`, `outpost`)

### `flags.yml`
- цвет флага по типу
- предмет (баннер), модель display-флага, HP, ремонт
- lore предмета флага

## 4. Поток захвата (кратко)

1. Игрок получает/крафтит typed-флаг (баннер с PDC тегами).
2. Ставит флаг в регионе Lands.
3. `FlagListener` проверяет:
   - регион зарегистрирован,
   - тип совпадает,
   - захват включен,
   - нет кулдауна/запрещённого дня,
   - нет другого активного захвата.
4. Плагин отменяет обычную установку баннера и создаёт display-флаг + коллизию.
5. Стартует `CaptureSession`.
6. По завершению вызывается `processCaptureSuccess(...)`.

## 5. Внутренние точки расширения

Рекомендуемые точки для новых модулей:
- `OrtusCapture#startCapture(...)`
- `OrtusCapture#endCapture(...)`
- `OrtusCapture#processCaptureSuccess(...)`
- `OrtusCapture#createFlagItem(...)`
- `DataManager#addOrUpdateRegion(...)`
- `GuiManager#openRegionMenu(...)` / `openAdminMenu(...)`

## 6. Что добавлять следующим этапом

- полноценные GUI-экраны региона/админа (списки, редактирование, кнопки действий)
- GUI настройки `flagpole`/точек построек
- локальный broadcast при старте захвата
- отдельный сервис `CaptureService` (вынести механику из `OrtusCapture`)
- парсер моделей `/summon block_display ...` → `flags.yml`

## 7. Соглашения по изменениям

- Все новые тексты — в `lang.yml`.
- Новые визуальные пресеты флагов — в `flags.yml`.
- Новые рецепты — в `crafts.yml`.
- Для runtime-документации используйте папку `docs/` (она копируется в `plugins/OrtusCapture/documentation/`).
