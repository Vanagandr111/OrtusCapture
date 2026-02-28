# OrtusCapture Commands (RU)

Автор плагина: `Azzimov`

## Игроки

- `/oc help` — список команд
- `/oc info` — информация о текущей земле Lands / регионе
- `/oc status` — статус плагина
- `/oc gui` — GUI текущего региона (если вы на зарегистрированной земле)

## Администраторы

- `/oc setup <id> <type>` — привязать текущую землю к региону
- `/oc create <id> <type> [name...]` — создать Land и зарегистрировать регион
- `/oc edit <id> <type>` — изменить тип региона
- `/oc capture <id> <on|off>` — включить/выключить захват региона
- `/oc point set flagpole <id>` — задать точку флагштока
- `/oc point clear flagpole <id>` — очистить точку флагштока
- `/oc giveflag <player> [id]` — выдать флаг (`[id]` не указан = универсальный)
- `/oc regionreset <id>` — удалить запись региона из `regions.yml` (Land остаётся)
- `/oc delete <id>` — удалить регион и связанную землю Lands
- `/oc reload` — перезагрузить конфиги / язык / документацию
- `/oc gui admin` — админ GUI
- `/oc gui <id>` — GUI региона по ID

## Debug (админ)

- `/oc debug admin`
- `/oc debug status`
- `/oc debug off`
