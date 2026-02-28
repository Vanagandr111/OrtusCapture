# OrtusCapture

Minecraft плагин для захвата территорий Land (Lands API). Работает на Paper/Spigot 1.21.1+.

## Возможности

- 📍 Захват территорий через флаги
- 🏰 Интеграция с Lands API
- 🎮 Система флагштоков с HP
- 🎨 GUI меню для игроков и админов
- 🔧 Полная настройка через конфиги

## Требования

- **Minecraft Server:** Paper/Spigot 1.21.1+
- **Java:** 21
- **Плагины:** Lands API, PlaceholderAPI (опционально)

## Установка

1. Скачайте последний релиз из [Releases](https://github.com/Vanagandr111/OrtusCapture/releases)
2. Положите `OrtusCapture-1.0.0.jar` в папку `plugins/` вашего сервера
3. Перезапустите сервер

## Команды

### Для игроков
- `/oc help` — список команд
- `/oc info` — информация о земле
- `/oc status` — статус плагина
- `/oc gui` — открыть меню региона

### Для админов
- `/oc setup <id> <type>` — привязать землю к ID
- `/oc create <id> <type> [name...]` — создать регион
- `/oc capture <id> <on|off>` — включить/выключить захват
- `/oc point set flagpole <id>` — задать точку флагштока
- `/oc giveflag <player> <id>` — выдать флаг
- `/oc reload` — перезагрузить конфиги

## Сборка из исходников

```bash
# Клонирование
git clone https://github.com/Vanagandr111/OrtusCapture.git
cd OrtusCapture/OrtusCapture

# Сборка
./gradlew build

# JAR появится в build/libs/
```

## Конфигурация

После первой загрузки создадутся файлы:
- `plugins/OrtusCapture/config.yml` — основные настройки
- `plugins/OrtusCapture/regions.yml` — настройки регионов
- `plugins/OrtusCapture/lang.yml` — языковые настройки

## Документация

Подробная документация:
- [Гайд по использованию](docs/ORTUSCAPTURE_USER_GUIDE_RU.md)
- [Гайд по разработке](docs/ORTUSCAPTURE_DEV_GUIDE_RU.md)
- [Технический аудит](docs/ORTUSCAPTURE_TECH_AUDIT_RU.md)

## Лицензия

MIT License
