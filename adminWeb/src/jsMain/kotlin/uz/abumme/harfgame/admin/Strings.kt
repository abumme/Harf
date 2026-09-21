package uz.abumme.harfgame.admin

import kotlinx.datetime.YearMonth
import uz.abumme.harfgame.admin.calendar.DayLock
import uz.abumme.harfgame.admin.players.TypeFilter
import uz.abumme.harfgame.admin.session.NavSection
import uz.abumme.harfgame.admin.words.StatusFilter
import uz.abumme.harfgame.data.admin.FieldReasons
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.StaffRules
import uz.abumme.harfgame.data.admin.analytics.AnalyticsParams
import uz.abumme.harfgame.data.admin.analytics.AnalyticsReasons
import uz.abumme.harfgame.data.admin.answerpool.MarkOutcome
import uz.abumme.harfgame.data.admin.answerpool.PoolReasons
import uz.abumme.harfgame.data.admin.calendar.CalendarNoticeDto
import uz.abumme.harfgame.data.admin.calendar.CalendarReasons
import uz.abumme.harfgame.data.admin.calendar.DaySource
import uz.abumme.harfgame.data.admin.calendar.NoticeReason
import uz.abumme.harfgame.data.admin.players.PlayerReasons
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.staff.StaffStatus
import uz.abumme.harfgame.data.admin.suggestions.DeciderDto
import uz.abumme.harfgame.data.admin.suggestions.DeciderKind
import uz.abumme.harfgame.data.admin.suggestions.ReviewReasons
import uz.abumme.harfgame.data.admin.words.BulkLineOutcome
import uz.abumme.harfgame.data.admin.words.WordReasons
import uz.abumme.harfgame.data.admin.words.WordRules
import uz.abumme.harfgame.data.admin.words.WordSource
import uz.abumme.harfgame.data.admin.words.WordStatus
import uz.abumme.harfgame.data.auth.OAuthProvider
import uz.abumme.harfgame.data.suggestion.SuggestionStatus

/** Every text the panel shows. Pages hold no literal UI text, so a second language needs only another object. */
object Strings {
    const val PRODUCT = "Harf"
    const val PANEL = "Панель сотрудников"

    object Common {
        const val LOADING = "Загрузка…"
        const val SAVE = "Сохранить"
        const val CANCEL = "Отмена"
        const val BACK = "Назад"
        const val RETRY = "Повторить"
        const val NOT_SET = "—"
        const val NETWORK_ERROR = "Нет связи с сервером. Проверьте подключение и повторите."
        const val SERVER_ERROR = "Сервер не смог выполнить запрос. Повторите чуть позже."
        const val FORBIDDEN_ACTION = "У вас нет прав на это действие."
        const val SIGN_OUT = "Выйти"
        const val PREVIOUS_PAGE = "Предыдущая страница"
        const val NEXT_PAGE = "Следующая страница"
        const val THEME_TO_DARK = "Тёмная тема"
        const val THEME_TO_LIGHT = "Светлая тема"

        fun range(first: Long, last: Long, total: Long) = "$first–$last из $total"
        fun pageOf(page: Int, count: Int) = "Страница $page из $count"
    }

    object Nav {
        const val LABEL = "Разделы панели"

        fun label(section: NavSection) = when (section) {
            NavSection.ANALYTICS -> "Аналитика"
            NavSection.WORDS -> "Слова"
            NavSection.SUGGESTIONS -> "Предложения"
            NavSection.CALENDAR -> "Календарь"
            NavSection.ANSWER_POOL -> "Пул слов дня"
            NavSection.PLAYERS -> "Игроки"
            NavSection.STAFF -> "Сотрудники"
            NavSection.AUDIT_LOG -> "Журнал действий"
            NavSection.ACTIVITY -> "Моя активность"
            NavSection.ACCOUNT -> "Аккаунт"
        }
    }

    object Roles {
        fun label(role: Role) = when (role) {
            Role.ADMIN -> "Администратор"
            Role.WORDER -> "Редактор слов"
        }
    }

    object Languages {
        fun label(lang: String) = when (lang) {
            "en" -> "Английский"
            "ru" -> "Русский"
            "kk" -> "Казахский"
            "uz-latn" -> "Узбекский (латиница)"
            "uz-cyrl" -> "Узбекский (кириллица)"
            "uz" -> "Узбекский"
            else -> lang
        }

        fun list(languages: Collection<String>) =
            if (languages.isEmpty()) Common.NOT_SET else languages.joinToString(", ") { label(it) }
    }

    object Login {
        const val TITLE = "Вход для сотрудников"
        const val USERNAME = "Логин"
        const val PASSWORD = "Пароль"
        const val SUBMIT = "Войти"
        const val SUBMITTING = "Входим…"
        const val INVALID = "Неверный логин или пароль."
        const val LOCKED = "Вход временно заблокирован после нескольких неудачных попыток. Попробуйте через 15 минут."
        const val RATE_LIMITED = "Слишком много попыток входа. Подождите минуту и попробуйте снова."
        const val EXPIRED = "Сессия закончилась. Войдите снова, чтобы продолжить."
    }

    object Home {
        const val TITLE = "Панель"
        const val NOTHING_AVAILABLE = "Для вашей учётной записи пока нет доступных разделов. Обратитесь к администратору."

        fun greeting(name: String, role: Role) = "$name, вы вошли как ${Roles.label(role).lowercase()}."
    }

    object Staff {
        const val TITLE = "Сотрудники"
        const val ADD = "Добавить сотрудника"
        const val NEW_TITLE = "Новый сотрудник"
        const val EDIT_TITLE = "Сотрудник"
        const val EMPTY = "Сотрудников пока нет. Добавьте первого."
        const val NOT_FOUND = "Такого сотрудника нет. Вернитесь к списку."
        const val COLUMN_USERNAME = "Логин"
        const val COLUMN_ROLE = "Роль"
        const val COLUMN_STATUS = "Статус"
        const val COLUMN_LANGUAGES = "Языки"
        const val COLUMN_TELEGRAM = "Telegram ID"
        const val COLUMN_LAST_LOGIN = "Последний вход"
        const val NEVER_SIGNED_IN = "Не входил"
        const val EDIT = "Изменить"

        const val USERNAME = "Логин"
        const val USERNAME_HINT = "3–32 символа: латинские буквы, цифры, точка, дефис, подчёркивание. Изменить потом нельзя."
        const val PASSWORD = "Пароль"
        const val PASSWORD_HINT = "От ${StaffRules.PASSWORD_MIN} до ${StaffRules.PASSWORD_MAX} символов."
        const val DISPLAY_NAME = "Имя"
        const val DISPLAY_NAME_HINT = "Необязательно. Показывается в журнале и списке."
        const val ROLE = "Роль"
        const val LANGUAGES = "Языки"
        const val LANGUAGES_HINT = "Редактор слов работает только с отмеченными языками. Администратору доступны все."
        const val TELEGRAM = "Telegram ID"
        const val TELEGRAM_HINT = "Необязательно. Числовой ID пользователя Telegram."
        const val CREATE = "Создать сотрудника"
        const val CREATED = "Сотрудник создан."
        const val SAVED = "Изменения сохранены."

        const val PASSWORD_SECTION = "Пароль"
        const val RESET_PASSWORD = "Задать новый пароль"
        const val NEW_PASSWORD = "Новый пароль"
        const val RESET_CONFIRM_TITLE = "Задать новый пароль?"
        const val RESET_CONFIRM_BODY = "Все сессии сотрудника завершатся, блокировка входа снимется. Сообщите новый пароль сотруднику сами."
        const val RESET_DONE = "Пароль изменён, сессии сотрудника завершены."

        const val ACCESS_SECTION = "Доступ"
        const val DISABLE = "Отключить"
        const val ENABLE = "Включить"
        const val DISABLE_CONFIRM_TITLE = "Отключить сотрудника?"
        const val DISABLE_CONFIRM_BODY = "Сотрудник сразу потеряет доступ к панели. Записи журнала и аккаунт сохранятся, включить его можно снова."
        const val ENABLE_CONFIRM_TITLE = "Включить сотрудника?"
        const val ENABLE_CONFIRM_BODY = "Сотрудник снова сможет входить со своим прежним паролем."
        const val DISABLED_DONE = "Сотрудник отключён."
        const val ENABLED_DONE = "Сотрудник снова может входить."
        const val DISABLED_NOTE = "Отключён: вход в панель закрыт."
        const val LAST_ADMIN = "Нельзя оставить панель без активного администратора."

        fun status(status: StaffStatus) = when (status) {
            StaffStatus.ACTIVE -> "Активен"
            StaffStatus.DISABLED -> "Отключён"
        }

        const val LOCKED = "Вход заблокирован"

        fun lockedUntil(time: String) = "Вход заблокирован до $time"
    }

    object Audit {
        const val TITLE = "Журнал действий"
        const val ACTIVITY_TITLE = "Моя активность"
        const val EMPTY = "Записей с такими условиями нет. Измените фильтры."
        const val FILTER_ACTOR = "Кто"
        const val FILTER_ACTION = "Действие"
        const val FILTER_LANG = "Язык"
        const val FILTER_FROM = "С даты"
        const val FILTER_TO = "По дату"
        const val ANY = "Все"
        const val RESET_FILTERS = "Сбросить фильтры"
        const val COLUMN_TIME = "Когда"
        const val COLUMN_ACTOR = "Кто"
        const val COLUMN_ACTION = "Действие"
        const val COLUMN_TARGET = "Объект"
        const val COLUMN_LANG = "Язык"
        const val COLUMN_DETAILS = "Подробности"
        const val SYSTEM = "Система"
        const val TELEGRAM = "Telegram"
        const val DELETED_ACTOR = "Сотрудник"

        fun action(action: String) = when (action) {
            AuditActions.AUTH_LOGIN_SUCCEEDED -> "Вход"
            AuditActions.AUTH_LOGIN_FAILED -> "Неудачный вход"
            AuditActions.AUTH_ACCOUNT_LOCKED -> "Вход заблокирован"
            AuditActions.AUTH_LOGGED_OUT -> "Выход"
            AuditActions.AUTH_PASSWORD_CHANGED -> "Смена своего пароля"
            AuditActions.STAFF_CREATED -> "Сотрудник создан"
            AuditActions.STAFF_UPDATED -> "Сотрудник изменён"
            AuditActions.STAFF_PASSWORD_RESET -> "Пароль сотрудника изменён"
            AuditActions.STAFF_DISABLED -> "Сотрудник отключён"
            AuditActions.STAFF_ENABLED -> "Сотрудник включён"
            AuditActions.STAFF_BOOTSTRAPPED -> "Администратор из настроек сервера"
            AuditActions.WORD_ADDED -> "Слово добавлено"
            AuditActions.WORD_RESTORED -> "Слово восстановлено"
            AuditActions.WORD_EDITED -> "Написание изменено"
            AuditActions.WORD_REMOVED -> "Слово удалено"
            AuditActions.WORD_CATALOG_IMPORTED -> "Словарь перенесён в каталог"
            AuditActions.WORD_BUNDLED_MERGED -> "Слова из словаря сборки"
            AuditActions.SUGGESTION_DECIDED -> "Решение по предложению"
            AuditActions.DAILY_ELIGIBILITY_MARKED -> "Слово добавлено в пул"
            AuditActions.DAILY_ELIGIBILITY_UNMARKED -> "Слово убрано из пула"
            AuditActions.DAILY_PAIR_CREATED -> "Пара создана"
            AuditActions.DAILY_PAIR_REMOVED -> "Пара удалена"
            AuditActions.DAILY_WORD_PICKED -> "Слово дня выбрано"
            AuditActions.DAILY_WORD_UNPICKED -> "Выбор слова дня отменён"
            AuditActions.DAILY_NOTICE_DISMISSED -> "Уведомление скрыто"
            AuditActions.DAILY_MANUAL_PICK_REPLACED -> "Выбранное слово дня заменено"
            AuditActions.PLAYER_DELETED -> "Аккаунт игрока удалён"
            AuditActions.PLAYER_SESSIONS_ENDED -> "Сессии игрока завершены"
            AuditActions.PLAYER_SUGGESTIONS_BLOCKED -> "Предложения игрока заблокированы"
            AuditActions.PLAYER_SUGGESTIONS_UNBLOCKED -> "Предложения игрока разблокированы"
            AuditActions.PLAYER_DISPLAY_NAME_CLEARED -> "Имя игрока очищено"
            else -> action
        }

        fun detailField(field: String) = when (field) {
            "username" -> "логин"
            "role" -> "роль"
            "languages" -> "языки"
            "displayName" -> "имя"
            "telegramUserId" -> "Telegram ID"
            "status" -> "статус"
            "reason" -> "причина"
            "ip" -> "адрес"
            "mode" -> "режим"
            "lockedUntil" -> "до"
            "text", "word" -> "слово"
            "source" -> "источник"
            "count" -> "количество"
            "words" -> "слова"
            "invalid" -> "не проходят правила"
            "packVersion" -> "версия словаря"
            "via" -> "способ"
            "day" -> "день"
            "previous" -> "было"
            "previousSource" -> "как было выбрано"
            "latn" -> "латиница"
            "cyrl" -> "кириллица"
            "cyrlAdded" -> "кириллица в словаре"
            "providers" -> "вход через"
            "revoked" -> "завершено сессий"
            "suggestion" -> "по предложению"
            else -> field
        }

        fun detailValue(value: String) = when (value) {
            "ADMIN" -> Roles.label(Role.ADMIN)
            "WORDER" -> Roles.label(Role.WORDER)
            "ACTIVE" -> Staff.status(StaffStatus.ACTIVE)
            "DISABLED" -> Staff.status(StaffStatus.DISABLED)
            "wrong_password" -> "неверный пароль"
            "locked" -> "вход заблокирован"
            "disabled" -> "аккаунт отключён"
            "created" -> "создан"
            "recovered" -> "восстановлен"
            "BUNDLED", "SUGGESTION", "STAFF" -> Words.source(WordSource.valueOf(value))
            "AUTO" -> "автоматически"
            "EDITOR" -> "редактор"
            "ACCEPTED" -> Suggestions.status(SuggestionStatus.ACCEPTED)
            "REJECTED" -> Suggestions.status(SuggestionStatus.REJECTED)
            "MANUAL" -> "вручную"
            "LEGACY" -> "из прежнего расписания"
            "REMOVED" -> "удалено"
            "INELIGIBLE" -> "убрано из пула"
            "added" -> "добавлена"
            "restored" -> "восстановлена"
            else -> value
        }
    }

    object Words {
        const val TITLE = "Слова"
        const val ADD = "Добавить слово"
        const val BULK_ADD = "Добавить списком"
        const val NO_LANGUAGES = "Вам пока не назначен ни один язык. Обратитесь к администратору."

        const val SEARCH = "Поиск"
        const val FILTER_STATUS = "Слова"
        const val FILTER_SOURCE = "Источник"
        const val FILTER_ADDED_BY = "Кто добавил"
        const val FILTER_FROM = "Добавлено с"
        const val FILTER_TO = "Добавлено по"
        const val ANY = "Все"
        const val ADDED_BY_ME = "Я"
        const val RESET_FILTERS = "Сбросить фильтры"

        const val COLUMN_WORD = "Слово"
        const val COLUMN_SOURCE = "Источник"
        const val COLUMN_ADDED = "Добавлено"
        const val COLUMN_UPDATED = "Изменено"
        const val COLUMN_STATUS = "Статус"
        const val COLUMN_ACTIONS = "Действия"
        const val EMPTY = "Слов с такими условиями нет. Измените фильтры или поиск."
        const val DETAILS = "Подробнее"
        const val HIDE_DETAILS = "Скрыть"

        const val PROVENANCE_ADDED = "Добавлено"
        const val PROVENANCE_UPDATED = "Последнее изменение"
        const val PROVENANCE_REMOVED = "Удалено"
        const val PREDATES_STAFF = "до учёта сотрудников"
        const val BY_SYSTEM = "без участия сотрудника"

        const val EDIT = "Изменить"
        const val REMOVE = "Удалить"
        const val RESTORE = "Восстановить"

        const val ADD_TITLE = "Новое слово"
        const val WORD = "Слово"
        const val NORMALIZED = "Будет сохранено как"
        const val LETTERS = "Букв"
        const val TOKENIZES = "Буквы алфавита"
        const val LENGTH = "Длина"
        const val YES = "да"
        const val NO = "нет"
        const val CHECKING = "Проверяем…"
        const val CHECK_VALID = "Слово можно добавить."
        const val CHECK_RESTORABLE = "Это слово было удалено: добавление восстановит его."
        const val CHECK_DUPLICATE = "Такое слово уже есть в словаре."
        const val SUBMIT_ADD = "Добавить"
        const val ADDED = "Слово добавлено. Игроки получат его со следующим обновлением словаря."
        const val RESTORED_BY_ADD = "Слово было удалено и теперь восстановлено."

        const val BULK_TITLE = "Добавить списком"
        const val BULK_LABEL = "Слова, по одному в строке"
        const val BULK_HINT = "Пустые строки пропускаются. Каждое слово проверяется отдельно, подходящие добавятся, даже если другие не подойдут."
        const val BULK_TOO_MANY = "Не больше ${WordRules.MAX_BULK_LINES} строк за раз. Разбейте список на части."
        const val BULK_SUBMIT = "Добавить все"
        const val BULK_AGAIN = "Добавить ещё"

        fun lineCount(count: Int) = "Строк: $count из ${WordRules.MAX_BULK_LINES}"

        fun bulkOutcome(outcome: BulkLineOutcome) = when (outcome) {
            BulkLineOutcome.ADDED -> "Добавлены"
            BulkLineOutcome.RESTORED -> "Восстановлены"
            BulkLineOutcome.DUPLICATE -> "Уже есть или повторяются"
            BulkLineOutcome.INVALID -> "Не подходят"
            BulkLineOutcome.BLOCKLISTED -> "В стоп-листе"
        }

        const val EDIT_TITLE = "Изменить написание"
        const val EDIT_NOTE = "Старое написание останется удалённым и не вернётся из словаря сборки."
        const val SUBMIT_EDIT = "Сохранить"
        const val EDITED = "Написание изменено."

        const val REMOVE_TITLE = "Удалить слово?"
        const val REMOVED = "Слово удалено."
        const val RESTORED = "Слово восстановлено."

        fun removeBody(word: String) =
            "«$word» перестанет приниматься в игре, когда словарь обновится у игроков. Удалённое слово можно восстановить."

        fun source(source: WordSource) = when (source) {
            WordSource.BUNDLED -> "Словарь"
            WordSource.SUGGESTION -> "Предложение игрока"
            WordSource.AUTO -> "Автопринято"
            WordSource.STAFF -> "Сотрудник"
        }

        /** How a suggested word was accepted, for its provenance. */
        fun suggestionOrigin(source: WordSource) = when (source) {
            WordSource.AUTO -> "Предложение игрока, принято автоматически после проверки словарём"
            else -> "Предложение игрока, принято редактором"
        }

        fun status(status: WordStatus) = when (status) {
            WordStatus.ACTIVE -> "Активно"
            WordStatus.REMOVED -> "Удалено"
        }

        fun statusFilter(filter: StatusFilter) = when (filter) {
            StatusFilter.ACTIVE -> "Активные"
            StatusFilter.REMOVED -> "Удалённые"
            StatusFilter.ALL -> "Все"
        }

        /** "5 букв" with the Russian plural. */
        fun letters(count: Int): String {
            val mod100 = count % 100
            val mod10 = count % 10
            val word = when {
                mod100 in 11..14 -> "букв"
                mod10 == 1 -> "буква"
                mod10 in 2..4 -> "буквы"
                else -> "букв"
            }
            return "$count $word"
        }

        fun lengthRange(min: Int, max: Int) = "от $min до $max"

        /** The message for a word reason (validation, conflict or bulk line); never mentions daily words. */
        fun reason(reason: String, minLength: Int = 4, maxLength: Int = 7) = when (reason) {
            WordReasons.EMPTY -> "Введите слово."
            WordReasons.NOT_TOKENIZABLE -> "В слове есть знаки не из алфавита языка."
            WordReasons.BAD_LENGTH -> "В слове должно быть от $minLength до $maxLength букв."
            WordReasons.BLOCKLISTED -> "Слово в стоп-листе языка."
            WordReasons.UNSUPPORTED_LANGUAGE -> "Для этого языка нет правил."
            WordReasons.DUPLICATE -> "Такое слово уже есть в словаре."
            WordReasons.REMOVED_EXISTS -> "Такое написание у удалённого слова — восстановите удалённое слово."
            WordReasons.NOT_ACTIVE -> "Изменить можно только активное слово."
            WordReasons.PACK_INTEGRITY -> "Изменение нарушит целостность словаря и не сохранено."
            WordReasons.TOO_MANY_LINES -> BULK_TOO_MANY
            else -> "Слово не подходит."
        }
    }

    object Suggestions {
        const val TITLE = "Предложения"
        const val SUBTITLE = "Слова, которые игроки предложили добавить. Решение сразу попадает в словарь и в Telegram."
        const val TAB_PENDING = "Ожидают"
        const val TAB_HISTORY = "История"
        const val COLUMN_WORD = "Слово"
        const val COLUMN_AUTHOR = "Автор"
        const val COLUMN_CREATED = "Предложено"
        const val COLUMN_REASON = "Почему на проверке"
        const val COLUMN_DECISION = "Решение"
        const val COLUMN_OUTCOME = "Итог"
        const val COLUMN_DECIDED_BY = "Кто решил"
        const val COLUMN_DECIDED_AT = "Когда"
        const val ACCEPT = "Принять"
        const val REJECT = "Отклонить"
        const val EMPTY_PENDING = "Новых предложений нет."
        const val EMPTY_HISTORY = "Решённых предложений пока нет."
        const val ALREADY_DECIDED = "Уже обработано: предложение решили раньше. Список обновлён."
        const val FORBIDDEN = "Этот язык вам не назначен."

        fun accepted(word: String) = "«$word» принято и добавлено в словарь."
        fun rejected(word: String) = "«$word» отклонено."

        fun reason(reason: String?) = when (reason) {
            null -> "ожидает проверки"
            ReviewReasons.NOT_FOUND -> "нет в словаре"
            ReviewReasons.PROPER_NOUN -> "имя собственное"
            ReviewReasons.ABBREVIATION -> "сокращение"
            ReviewReasons.MISSPELLING -> "ошибочное написание"
            ReviewReasons.VULGAR -> "грубое слово"
            ReviewReasons.DISABLED -> "проверка словарём выключена"
            ReviewReasons.UNVERIFIED -> "словарь недоступен"
            ReviewReasons.REMOVED_BY_STAFF -> "удалено редакторами"
            ReviewReasons.NOT_PLAYABLE -> "не проходит правила языка"
            else -> reason
        }

        fun status(status: SuggestionStatus) = when (status) {
            SuggestionStatus.PENDING -> "Ожидает"
            SuggestionStatus.ACCEPTED -> "Принято"
            SuggestionStatus.REJECTED -> "Отклонено"
        }

        fun decider(decider: DeciderDto) = when (decider.kind) {
            DeciderKind.STAFF -> decider.name
            DeciderKind.TELEGRAM -> "Telegram ${decider.name}"
            DeciderKind.AUTO -> "Автоматически (Wiktionary)"
        }
    }

    object Calendar {
        const val TITLE = "Календарь слов дня"
        const val SUBTITLE = "Сегодня и завтра не меняются. Слово можно выбрать с послезавтрашнего дня; остальные дни заполняются автоматически."
        const val TAB_MONTH = "Месяц"
        const val TAB_TABLE = "Таблица"
        const val PREVIOUS_MONTH = "Предыдущий месяц"
        const val NEXT_MONTH = "Следующий месяц"
        const val THIS_MONTH = "Сегодня"
        const val NOT_SET_UP = "Календарь ещё не запущен на сервере."
        const val MANUAL = "вручную"
        const val AUTO = "авто"
        const val LEGACY = "история"
        const val REPEAT = "повтор"
        const val LOCKED = "Нельзя изменить"
        const val EMPTY_DAY = "нет слова"
        const val NOTICES_TITLE = "Замены выбранных слов"
        const val DISMISS = "Скрыть"
        const val NEVER_USED = "ещё не было"
        const val PICK_TITLE = "Слово дня"
        const val PICK_SEARCH = "Поиск по пулу"
        const val PICK = "Выбрать"
        const val UNPICK = "Вернуть автовыбор"
        const val PICKED = "Слово выбрано."
        const val UNPICKED = "День снова заполняется автоматически."
        const val CURRENT = "Сейчас"
        const val NO_CANDIDATES = "В пуле нет подходящих слов."
        const val COLUMN_DAY = "День"
        const val COLUMN_WORD = "Слово"
        const val COLUMN_SOURCE = "Как выбрано"
        const val COLUMN_REPEAT = "Повтор"
        const val COLUMN_PICKED_BY = "Кто выбрал"
        const val FILTER_FROM = "С"
        const val FILTER_TO = "По"
        const val FILTER_SOURCE = "Как выбрано"
        const val FILTER_REPEATS = "Только повторы"
        const val ANY = "Все"
        const val RESET_FILTERS = "Сбросить фильтры"
        const val TABLE_EMPTY = "Дней с такими условиями нет."
        const val TABLE_HINT = "Без дат показаны дни с сегодня на 60 дней вперёд."

        val weekdays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

        fun calendar(id: String) = when (id) {
            "en" -> "Английский"
            "ru" -> "Русский"
            "kk" -> "Казахский"
            "uz" -> "Узбекский"
            else -> id
        }

        fun source(source: DaySource) = when (source) {
            DaySource.MANUAL -> MANUAL
            DaySource.AUTO -> AUTO
            DaySource.LEGACY -> LEGACY
        }

        fun sourceFilter(source: DaySource) = when (source) {
            DaySource.MANUAL -> "Вручную"
            DaySource.AUTO -> "Автоматически"
            DaySource.LEGACY -> "До календаря"
        }

        fun lock(lock: DayLock) = when (lock) {
            DayLock.PAST -> "прошедший день"
            DayLock.TODAY -> "сегодня"
            DayLock.TOMORROW -> "завтра"
            DayLock.OPEN -> ""
        }

        fun month(month: YearMonth): String {
            val names = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
            return "${names[month.month.ordinal]} ${month.year}"
        }

        /** "17 сент. 2026" for an ISO day. */
        fun shortDay(isoDay: String): String {
            val names = listOf("янв.", "февр.", "мар.", "апр.", "мая", "июн.", "июл.", "авг.", "сент.", "окт.", "нояб.", "дек.")
            val parts = isoDay.split("-")
            if (parts.size != 3) return isoDay
            val month = parts[1].toIntOrNull() ?: return isoDay
            val day = parts[2].toIntOrNull() ?: return isoDay
            return "$day ${names.getOrElse(month - 1) { "" }} ${parts[0]}"
        }

        fun repeatSince(isoDay: String?) = if (isoDay == null) REPEAT else "$REPEAT, было ${shortDay(isoDay)}"
        fun lastUsed(isoDay: String?) = if (isoDay == null) NEVER_USED else "было ${shortDay(isoDay)}"
        fun scheduledOn(isoDay: String, source: DaySource) = "стоит на ${shortDay(isoDay)} (${source(source)})"
        fun pickTitle(calendar: String, isoDay: String) = "$PICK_TITLE · ${calendar(calendar)} · ${shortDay(isoDay)}"
        fun usedOn(days: List<String>) = "Это слово уже было словом дня: ${days.joinToString(", ") { shortDay(it) }}."
        fun pickedOn(days: List<String>) = "Это слово уже выбрано на ${days.joinToString(", ") { shortDay(it) }}."
        fun pickedBy(name: String) = "выбрал(а) $name"

        fun notice(notice: CalendarNoticeDto): String {
            val why = when (notice.reason) {
                NoticeReason.REMOVED -> "слово удалено из словаря"
                NoticeReason.INELIGIBLE -> "слово убрано из пула"
            }
            return "${calendar(notice.calendar)}, ${shortDay(notice.day)}: «${notice.wordText}» заменено автоматически — $why."
        }

        fun reason(field: String, reason: String) = when (reason) {
            CalendarReasons.LOCKED -> "Этот день уже нельзя изменить: сегодня и завтра закрыты."
            CalendarReasons.TOO_FAR -> "Слово можно выбрать не дальше чем на год вперёд."
            CalendarReasons.NOT_ELIGIBLE -> "Этого слова нет в пуле слов дня."
            CalendarReasons.NOT_MANUAL -> "У этого дня нет выбранного вручную слова."
            WordReasons.PACK_INTEGRITY -> Words.reason(reason)
            else -> fieldReason(field, reason)
        }
    }

    object AnswerPool {
        const val TITLE = "Пул слов дня"
        const val SUBTITLE = "Из этих слов выбираются слова дня. Изменения сразу публикуются игрокам."
        const val UNUSED = "Ещё не были словом дня"
        const val UNUSED_HINT = "Слова пула, которые не выпадали с начала истории и не выбраны вручную на будущее."
        const val SEARCH = "Поиск"
        const val ADD = "Добавить слова"
        const val PASTE = "Вставить списком"
        const val NEW_PAIR = "Новая пара"
        const val COLUMN_WORD = "Слово"
        const val COLUMN_LATIN = "Латиница"
        const val COLUMN_CYRILLIC = "Кириллица"
        const val COLUMN_USAGE = "Использование"
        const val COLUMN_ACTIONS = "Действия"
        const val INACTIVE = "удалено из словаря"
        const val PAIR_INACTIVE = "слово пары удалено"
        const val UNMARK = "Убрать из пула"
        const val REMOVE_PAIR = "Удалить пару"
        const val EMPTY = "Пул пуст. Добавьте слова."
        const val EMPTY_PAIRS = "Пар пока нет. Создайте первую."
        const val MARKED = "Слово добавлено в пул."
        const val UNMARKED = "Слово убрано из пула."
        const val PAIR_CREATED = "Пара создана и добавлена в пул."
        const val PAIR_REMOVED_DONE = "Пара удалена из пула."
        const val UNMARK_TITLE = "Убрать слово из пула?"
        const val REMOVE_PAIR_TITLE = "Удалить пару?"
        const val CANDIDATES_TITLE = "Добавить в пул"
        const val CANDIDATES_HINT = "Активные слова словаря подходящей длины, которых ещё нет в пуле."
        const val NO_CANDIDATES = "Подходящих слов не найдено."
        const val MARK = "Добавить"
        const val PASTE_TITLE = "Добавить в пул списком"
        const val PASTE_LABEL = "Слова, по одному в строке"
        const val PASTE_HINT = "Каждое слово проверяется отдельно: подходящие добавятся, даже если другие не подойдут."
        const val PASTE_SUBMIT = "Добавить все"
        const val PASTE_AGAIN = "Добавить ещё"
        const val PAIR_TITLE = "Новая пара · узбекский"
        const val PAIR_LATIN_SEARCH = "Слово на латинице"
        const val PAIR_LATIN_HINT = "Активные слова без пары."
        const val PAIR_CHOOSE = "Выбрать"
        const val PAIR_CHOSEN = "Выбрано"
        const val PAIR_CYRILLIC = "Написание кириллицей"
        const val PAIR_CYRILLIC_HINT = "Предложено по правилам транслитерации — проверьте и исправьте при необходимости (например, ц, ь в заимствованиях)."
        const val PAIR_RESET = "Вернуть предложение"
        const val PAIR_NO_SUGGESTION = "Правила не смогли предложить написание — введите его сами."
        const val PAIR_CHECKING = "Проверяем словарь…"
        const val PAIR_ACTIVE = "Это слово есть в словаре."
        const val PAIR_MISSING = "Этого слова нет в словаре."
        const val PAIR_REMOVED = "Это слово было удалено из словаря."
        const val PAIR_ADD_TO_CATALOG = "Добавить слово в словарь"
        const val PAIR_RESTORE_IN_CATALOG = "Восстановить слово в словаре"
        const val PAIR_CREATE = "Создать пару"
        const val PAIR_CHOOSE_LATIN = "Сначала выберите слово на латинице."
        const val PAIR_NOT_CYRILLIC = "В написании есть буквы не из узбекской кириллицы."

        fun pairedWith(latin: String) = "Это слово уже в паре с «$latin»."
        fun unmarkBody(word: String) = "«$word» больше не будет выпадать словом дня. Сегодня и завтра не изменятся."
        fun removePairBody(latin: String, cyrillic: String) = "«$latin / $cyrillic» больше не будет выпадать словом дня. Сегодня и завтра не изменятся."

        fun outcome(outcome: MarkOutcome) = when (outcome) {
            MarkOutcome.MARKED -> "Добавлены"
            MarkOutcome.ALREADY_ELIGIBLE -> "Уже в пуле"
            MarkOutcome.NOT_IN_CATALOG -> "Нет в словаре"
            MarkOutcome.REMOVED -> "Удалены из словаря"
            MarkOutcome.UNSUPPORTED_LENGTH -> "Неподходящая длина"
        }

        /** A refused pool request; a pair conflict names the pair. */
        fun reason(field: String, reason: String): String = when {
            reason.startsWith(PoolReasons.PAIRED + " ") -> "Это слово уже в паре «${reason.substringAfter(' ').replace("/", " / ")}»."
            reason == PoolReasons.NOT_ACTIVE -> "Слово на латинице не активно в словаре."
            reason == PoolReasons.NOT_IN_CATALOG -> PAIR_MISSING
            reason == PoolReasons.REMOVED -> PAIR_REMOVED
            reason == PoolReasons.PAIRS_ONLY -> "Узбекский пул состоит из пар."
            reason == PoolReasons.TOO_MANY_ITEMS -> Words.BULK_TOO_MANY
            field == "cyrlText" || field == "latnWordId" || field == "pack" -> Words.reason(reason)
            else -> fieldReason(field, reason)
        }
    }

    object Players {
        const val TITLE = "Игроки"
        const val SUBTITLE = "Аккаунты игроков: поиск, статистика и предложения, действия поддержки."
        const val SEARCH = "Поиск"
        const val SEARCH_HINT = "ID аккаунта или часть имени"
        const val SEARCH_TOO_SHORT = "Введите хотя бы 2 символа имени или полный ID аккаунта."
        const val FILTER_TYPE = "Тип аккаунта"
        const val FILTER_FROM = "Создан с"
        const val FILTER_TO = "Создан по"
        const val FILTER_BLOCKED = "Только с заблокированными предложениями"
        const val RESET_FILTERS = "Сбросить фильтры"
        const val DATES_HINT = "Даты — по времени Ташкента."

        const val COLUMN_ACCOUNT = "Аккаунт"
        const val COLUMN_NAME = "Имя"
        const val COLUMN_SIGN_IN = "Вход"
        const val COLUMN_CREATED = "Создан"
        const val COLUMN_SUGGESTIONS = "Предложения"
        const val ANONYMOUS = "Анонимный"
        const val BLOCKED = "Заблокированы"
        const val EMPTY = "Игроков с такими условиями нет. Измените поиск или фильтры."
        const val LOAD_MORE = "Показать ещё"
        const val ALL_SHOWN = "Показаны все найденные."
        const val COPY_ID = "Скопировать ID"
        const val COPIED = "ID скопирован."
        const val DELETED = "Аккаунт удалён."

        fun shown(count: Int) = "Показано: $count"

        fun type(filter: TypeFilter) = when (filter) {
            TypeFilter.ALL -> "Все"
            TypeFilter.ANONYMOUS -> "Анонимные"
            TypeFilter.GOOGLE -> "Google"
            TypeFilter.APPLE -> "Apple"
        }

        fun provider(provider: OAuthProvider) = when (provider) {
            OAuthProvider.GOOGLE -> "Google"
            OAuthProvider.APPLE -> "Apple"
        }

        const val BACK = "Игроки"
        const val TITLE_UNNAMED = "Игрок без имени"
        const val NOT_FOUND = "Такого аккаунта нет: он удалён или ссылка неверна."
        const val FACT_ID = "ID аккаунта"
        const val FACT_CREATED = "Создан"
        const val FACT_NAME = "Имя"
        const val FACT_SIGN_IN = "Вход"
        const val FACT_SNAPSHOT = "Статистика синхронизирована"
        const val FACT_SESSIONS = "Активные сессии"
        const val NEVER_SYNCED = "ни разу"

        const val STATS_TITLE = "Статистика по языкам"
        const val STATS_HINT = "По правилам экрана статистики в приложении; текущая серия — на сегодняшний день языка."
        const val STATS_EMPTY = "Игрок ещё не синхронизировал статистику."
        const val COLUMN_LANGUAGE = "Язык"
        const val COLUMN_GAMES = "Игры"
        const val COLUMN_WINS = "Победы"
        const val COLUMN_WIN_RATE = "Процент побед"
        const val COLUMN_STREAK = "Серия"
        const val COLUMN_BEST_STREAK = "Лучшая серия"

        const val SUGGESTIONS_TITLE = "Предложения слов"
        const val RECENT_EMPTY = "Игрок ещё ничего не предлагал."
        const val COLUMN_WORD = "Слово"
        const val COLUMN_STATUS = "Статус"
        const val COLUMN_SUGGESTED = "Предложено"
        const val COLUMN_DECIDED = "Решено"

        fun counts(pending: Long, accepted: Long, rejected: Long) = "Ожидают: $pending · Приняты: $accepted · Отклонены: $rejected"
        fun recent(shown: Int) = "Последние $shown"
        fun winRate(rate: Float) = "${kotlin.math.round(rate * 100).toInt()}%"

        const val BLOCK_SECTION = "Предложения слов от аккаунта"
        const val NOT_BLOCKED = "Разрешены."

        fun blockedBy(name: String, time: String) = "Заблокированы: $name, $time."

        const val ACTIONS_TITLE = "Действия"
        const val CLEAR_NAME = "Очистить имя"
        const val BLOCK = "Заблокировать предложения"
        const val UNBLOCK = "Разблокировать"
        const val END_SESSIONS = "Завершить все сессии"
        const val DELETE = "Удалить аккаунт"

        const val CLEAR_NAME_TITLE = "Очистить имя игрока?"
        const val CLEAR_NAME_BODY = "Имя удалится с сервера и не сохранится в журнале. Новые предложения будут подписаны «Аноним»; уже отправленные в Telegram сообщения не изменятся, а на устройстве игрока имя может остаться."
        const val BLOCK_TITLE = "Заблокировать предложения?"
        const val BLOCK_BODY = "Новые предложения этого аккаунта будут отклоняться сразу: приложение покажет «Не удалось отправить», редакторы их не увидят. Уже отправленные предложения останутся на проверке. Игра и синхронизация не затрагиваются."
        const val UNBLOCK_TITLE = "Разблокировать предложения?"
        const val UNBLOCK_BODY = "Аккаунт снова сможет предлагать слова."
        const val END_SESSIONS_TITLE = "Завершить все сессии?"
        const val END_SESSIONS_BODY = "Приложение игрока больше не сможет продлить вход и перейдёт на новый анонимный аккаунт; уже выданный доступ действует ещё до 15 минут. Игрок с Google или Apple вернётся к этому аккаунту, войдя снова."
        const val DELETE_TITLE = "Удалить аккаунт навсегда?"
        const val DELETE_BODY = "Удалятся аккаунт, привязки входа, сессии и статистика, для Apple будет отозван доступ. Предложения останутся без автора. Удаление нельзя отменить."
        const val DELETE_CONFIRM = "Для подтверждения введите ID аккаунта"

        const val NAME_CLEARED = "Имя очищено."
        const val BLOCKED_DONE = "Предложения заблокированы."
        const val UNBLOCKED_DONE = "Предложения разблокированы."

        fun sessionsEnded(count: Int) = "Сессии завершены: $count."
    }

    object Account {
        const val TITLE = "Аккаунт"
        const val USERNAME = "Логин"
        const val ROLE = "Роль"
        const val LANGUAGES = "Языки"
        const val ALL_LANGUAGES = "Все языки"
        const val CHANGE_PASSWORD = "Смена пароля"
        const val CHANGE_PASSWORD_NOTE = "После смены пароля другие ваши сессии завершатся, эта останется."
        const val CURRENT_PASSWORD = "Текущий пароль"
        const val NEW_PASSWORD = "Новый пароль"
        const val REPEAT_PASSWORD = "Новый пароль ещё раз"
        const val SUBMIT = "Сменить пароль"
        const val DONE = "Пароль изменён. Вы остаётесь в панели."
    }

    object Analytics {
        const val TITLE = "Аналитика"
        const val SUBTITLE = "Сводные данные без персональной информации. Игроки — те, кто синхронизировал статистику; игровые дни считаются по часовому поясу языка, события — по Ташкенту."
        const val FILTER_FROM = "С"
        const val FILTER_TO = "По"
        const val FILTER_LANGUAGE = "Язык"
        const val ALL_LANGUAGES = "Все языки"
        const val DEFAULT_RANGE = "Последние 30 дней"
        const val NOT_BY_LANGUAGE = "Без фильтра по языку"
        const val PARTIAL = "неполные"
        const val PROVISIONAL = "уточняется"
        const val PROVISIONAL_SHORT = "уточняется"
        const val PROVISIONAL_NOTE = "Бледные значения ещё уточняются: игроки без сети синхронизируются позже. День становится окончательным через 7 дней после закрытия."
        const val SHOW_TABLE = "Таблицей"
        const val SHOW_CHART = "Графиком"
        const val COLUMN_LABEL = "День"
        const val NO_DATA = "Нет данных за выбранный период."
        const val EXPORT_CSV = "Скачать CSV"
        const val LOADING = "Загрузка…"

        fun rangeProblem(reason: String) = when (reason) {
            AnalyticsReasons.BEFORE_FROM -> "Дата «по» раньше даты «с»."
            AnalyticsReasons.RANGE_TOO_LONG -> "Период не может быть длиннее ${AnalyticsParams.MAX_DAYS} дней."
            else -> "Укажите обе даты периода."
        }

        // Overview
        const val OVERVIEW = "Обзор"
        const val KPI_DAU = "Игроков за день (DAU)"
        const val KPI_WAU = "За 7 дней (WAU)"
        const val KPI_MAU = "За 30 дней (MAU)"
        const val KPI_GAMES = "Партий за день"
        const val KPI_WIN_RATE = "Доля побед"
        const val KPI_NEW_ACCOUNTS = "Новых аккаунтов"
        const val KPI_TOTAL_ACCOUNTS = "Всего аккаунтов"
        const val KPI_BACKLOG = "Предложений ждут решения"
        const val TODAY = "Сегодня"

        fun dayOf(day: String?) = if (day == null) Common.NOT_SET else "за ${shortDate(day)}"
        fun delta(text: String) = "$text к предыдущему дню"
        fun todayTile(lang: String) = "$TODAY · ${Languages.label(lang)}"
        fun todayValue(players: String, games: String) = "$players игр. · $games парт."

        // Accounts
        const val ACCOUNTS = "Аккаунты"
        const val ACCOUNTS_TOTALS = "Аккаунты на конец дня"
        const val ACCOUNTS_FLOW = "Новые аккаунты и удаления"
        const val ACCOUNTS_LINKS = "Привязки Google и Apple"
        const val LINKED = "Привязанные"
        const val ANONYMOUS = "Анонимные"
        const val NEW_ACCOUNTS = "Новые"
        const val DELETIONS = "Удалённые"
        const val GOOGLE = "Google"
        const val APPLE = "Apple"
        const val ACCOUNTS_NOTE = "Итоги на конец дня есть с первого дня работы аналитики; привязки по дням — с момента, когда их время начали записывать."

        // Activity
        const val ACTIVITY = "Активность"
        const val PLAYERS_BY_LANGUAGE = "Игроки по языкам"
        const val ACTIVE_PLAYERS = "Активные игроки (DAU, WAU, MAU)"

        // Retention
        const val RETENTION = "Удержание"
        const val RETENTION_NOTE = "Когорта — аккаунты, чей первый результат пришёлся на этот день. D1, D7, D30 — доля тех, кто сыграл ровно через 1, 7 и 30 дней."
        const val COLUMN_COHORT = "Когорта"
        const val COLUMN_SIZE = "Размер"
        const val NOT_YET = "ещё нет"

        // Streaks and outcomes
        const val STREAKS = "Серии побед"
        val STREAK_BUCKETS = listOf("1", "2–6", "7–29", "30+")
        fun streaksOn(day: String) = "Текущие серии на ${shortDate(day)}"
        const val ACCOUNTS_SERIES = "Аккаунтов"
        const val OUTCOMES = "Результаты"
        const val DISTRIBUTION = "Распределение побед по попыткам"
        const val WINS = "Побед"
        const val WIN_RATE_BY_DAY = "Доля побед по дням"
        const val GAMES = "Партий"
        const val LOSSES = "Поражений"
        const val AVG_ATTEMPTS = "Среднее число попыток"

        // Word difficulty
        const val WORDS = "Сложность слов дня"
        const val COLUMN_DAY = "День"
        const val COLUMN_CALENDAR = "Календарь"
        const val COLUMN_WORD = "Слово"
        const val COLUMN_MARKER = "Выбор"
        const val COLUMN_PLAYERS = "Игроков"
        const val COLUMN_WIN_RATE = "Побед"
        const val COLUMN_ATTEMPTS = "Попыток"
        const val MARKER_MANUAL = "Вручную"
        const val MARKER_AUTO = "Автоматически"
        const val MARKER_REPEAT = "Повтор"

        // Suggestions
        const val SUGGESTIONS = "Предложения слов"
        const val COLUMN_DATE = "Дата"
        const val COLUMN_LANGUAGE = "Язык"
        const val COLUMN_SUBMITTED = "Пришло"
        const val COLUMN_AUTO = "Авто"
        const val COLUMN_EDITOR = "Редактор"
        const val COLUMN_REJECTED = "Отклонено"
        const val COLUMN_BACKLOG = "Ждут"
        const val COLUMN_MEDIAN_AUTO = "Медиана авто"
        const val COLUMN_MEDIAN_EDITOR = "Медиана редактора"

        // Content
        const val CONTENT = "Словарь и слова дня"
        const val ADDED_BY_SOURCE = "Добавлено слов по источникам"
        const val SHOW_BUNDLED = "Показывать словари из поставки"
        const val BUNDLED = "Словари из поставки"
        const val SUGGESTION = "Предложения"
        const val AUTO = "Автопринятие"
        const val STAFF_SOURCE = "Сотрудники"
        const val CURRENT_ACTIVE = "Слов в словаре сейчас"
        const val COLUMN_ACTIVE = "Слов"
        const val COLUMN_BUNDLED = "Поставка"
        const val COLUMN_SUGGESTION = "Предл."
        const val COLUMN_STAFF = "Сотр."
        const val COLUMN_REMOVED = "Удалено"
        const val COLUMN_RESTORED = "Возвращено"
        const val POOL = "Пул слов дня сейчас"
        const val POOL_SIZE = "в пуле"
        const val UNUSED = "не были словом дня"
        const val REPEATS = "повторов за период"
        fun poolDetails(unused: String, repeats: String, upcoming: String) = "$UNUSED: $unused · $REPEATS: $repeats · $UPCOMING_REPEATS: $upcoming"
        const val UPCOMING_REPEATS = "повторов впереди"

        // Staff
        const val STAFF = "Работа сотрудников"
        const val COLUMN_STAFF_MEMBER = "Сотрудник"
        const val COLUMN_ADDED = "Добавил"
        const val COLUMN_EDITED = "Изменил"
        const val COLUMN_REMOVED_BY = "Удалил"
        const val COLUMN_DECIDED = "Решений"
        const val TELEGRAM_UNLINKED = "Telegram без привязки"

        /** "18.08" from an ISO date. */
        fun shortDate(iso: String): String = if (iso.length == 10) "${iso.substring(8, 10)}.${iso.substring(5, 7)}" else iso
    }

    object Forbidden {
        const val TITLE = "Нет доступа"
        const val BODY = "Этот раздел недоступен для вашей роли. Если он нужен для работы, обратитесь к администратору."
        const val HOME = "На главную"
    }

    object NotFound {
        const val TITLE = "Страница не найдена"
        const val BODY = "По этому адресу ничего нет. Проверьте ссылку или перейдите на главную."
        const val HOME = "На главную"
    }

    /** The message for a `field: reason` error from the server or the form's own checks. */
    fun fieldReason(field: String, reason: String): String = when (reason) {
        FieldReasons.REQUIRED -> if (field == "languages") "Отметьте хотя бы один язык." else "Заполните поле."
        FieldReasons.INVALID -> when (field) {
            "username" -> "Только латинские буквы, цифры, точка, дефис и подчёркивание, от ${StaffRules.USERNAME_MIN} до ${StaffRules.USERNAME_MAX} символов."
            "telegramUserId" -> "Telegram ID состоит только из цифр."
            else -> "Неверное значение."
        }
        FieldReasons.TOO_LONG -> "Не длиннее ${StaffRules.DISPLAY_NAME_MAX} символов."
        FieldReasons.LENGTH -> "От ${StaffRules.PASSWORD_MIN} до ${StaffRules.PASSWORD_MAX} символов."
        FieldReasons.TAKEN -> when (field) {
            "username" -> "Этот логин уже занят."
            "telegramUserId" -> "Этот Telegram ID уже привязан к другому сотруднику."
            else -> "Значение уже используется."
        }
        FieldReasons.UNKNOWN -> "Для этого языка нет словаря."
        FieldReasons.WRONG -> "Текущий пароль указан неверно."
        FieldReasons.LAST_ADMIN -> Staff.LAST_ADMIN
        // "mismatch" is both the form's repeated password and a player deletion's confirmation (`confirmAccountId`).
        FormReasons.MISMATCH -> if (field == "confirmAccountId") "Введённый ID не совпадает с аккаунтом." else "Пароли не совпадают."
        PlayerReasons.TOO_SHORT -> Players.SEARCH_TOO_SHORT
        else -> "Неверное значение."
    }
}

/** Reasons only the panel's own checks produce. */
object FormReasons {
    const val MISMATCH = "mismatch"
}
