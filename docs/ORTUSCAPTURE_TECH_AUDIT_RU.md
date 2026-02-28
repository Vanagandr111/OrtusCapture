# OrtusCapture — технический аудит и карта кода

Документ подготовлен по текущему состоянию проекта в репозитории.

Цель документа:
- проверить соответствие текущей реализации заявленному ТЗ;
- дать понятную карту по файлам, классам и методам;
- описать текущий API/точки расширения для дальнейшей разработки.

## 1) Аудит по ТЗ (чеклист)

Статусы:
- `Сделано`
- `Частично`
- `Не сделано`

### 1.1 Создание захватываемых регионов

1. Создавать новые земли через Lands API  
Статус: `Сделано`  
Комментарий: добавлена команда `/oc create <systemId> <type> [name...]`, которая создает Land через `Lands API` (`Land.of(...)`), переводит землю в `ADMIN`, назначает системного владельца и регистрирует регион в `regions.yml`.

2. Помечать существующие земли как захватываемые (`capture_zone: true`)  
Статус: `Сделано`  
Комментарий: регистрация выполняется через `/oc setup`, а явное управление флагом доступно через `/oc capture <id> <on/off>`. В `regions.yml` сохраняются оба ключа: `captureEnabled` и алиас `capture_zone`. Дополнительно плагин умеет синхронизировать флаг в Lands через admin-команду `setflag` (настраивается в `config.yml`, секция `lands-capture-flag`).

3. Поддержка произвольных пользовательских названий региона  
Статус: `Сделано`  
Комментарий: используется имя земли из Lands (`land.getName()`), системная логика завязана на ULID и `systemId`.

4. Уникальный системный ID (`mine_001`, `fort_017`) для логики/хранения/API  
Статус: `Сделано`  
Комментарий: ключом региона является `systemId`, привязка к земле хранится по `land.getULID().toString()`.

5. Владелец региона по умолчанию — системный аккаунт (`server`)  
Статус: `Сделано`  
Комментарий: в плагине реализован системный владелец `Wild_land` (конфиг `system-owner-name`). Назначение системного владельца выполняется в `/oc setup` и повторно при успешном захвате (`land.setOwner(systemOwnerId)`).

6. Поддержка типов регионов (шахта, форт, застава) и влияние на поведение  
Статус: `Сделано`  
Комментарий: типы сохраняются (`mine/farm/fort/outpost`), валидируются и используются в UI/инфо. Также добавлены type-specific настройки поведения захвата через `config.yml` (`capture-duration-seconds-by-type`, `capture-points-required-by-type`).

7. Индивидуальное отключение захвата региона  
Статус: `Сделано`  
Комментарий: поле `captureEnabled` в `RegionModel`/`regions.yml`, проверяется в `FlagListener`.

### 1.2 Инициация захвата

1. Игрок устанавливает специальный предмет "Флаг захвата"  
Статус: `Сделано`  
Комментарий: предмет — `WHITE_BANNER` с `PersistentDataContainer` (`flag_system_id`).

2. Отслеживание через `BlockPlaceEvent` / `PlayerInteractEvent`  
Статус: `Сделано` (через `BlockPlaceEvent`)  
Комментарий: используется `FlagListener#onBlockPlace`.

3. Условия старта:
- регион захватываемый (`capture_zone`)  
Статус: `Сделано` (через `regions.yml` + `captureEnabled`)
- у игрока нет активного захвата  
Статус: `Сделано`
- игрок не превысил лимит владения  
Статус: `Сделано` (считается по `currentOwner == UUID игрока`)
- в регионе нет другого активного захвата  
Статус: `Сделано`

Примечание по лимиту:
- баг подсчета лимита исправлен: теперь лимит считается по количеству регионов, где `currentOwner` совпадает с UUID игрока.

### 1.3 Процесс захвата

1. Режим "по времени"  
Статус: `Сделано`  
Комментарий: `capture-duration-seconds`, `BossBar`, таймер `BukkitRunnable`.

2. Режим "по очкам" (убийства/очки)  
Статус: `Сделано`  
Комментарий: добавлен режим `capture-mode: POINTS`, начисление очков за убийства защитников (`PlayerDeathEvent` -> `handleCaptureKill`), отображение прогресса в `BossBar`/надписи.

3. Пауза захвата, если в регионе находятся защитники (trusted игроки)  
Статус: `Сделано`  
Комментарий: в режиме `TIME` таймер приостанавливается, если в земле есть защитники (trusted/owner, кроме захватчика и системного владельца). Настройка: `pause-time-capture-while-defenders-present`.

4. Визуал/аудио:
- `BossBar`  
Статус: `Сделано`
- `Title` уведомления  
Статус: `Сделано`
- `ActionBar` уведомления  
Статус: `Сделано`
- `Sound` (тревога/победа)  
Статус: `Сделано` (звук старта, победы и провала)
- `Particles` вокруг флага  
Статус: `Сделано`

### 1.4 Завершение захвата

1. Удаление участников из привата (`untrust`)  
Статус: `Сделано`  
Комментарий: в `processCaptureSuccess` удаляются trusted игроки, кроме системного владельца и захватчика.

2. Выдача роли `captor` с ограниченными правами  
Статус: `Сделано`  
Комментарий: плагин выдает trust и запускает консольный пайплайн назначения роли `captor` (настраивается в `config.yml`): `addRole` + `setrole` + дополнительные консольные команды. Права роли задаются шаблоном в `roles.yml` Lands; плагин также кладет helper-файлы в `landsloadconfig/`.

3. Уведомления захватчику / бывшему владельцу  
Статус: `Сделано`  
Комментарий: захватчик получает сообщения; выгнанные trusted-игроки (онлайн) получают уведомление; также добавлено отдельное уведомление бывшему владельцу земли (если он онлайн).

4. Уникальный ID сохраняется для логирования и повторной идентификации  
Статус: `Сделано`

### 1.5 Ограничения и правила

1. Захват только в `capture_zone`  
Статус: `Сделано` (через внутреннюю регистрацию региона)

2. Игрок может владеть несколькими регионами, но не более N  
Статус: `Сделано` (лимит по `currentOwner`, настраивается через `max-regions-per-player`)

3. Кулдаун между захватами  
Статус: `Сделано` (реализован кулдаун региона `region-cooldown-hours`)

4. Расписание (например, только по субботам)  
Статус: `Сделано` (`allowed-days`, плюс debug bypass)

5. Один активный захват на игрока  
Статус: `Сделано`

## 2) Что уже есть сверх базового ТЗ

- `/oc create`, `/oc capture`, `/oc help`, `/oc status`, `/oc info`, `/oc reload`, `/oc reset`, `/oc debug ...`
- Локализация через `lang.yml` (почти весь текст вынесен)
- Конфигурируемые крафты флагов через `crafts.yml` (shaped recipes по типам регионов)
- Крафтовые флаги с доп. PDC-тегами (`type/kind/version`) и автопривязкой к региону по земле Lands
- `Title` и `ActionBar` уведомления во время захвата
- Настройка звуков захвата через `config.yml` (название/громкость/тон/enable)
- Синхронизация `capture`-флага в Lands через `setflag` (консольные команды из `config.yml`)
- `PlaceholderAPI` плейсхолдеры:
  - `%ortus_owner_<id>%`
  - `%ortus_cooldown_<id>%`
- Логирование истории захватов в `history.log`
- Защита системного ника (`SystemOwnerGuard`)
- Helper-файлы для настройки роли `captor` в Lands (`landsloadconfig/`)

## 3) Ключевые риски / что стоит исправить в первую очередь

1. `capture_zone` в логике OrtusCapture основан на `regions.yml`; синхронизация в Lands (`setflag`) зависит от наличия/валидности нужного land-flag на вашем сервере  
Если флаг `capture` (или другой указанный в `lands-capture-flag.flag-name`) не поддерживается вашей конфигурацией Lands, плагин продолжит работать по внутреннему флагу.

2. Точные флаги роли `captor` зависят от настройки Lands (`roles.yml`) и/или доп. консольных команд в `config.yml`  
Плагин теперь запускает консольный пайплайн (`addRole`, `setrole`, `extra-console-commands`), но конкретные флаги роли определяются конфигом Lands/администратором.

## 4) Карта проекта (по файлам)

### 4.1 Java-код (`src/main/java/me/azzimov/ortuscapture`)

#### `OrtusCapture.java`
Главный класс плагина (`JavaPlugin`), а также:
- `CommandExecutor` для `/oc`
- `TabCompleter` для `/oc`
- центр захватной механики (таймер, BossBar, завершение, выдача роли)
- общий API для внутренних классов (`getDataManager`, `getLandsHook`, `createFlagItem`, т.д.)

#### `FlagListener.java`
Обрабатывает:
- установку флага (`BlockPlaceEvent`)
  - поддерживает bound-флаги (`systemId`) и crafted typed-флаги (`type`) с автопоиском региона по текущей земле
- ломание флага (`BlockBreakEvent`) -> отмена активного захвата
- смерти игроков (`PlayerDeathEvent`) -> начисление очков захвата в режиме `POINTS`
- выход игрока (`PlayerQuitEvent`) -> очистка debug bypass

#### `DataManager.java`
Хранилище `regions.yml`:
- загрузка/сохранение регионов
- поиск `systemId` по `landId` (ULID)
- доступ к списку регионов

#### `RegionModel.java`
Модель данных региона:
- `systemId`
- `landId` (ULID строки)
- `type`
- `captureEnabled`
- `currentOwner`
- `lastCaptureTime`

#### `CaptureSession.java`
DTO/контейнер активной сессии захвата:
- UUID игрока
- ID региона
- ULID земли (`landId`)
- режим захвата (`TIME`/`POINTS`)
- время старта
- `BossBar`
- `ArmorStand`
- локация флага
- очки захвата (`requiredPoints`, `currentPoints`)
- флаг паузы (`pausedByDefenders`)

#### `LandsHook.java`
Инициализация и хранение подключения к Lands API.

#### `LangManager.java`
Загрузка и форматирование локализации (`lang.yml`), плейсхолдеры `{key}`, цвет-коды `&`.
Также содержит небольшую миграцию устаревших ключей/значений (пример: `будний` -> `будни`).

#### `CraftManager.java`
Менеджер крафтов `crafts.yml`:
- загрузка/перезагрузка конфигурации крафтов
- регистрация shaped-рецептов для флагов по типам (`mine/farm/fort/outpost`)
- удаление/перерегистрация рецептов при `/oc reload`

#### `OrtusPlaceholderExpansion.java`
Расширение PlaceholderAPI для плейсхолдеров OrtusCapture.

#### `SystemOwnerGuard.java`
Блокирует вход игрока с системным ником (`system-owner-name`), чтобы не было конфликта с "служебным" владельцем.

### 4.2 Ресурсы (`src/main/resources`)

#### `plugin.yml`
- регистрация команды `oc`
- пермишены:
  - `ortuscapture.admin`
  - `ortuscapture.player`
- `softdepend`: `Lands`, `PlaceholderAPI`

#### `config.yml`
Основные настройки:
- `max-regions-per-player`
- `region-types`
- `capture-mode`
- `capture-duration-seconds`
- `capture-duration-seconds-by-type`
- `capture-points-required`
- `capture-points-required-by-type`
- `capture-points-per-kill`
- `pause-time-capture-while-defenders-present`
- `ui.title.*` (вкл/выкл, тайминги)
- `ui.actionbar.enabled`
- `sounds.capture-start.*`
- `sounds.capture-success.*`
- `sounds.capture-fail.*`
- `captor-role.*` (консольный пайплайн назначения роли)
- `lands-capture-flag.*` (sync `setflag` в Lands)
- `system-owner-name`
- `region-cooldown-hours`
- `allowed-days`

#### `crafts.yml`
Конфиг крафтов флагов:
- `crafts.enabled`
- `crafts.flags.<type>.enabled`
- `crafts.flags.<type>.shape` (сетка 3x3)
- `crafts.flags.<type>.ingredients`
- `crafts.flags.<type>.amount`

#### `lang.yml`
Локализация всех сообщений плагина.

#### `landsloadconfig/captor-role.yml`
Пример роли `captor` для переноса в конфиг Lands.

#### `landsloadconfig/README_lands_ru.txt`
Инструкция по интеграции роли и настроек в Lands.

#### `landsloadconfig/CAPTURE_FLAG_GUIDE_RU.txt`
Инструкция по настройке и синхронизации флага `capture` через `lands admin land ... setflag`.

## 4.3 Крафты флагов (новое)

- Крафт-флаги создаются через `crafts.yml` как **typed flags** (например, `mine`, `farm`).
- На предмет пишутся доп. PDC-теги:
  - `flag_region_type`
  - `flag_kind`
  - `flag_version`
- При установке такого флага плагин:
  1) определяет текущую землю Lands по чанку;
  2) ищет зарегистрированный регион OrtusCapture по `landId`;
  3) проверяет совпадение типа региона и типа флага;
  4) запускает стандартную механику захвата.

Это подключено к текущей системе и не ломает `/oc giveflag`, который продолжает выдавать bound-флаг с `systemId`.

## 5) Карта классов и методов (что делает и как использовать)

Ниже перечислены все методы классов проекта (по текущему коду).

### 5.1 `CaptureSession`

Файл: `src/main/java/me/azzimov/ortuscapture/CaptureSession.java`

- `CaptureSession(UUID playerUuid, String regionId, String landId, CaptureMode mode, long startTimeMillis, BossBar bossBar, ArmorStand armorStand, Location flagLocation, int requiredPoints)`
  - Назначение: создать объект активного захвата.
  - Где используется: `OrtusCapture#startCapture`.
  - Примечание: теперь сессия уже хранит режим, землю, очки и paused-state.

- `getPlayerUuid()`
  - Возвращает UUID захватчика.

- `getRegionId()`
  - Возвращает системный ID региона.

- `getLandId()`
  - Возвращает ULID земли для этой сессии.

- `getMode()`
  - Возвращает режим захвата (`TIME` / `POINTS`).

- `getStartTimeMillis()`
  - Возвращает время старта.

- `getBossBar()`
  - Возвращает `BossBar` сессии.

- `getArmorStand()`
  - Возвращает `ArmorStand` текста над флагом.

- `getFlagLocation()`
  - Возвращает координаты установленного флага.

- `getRequiredPoints()`, `getCurrentPoints()`, `addPoints(int)`
  - Используются в режиме `POINTS` для хранения/увеличения очков прогресса.

- `isPausedByDefenders()`, `setPausedByDefenders(boolean)`
  - Состояние паузы захвата по времени при наличии защитников.

### 5.2 `RegionModel`

Файл: `src/main/java/me/azzimov/ortuscapture/RegionModel.java`

- `RegionModel(String systemId, String landId, String type, boolean captureEnabled)`
  - Упрощенный конструктор (без владельца и кулдауна).

- `RegionModel(String systemId, String landId, String type, boolean captureEnabled, UUID currentOwner, long lastCaptureTime)`
  - Полный конструктор.

- `getSystemId()`
  - Возвращает системный ID (ключ логики).

- `getLandId()`
  - Возвращает ULID земли Lands (строка).

- `getType()`
  - Возвращает тип региона (`mine/farm/fort/outpost`).

- `isCaptureEnabled()`
  - Проверка, разрешен ли захват региона.

- `setCaptureEnabled(boolean captureEnabled)`
  - Включение/выключение захвата для конкретного региона.
  - Будущее использование: команда `/oc region toggle <id>`.

- `getCurrentOwner()`
  - Возвращает статистического владельца (UUID захватчика) из `regions.yml`.

- `setCurrentOwner(UUID currentOwner)`
  - Устанавливает текущего владельца для статистики/плейсхолдеров.

- `getLastCaptureTime()`
  - Возвращает таймстамп последнего успешного захвата.

- `setLastCaptureTime(long lastCaptureTime)`
  - Обновляет время последнего захвата (для кулдауна).

### 5.3 `DataManager`

Файл: `src/main/java/me/azzimov/ortuscapture/DataManager.java`

- `DataManager(OrtusCapture plugin)`
  - Инициализация менеджера, создание `regions.yml`, загрузка данных.

- `loadRegions()`
  - Внутренний метод загрузки из YAML в память (`Map<String, RegionModel>`).
  - Примечание: приватный, вызывается через `reload()`.

- `reload()`
  - Перезагружает `regions.yml` из файла.
  - Использование: `OrtusCapture#reloadOrtusConfig()`.

- `saveRegions()`
  - Полностью пересобирает секцию `regions` и сохраняет в файл.
  - Важно: вызывается после изменений модели.

- `addOrUpdateRegion(RegionModel region)`
  - Добавляет/обновляет запись региона и сохраняет файл.
  - Использование: `/oc setup`, завершение захвата, `/oc reset`.

- `getRegionIdByLandId(String searchLandId)`
  - Ищет системный ID по ULID земли.
  - Ключевой метод для `/oc info` и проверки соответствия земли в логике флага.

- `getSystemIds()`
  - Возвращает набор всех зарегистрированных `systemId`.
  - Использование: tab-complete для `/oc giveflag`.

- `getRegion(String systemId)`
  - Возвращает модель региона по `systemId`.

- `getRegions()`
  - Возвращает коллекцию всех регионов.
  - Использование: статус, лимиты, аналитика.

### 5.4 `LandsHook`

Файл: `src/main/java/me/azzimov/ortuscapture/LandsHook.java`

- `LandsHook(OrtusCapture plugin)`
  - Создает хук и вызывает подключение.

- `hook()`
  - Внутренний метод инициализации `LandsIntegration`.
  - Логирует успешный/неуспешный хук.

- `isAvailable()`
  - Возвращает доступность Lands API.
  - Использование: проверки в командах и `FlagListener`.

- `getLandsApi()`
  - Возвращает экземпляр `LandsIntegration`.

### 5.5 `LangManager`

Файл: `src/main/java/me/azzimov/ortuscapture/LangManager.java`

- `LangManager(JavaPlugin plugin)`
  - Конструктор менеджера локализации.

- `load()`
  - Загружает `lang.yml`, при отсутствии копирует ресурс.

- `reload()`
  - Повторная загрузка `lang.yml`.

- `getRaw(String key)`
  - Возвращает строку без цветовых кодов.

- `get(String key)`
  - Возвращает строку с цветами (`&` -> `§`).

- `format(String key, String... pairs)`
  - Форматирует строку с плейсхолдерами (`{id}`, `{name}`) и цветами.
  - Пример: `lang.format("capture.success", "id", "mine_001")`

- `formatRaw(String key, String... pairs)`
  - Форматирует строку без окрашивания (удобно для логов/файлов).

- `getList(String key)`
  - Возвращает список строк с цветами (например, help-линии).

- `formatList(String key, String... pairs)`
  - Возвращает список строк с подстановкой плейсхолдеров и цветами.

- `getRawOrMissing(String key)`
  - Внутренний fallback для отсутствующих ключей.

- `applyPlaceholders(String text, String... pairs)`
  - Внутренняя подстановка `{prefix}` и пользовательских пар.

- `color(String text)`
  - Внутреннее применение `ChatColor.translateAlternateColorCodes`.

### 5.6 `FlagListener`

Файл: `src/main/java/me/azzimov/ortuscapture/FlagListener.java`

- `FlagListener(OrtusCapture plugin)`
  - Конструктор listener-а.

- `onBlockPlace(BlockPlaceEvent event)`
  - Главная точка старта захвата.
  - Что делает:
    - распознает "флаг захвата" по PDC;
    - находит `RegionModel` по `systemId`;
    - проверяет `captureEnabled`;
    - проверяет расписание (`allowed-days`) и debug bypass;
    - проверяет Lands и конкретную землю по чанку блока;
    - проверяет соответствие ULID земли и региона;
    - проверяет лимит/кулдаун/активный захват;
    - вызывает `plugin.startCapture(...)`.
  - Расширение:
    - сюда удобно добавить проверку защитников;
    - сюда же можно добавить выбор режима захвата (`time`/`points`).

- `onBlockBreak(BlockBreakEvent event)`
  - Останавливает захват, если сломан именно блок-флаг активной сессии.
  - Использует `cancelCaptureByFlagLocation`.

- `onPlayerQuit(PlayerQuitEvent event)`
  - Сбрасывает debug bypass у игрока при выходе.

### 5.7 `SystemOwnerGuard`

Файл: `src/main/java/me/azzimov/ortuscapture/SystemOwnerGuard.java`

- `SystemOwnerGuard(OrtusCapture plugin)`
  - Конструктор listener-а.

- `onAsyncPreLogin(AsyncPlayerPreLoginEvent event)`
  - Кикает игрока, если его ник совпадает с `system-owner-name`.
  - Назначение: защитить системный "служебный" ник.

### 5.8 `OrtusPlaceholderExpansion`

Файл: `src/main/java/me/azzimov/ortuscapture/OrtusPlaceholderExpansion.java`

- `OrtusPlaceholderExpansion(OrtusCapture plugin)`
  - Конструктор PAPI expansion.

- `getIdentifier()`
  - Возвращает namespace плейсхолдеров: `ortus`.

- `getAuthor()`
  - Автор expansion.

- `getVersion()`
  - Версия из `plugin.yml`.

- `persist()`
  - Говорит PlaceholderAPI не удалять expansion при reload.

- `canRegister()`
  - Разрешение регистрации expansion.

- `onRequest(OfflinePlayer player, String params)`
  - Разбор плейсхолдеров:
    - `owner_<id>`
    - `cooldown_<id>`

- `resolveOwner(String regionId)`
  - Возвращает ник текущего владельца региона из `regions.yml`.

- `resolveCooldown(String regionId)`
  - Возвращает оставшийся кулдаун региона в формате текста.

### 5.9 `OrtusCapture` (главный класс)

Файл: `src/main/java/me/azzimov/ortuscapture/OrtusCapture.java`

#### Жизненный цикл

- `onEnable()`
  - Загружает `config.yml`, `lang.yml`, `regions.yml`
  - Инициализирует Lands hook, PDC key
  - Инициализирует системного владельца (детерминированный fake UUID)
  - Копирует helper-файлы `landsloadconfig/`
  - Регистрирует listeners
  - Регистрирует PlaceholderAPI expansion (если доступен)

- `onDisable()`
  - Чистит BossBar/ArmorStand активных сессий
  - Сбрасывает коллекции runtime-состояния

#### Команды и tab-complete

- `onCommand(...)`
  - Делегирует в `handleCommand`.

- `handleCommand(...)`
  - Реализует подкоманды:
    - `/oc help`
    - `/oc status`
    - `/oc debug admin|status|off`
    - `/oc setup <id> <type>`
    - `/oc giveflag <player> <id>`
    - `/oc info`
    - `/oc reload`
    - `/oc reset <id>`

- `sendHelp(...)`
  - Отправляет красиво оформленную справку из `lang.yml`.

- `send(...)`
  - Унифицированная отправка сообщений через `LangManager`.

- `getDayName(...)`
  - Локализует `DayOfWeek` через `lang.yml`.

- `onTabComplete(...)`
  - Автодополнение для `/oc`:
    - сабкоманды
    - типы региона из `config.yml`
    - игроков и `systemId`
    - debug-режимы

#### Внутренний API для других классов

- `getDataManager()`
  - Доступ к хранилищу регионов.

- `getLandsHook()`
  - Доступ к Lands hook.

- `getFlagKey()`
  - Доступ к `NamespacedKey` для PDC флага.

- `getLang()`
  - Доступ к менеджеру локализации.

- `hasWeekendBypass(UUID playerId)`
  - Проверка debug bypass выходных.

- `setWeekendBypass(UUID playerId, boolean enabled)`
  - Включение/выключение bypass.

- `clearWeekendBypass(UUID playerId)`
  - Сброс bypass.

- `logCapture(String message)`
  - Записывает событие в `plugins/OrtusCapture/history.log`.

#### Визуал / утилиты

- `spawnCaptureParticles(Location flagLocation, int elapsedSeconds)`
  - Рисует круг частиц вокруг флага.

- `resolveWitchParticle()`
  - Подбирает доступную witch-particle по версии API.

- `formatLocation(Location location)`
  - Форматирует координаты в строку (`world:x,y,z`) для логов.

- `isAdministrativeLand(Land land)`
  - Пытается определить админ-землю через reflection (`isAdmin()` или `getLandType()`).

- `createFlagItem(String systemId)`
  - Создает флаг захвата (`WHITE_BANNER`) с PDC-тегом `systemId`.
  - Это основной "внутренний API" для выдачи кастомного предмета.

- `reloadOrtusConfig()`
  - Перезагрузка:
    - `config.yml`
    - `lang.yml`
    - `regions.yml`
    - системного владельца (`systemOwnerId`)

- `initSystemOwnerId()`
  - Генерирует детерминированный fake UUID для системного владельца по имени из конфига.
  - Причина: `Bukkit.getOfflinePlayer(name)` может быть ненадежным до первого входа.

- `saveLandsHelperResources()`
  - Копирует helper-файлы для настройки роли Lands из ресурсов.

#### Механика захвата

- `hasActiveCapture(UUID playerId)`
  - Проверяет, есть ли активный захват у игрока.

- `hasActiveCaptureForRegion(String regionId)`
  - Проверяет, идет ли захват данного региона кем-либо.

- `cancelCaptureByFlagLocation(Location location)`
  - Ищет активную сессию по координатам флага и завершает её с `success=false`.

- `startCapture(Player player, RegionModel region, Location flagLocation)`
  - Запускает захват:
    - создает `BossBar`
    - создает `ArmorStand`
    - создает `CaptureSession`
    - пишет лог старта
    - запускает `BukkitRunnable` раз в секунду
    - проверяет смерть/оффлайн/выход за землю
    - обновляет прогресс/частицы
    - завершает захват при 100%

- `run()` (анонимный `BukkitRunnable`)
  - Таймер прогресса захвата.
  - Технически это часть `startCapture`, но логика вынесена в переопределение `run()`.

- `endCapture(UUID playerId, boolean success)`
  - Завершает сессию:
    - удаляет BossBar / ArmorStand
    - убирает поставленный баннер
    - логирует исход
    - вызывает `processCaptureSuccess(...)` при успехе
    - отправляет сообщения и звук

- `processCaptureSuccess(Player captor, RegionModel region)`
  - Обрабатывает успешный захват:
    - получает землю Lands по ULID
    - пытается перевести в `ADMIN` (`LandType.ADMIN`)
    - пытается назначить системного владельца
    - `untrust` всем trusted кроме системного владельца и захватчика
    - `trustPlayer(captorId)`
    - консольная команда `setrole ... captor`
    - лог роли
    - уведомления игрокам
    - запись `currentOwner` и `lastCaptureTime` в `regions.yml`

## 6) Текущий "API" и как его использовать

### 6.1 Команды

- `/oc help`
- `/oc status`
- `/oc info`
- `/oc setup <systemId> <type>`
- `/oc giveflag <player> <systemId>`
- `/oc reset <systemId>`
- `/oc reload`
- `/oc debug admin`
- `/oc debug status`
- `/oc debug off`

### 6.2 Пермишены

- `ortuscapture.player`
  - Доступ для обычных игроков к базовым командам (`info`, `help`, `status`).

- `ortuscapture.admin`
  - Доступ ко всем админским командам и debug-инструментам.

### 6.3 PlaceholderAPI

- `%ortus_owner_<id>%`
  - Возвращает текущего владельца региона по `regions.yml`.

- `%ortus_cooldown_<id>%`
  - Возвращает оставшийся кулдаун региона.

### 6.4 Внутренний API (для будущих классов/модулей)

Основные точки, которые уже можно переиспользовать:
- `OrtusCapture#createFlagItem(systemId)` — выдача флага
- `OrtusCapture#startCapture(...)` — запуск сессии захвата
- `OrtusCapture#endCapture(...)` — принудительное завершение
- `OrtusCapture#hasActiveCapture(...)`
- `OrtusCapture#hasActiveCaptureForRegion(...)`
- `OrtusCapture#logCapture(...)`
- `DataManager#getRegion(...)`, `getRegions()`, `addOrUpdateRegion(...)`

## 7) Зачатки API для будущего расширения (рекомендации)

Ниже то, что логично добавить следующим этапом, чтобы закрыть ТЗ полностью:

1. `CaptureMode` (enum)
- `TIME`
- `POINTS`

2. Расширить `CaptureSession`
- `CaptureMode mode`
- `int currentPoints`
- `boolean paused`
- `Set<UUID> defendersInside`

3. `RegionModel` (доп. настройки)
- `captureMode` (или по типу региона)
- `requiredPoints`
- `captureDurationSeconds` (override для конкретного региона)
- `captureZone` (явный флаг, если хотите отделить от факта регистрации)

4. Отдельный сервис `CaptureService`
- вынести из `OrtusCapture` логику запуска/таймера/завершения
- упростить поддержку и тестирование

5. Команда создания земли через Lands API (новая)
- `/oc create <systemId> <type> <displayName>`
- шаги:
  - создать Land через Lands API
  - перевести в admin
  - назначить системного владельца
  - сохранить в `regions.yml`

## 8) Мини-план до полного соответствия ТЗ

Если делать по приоритету:

1. Исправить подсчет лимита владения игрока (критично)
2. Добавить паузу захвата при защитниках
3. Добавить режим захвата по очкам
4. Добавить `Title`/`ActionBar` уведомления
5. Добавить создание земли через Lands API (`/oc create`)

## 9) Дополнительные требования пользователя (следующая фаза)

Ниже зафиксированы дополнительные идеи/правки, которые выходят за базовый ТЗ и требуют отдельного этапа разработки.

Статусы:
- `Сделано` — уже реализовано
- `Частично` — фундамент есть
- `Запланировано` — зафиксировано в аудите, но ещё не реализовано

1. Конфигурируемые крафты флагов (сеткой) + интеграция с текущей системой  
Статус: `Сделано`  
Комментарий: добавлены `crafts.yml`, `CraftManager`, shaped-рецепты по типам, typed-флаги с PDC-тегами.

2. Дополнительные теги у флагов для отслеживания  
Статус: `Сделано`  
Комментарий: используются `flag_system_id`, `flag_region_type`, `flag_kind`, `flag_version`.

3. Создание Lands с особым названием/описанием и типовым приветственным сообщением  
Статус: `Частично`  
Комментарий: особое название уже есть через `/oc create ... [name...]`; авто-MOTD/описание для Lands по шаблону пока не реализовано.

4. Проверки разрешённых действий внутри спец-регионов по правилам плагина (поверх Lands)  
Статус: `Частично`  
Комментарий: базовая модель контроля через роли/захват есть; отдельный слой listeners по действиям (build/break/use по типам региона) ещё не реализован.

5. Дополнительные debug-команды, имитирующие действия флагов/захвата  
Статус: `Частично`  
Комментарий: уже есть `debug` режимы, но нет симуляторов старта/успеха/провала/очков захвата.

6. Технический системный владелец для “ничейных”/служебных земель  
Статус: `Сделано`  
Комментарий: системный владелец + защита входа (`SystemOwnerGuard`) уже реализованы.

7. Автовозврат регионов системе при неактиве/утере состава/удалении группы  
Статус: `Запланировано`  
Комментарий: нужен отдельный scheduler + политика неактива + критерии сброса.

8. Собственное GUI шахты/форта (похоже на Lands): приглашения, статистика, активный захват, координаты флагштока  
Статус: `Запланировано`  
Комментарий: требует отдельного GUI-слоя и расширения `RegionModel`.

9. Запрет переименования спец-земель (шахта/форт и т.п.)  
Статус: `Запланировано`

10. Админ-GUI для конфигов плагина  
Статус: `Запланировано`

11. Локальный broadcast с эффектами при старте захвата  
Статус: `Частично`  
Комментарий: эффекты/BossBar/звуки/Title/ActionBar уже есть для захватчика; локальный broadcast по радиусу/земле ещё не реализован.

12. BossBar с прогрессом захвата  
Статус: `Сделано`

13. Назначение существующего Lands в тип (шахта/форт) с принудительной системной настройкой  
Статус: `Частично`  
Комментарий: `/oc setup` уже привязывает существующий Land, переводит в `ADMIN`, назначает системного владельца и синхронизирует capture-flag. Отдельного “профиля типа” (пресеты построек/точек/ограничений) пока нет.

14. Настройка местоположений построек/точек (включая флагшток) через меню  
Статус: `Запланировано`

### Рекомендуемый порядок реализации (следующий этап)

1. Расширить `RegionModel` (точки/координаты/displayName/MOTD шаблон)
2. Добавить `RegionRulesService` (кастомные проверки действий)
3. Добавить `RegionGuiService` (GUI игрока/админа)
4. Добавить `BroadcastService` (локальные анонсы и эффекты)
5. Добавить `InactiveRegionTask` (автосброс/возврат системе)
## 10) Дополнение: флагшток, display-флаги и прочность (после аудита)

### Статус: `Сделано` (фундамент + рабочая механика)

- Добавлен `flags.yml`:
  - тип региона -> цвет (`mine=purple`, `farm=green`, `fort=red, outpost=yellow`)
  - цветовые пресеты (`red`, `green`, `purple`, `blue`)
  - какой баннер выдаётся игроку (`item-banner`)
  - HP/урон/ремонт/кулдаун ремонта
  - модель display-флага в структурном формате (`model.parts`, без `/summon`)
- Обновлено: `outpost -> yellow` (для разнообразия), добавлен пресет `yellow`
- Добавлены красивые lore-описания флагов в `flags.yml` (инструкция по установке/ломанию/ремонту)
- Реализован `FlagStyleManager` (загрузка стилей/цветов/параметров из `flags.yml`)
- Флаг выдаётся как баннер-предмет, но при установке баннер-блок не ставится:
  - `BlockPlaceEvent` отменяется
  - спавнится display-модель флага
  - ставятся невидимые `BARRIER`-коллизии (по умолчанию 2 блока вверх)
- Реализована прочность флага:
  - удары по коллизии (`BlockDamageEvent` / `BlockBreakEvent`) наносят урон
  - HP отображается в визуале захвата + actionbar
  - при разрушении флага захват прерывается
- Реализован ремонт флага:
  - `Shift + ПКМ` по коллизии
  - ремонт по шагам с cooldown (медленно)
  - скорость/кулдаун/дальность настраиваются в `flags.yml`
- Реализована точка флагштока региона:
  - хранится в `RegionModel` и `regions.yml` (`flagpole.world/x/y/z`)
  - команда `/oc point set flagpole <id>` (и `clear`)
  - установка флага проверяет точку, если она задана

### Ограничение текущего этапа

- Экспорт block-display сайта в виде длинной `/summon ... Passengers:[item_display...]` команды с `player_head`-текстурами пока не импортируется автоматически.
- Вместо этого сделан "адекватный" конфиг `flags.yml` с понятными частями модели (`block + translation + scale`), чтобы можно было быстро править внешний вид без правки кода.

### Файлы (новые/обновлённые)

- `src/main/java/me/azzimov/ortuscapture/FlagStyleManager.java`
- `src/main/java/me/azzimov/ortuscapture/ActiveFlagPole.java`
- `src/main/resources/flags.yml`
- `src/main/java/me/azzimov/ortuscapture/RegionModel.java`
- `src/main/java/me/azzimov/ortuscapture/DataManager.java`
- `src/main/java/me/azzimov/ortuscapture/FlagListener.java`
- `src/main/java/me/azzimov/ortuscapture/OrtusCapture.java`
- `src/main/resources/documentation/ORTUSCAPTURE_COMMANDS_RU.md`
- `docs/ORTUSCAPTURE_USER_GUIDE_RU.md`
- `build.gradle` (`runClient` запускается через Java toolchain 21 для устранения `LinkageError` на JDK17)

## 11) Дополнение: GUI и runtime-документация (после аудита)

### Статус: `Частично`

### Что уже реализовано

- Базовый GUI-фреймворк:
  - `GuiManager`
  - `GuiListener`
  - `OrtusMenuHolder`
- Меню в формате сундука `3x9` (27 слотов)
- Симметричная рамка из `BLACK_STAINED_GLASS_PANE` с пустым названием
- Открытая центральная зона под кнопки/элементы меню
- Команды:
  - `/oc gui` (регион по текущей земле)
  - `/oc gui admin`
  - `/oc gui <id>`
- Регион GUI (база):
  - systemId / type
  - статус захвата
  - точка флагштока
  - владелец / кулдаун
- Админ GUI (база):
  - число регионов в базе
  - кнопка reload
  - день сервера / тип дня

### Что ещё не реализовано в GUI

- список регионов с пагинацией
- редактирование региона через кнопки (type/captureEnabled/flagpole)
- отдельные GUI для шахты/форта с приглашениями/статистикой
- админ GUI конфигов (полный набор настроек)

### Runtime-документация в папке плагина

- Документы из `docs/` и `src/main/resources/documentation/` выгружаются в:
  - `plugins/OrtusCapture/documentation/`
- На `/oc reload` документы теперь перезаписываются (`overwrite=true`), чтобы пользователь видел актуальную версию.
