ФАЙЛЫ НАСТРОЙКИ LANDS ДЛЯ OrtusCapture
======================================

Эта папка создаётся плагином OrtusCapture, чтобы помочь настроить Lands:

- роль захватчика `captor`
- флаг `capture` (если используете кастомный флаг в Lands)

Содержимое:

1) `captor-role.yml`
   Пример секции роли `captor` для `plugins/lands/roles.yml`.

2) `CAPTURE_FLAG_GUIDE_RU.txt`
   Памятка по добавлению/настройке флага `capture` в Lands и синхронизации с OrtusCapture.

Важно:
- После изменения `roles.yml` Lands обычно требует reload/restart (смотрите документацию вашей версии).
- Команды, которые OrtusCapture выполняет через консоль (`addRole`, `setrole`, `setflag`), настраиваются в `config.yml`.
