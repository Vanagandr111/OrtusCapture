# Headless test bot (`runClient` / `runTestBot`)

Мини-клиент для локального теста захватов без второго лаунчера/аккаунта.

## Что это такое

- Это **headless** бот (без окна Minecraft).
- Он подключается к серверу как клиент и умеет отправлять команды.
- Окно игры не появится — это нормально.

## Запуск

Из папки проекта:

- `gradle runTestBot`
- или `gradle runClient`

С параметрами:

- `gradle runTestBot -PbotHost=127.0.0.1 -PbotPort=25565 -PbotName=OrtusBot`
- `gradle runTestBot -PbotAutoCommands=\"oc info;/oc status\"`

## Важно для локального теста

- Если бот без лицензии — для локального тестового сервера нужен `online-mode=false` в `server.properties`.
- Используйте это только на локальном/приватном тестовом сервере.

## Почему пишет `Connection refused`

Это означает, что на `host:port` не запущен сервер Paper.

Проверьте:
- сервер запущен;
- правильный порт (`25565` по умолчанию);
- `botHost/botPort` совпадают с настройками сервера.

## Ввод команд

После подключения команды можно вводить в консоль Gradle:

- `/oc info`
- `/oc status`
- `/oc debug status`

Выход:
- `exit`
- `quit`
