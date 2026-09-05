# План исправлений перед релизом Harf

Основание: [аудит от 2026-09-05](release-readiness-audit.md), commit `a8b62d5`. Документ описывает будущие изменения; исправления пока не выполнялись.

## Порядок работ

| Этап | Изменения | Что нужно до завершения этапа | Критерий готовности |
|---|---|---|---|
| 0. Зафиксировать релизные решения | Окончательные app IDs, первая платформа, окружения, покупки, правила объединения аккаунтов | Решения владельца из раздела ниже | Нет противоречий между кодом, OAuth clients, магазинами и backend |
| 1. Устранить падения и собрать приложения | Запрет Test Store ключей в release; безопасное поведение при недоступных покупках; восстановление iOS native linking и согласование архитектур; раздельная конфигурация dev/staging/prod | Для проверки реальных покупок — RevenueCat platform SDK keys. Локальная работа над защитой и линковкой возможна без них | Android release запускается; iOS-приложение линкуется и запускается; отсутствие сети не мешает игре |
| 2. Исправить серверные сессии и удаление | JSON challenge + обработка HTTP 401; один согласованный refresh; retries для защищённых операций; безопасное удаление; обязательный JWT secret; защита/TTL grace token; семантика access-token revocation | Можно начать локально без внешних ключей | Клиент против реального Ktor проходит expired access, concurrent refresh, logout, delete success и delete failure; при ошибке удаления данные/сессия сохраняются |
| 3. Исправить синхронизацию и аккаунты | Очередь ожидающих отправки результатов, retry после восстановления сети и foreground, согласование snapshot после конфликтов; серверное состояние после merge принимается до upload; запрет удаления уже связанного аккаунта через случайный merge | Подтверждённая политика account merge, уже описанная в OpenSpec, либо согласованное изменение | Два устройства сходятся по данным; офлайн-результат отправляется без новой игры; неуспешный pull после link не разрешает upload |
| 4. Довести офлайн-игру | Хранение завершённого раунда, корректное восстановление результата дня, интерфейс ошибок, прокрутка/крупный шрифт, локализация | Первые языки релиза; для контента — наборы слов и проверка носителями | Результат не сбрасывается при выходе/перезапуске, не создаются повторные результаты; основной сценарий доступен на небольшом экране |
| 5. Реализовать Google/Apple | Реальные платформенные OAuthClient, callback/nonce, Apple audience validation, Google audience config, logout UI, Apple code exchange и revoke при удалении | Google и Apple параметры из таблиц ниже | Реальные аккаунты проходят вход, отмену, повторный вход и второй девайс; чужая audience/неверный nonce отвергаются; удаление отзывает нужные credentials |
| 6. Развернуть staging и production | HTTPS API, внешняя конфигурация клиента, migrations, rate limits, секреты окружения, резервное копирование и проверка восстановления; технические health/readiness checks | Хостинг/домен, БД, доступ к настройке окружения | Мобильная сборка получает сессию без localhost/adb reverse; миграция и восстановление БД проверены |
| 7. Проверить покупки и подписывать артефакты | Play/App Store products ↔ RevenueCat entitlements; реальные цены; sandbox purchase/restore; release signing; versionCode; CI и артефакты; R8 по проектному плану | Store app IDs, SDK keys, product mapping, signing setup, тестовые аккаунты | Подписанный store-installed build запускается; покупка/restore отражаются в приложении; test keys не могут попасть в release |
| 8. Финальная приёмка | Регрессионный прогон JVM → Android → Web → iOS; IDE; Android min API/актуальный API, физический девайс; privacy/store declarations и listing | Доступные устройства/аккаунты, подтверждения из консолей | Все блокеры аудита закрыты проверками, внешние store gates подтверждены |

Этап 6 можно готовить одновременно с этапом 5. Реальный OAuth проверяется на staging до production. Временная неготовность внешних консолей не мешает исправлять этапы 1–4. Изменения группировать небольшими самостоятельными PR/коммитами; не объединять исправление refresh, merge, signing и словарей в один большой patch.

Публикация в магазинах и платные ресурсы — отдельные действия после готового проверяемого результата и соответствующего поручения.

## Решения, которые нужны от владельца

1. **Что выпускаем первым:** Android + iOS или сначала Android? Google нужен на Android/iOS; Apple в текущем плане — только iOS. Desktop/Web OAuth — отдельный объём, если также входит в первый релиз.
2. **Окончательные идентификаторы.** Сейчас Android `uz.abumme.harfgame.androidApp`, iOS `uz.abumme.harfgame.iosApp`. Релизный OpenSpec предлагает Android `uz.abumme.harfgame`. Если приложения уже созданы в магазинах, нужны их фактические IDs: менять их вслепую нельзя.
3. **Покупки в первом релизе:** включаем Founder/темы или выпускаем игру без активных покупок? Какие реальные функции входят в Founder? Перед продажей проверить, что обещанные archive/hard mode действительно доступны.
4. **Инфраструктура:** существующие hosting/DB/domain или требуется подобрать и настроить? При новом хостинге — допустимый месячный бюджет и предпочтительный регион.
5. **Аккаунты:** сохранить правило из текущего OpenSpec «при привязке к существующему аккаунту его серверные данные побеждают»? Что показывать пользователю перед потерей гостевых результатов? Уже связанный аккаунт не удалять автоматически.
6. **Первый контент:** языки интерфейса/игры, ответственный за редактуру словарей, желаемый срок до повторения ежедневных слов.

## Конкретные данные: Google

| Данные | Где взять / зачем | Секрет? |
|---|---|---|
| Google Cloud project ID | Проект, которому принадлежат OAuth clients; нужен для настройки consent и проверки согласованности | Нет |
| Web OAuth client ID, вида `…apps.googleusercontent.com` | Google Auth Platform → Clients; используется как server client ID/audience для Android-входа в собственный backend | Нет |
| Android OAuth client с финальным package + SHA-1 | Google Auth Platform → Clients; зарегистрировать пары package/certificate для нужных вариантов установки | Нет |
| SHA-1 Play App Signing certificate | Play Console → App integrity/App signing; нужен для входа в приложении, установленном из Play | Нет |
| iOS OAuth client ID | Client типа iOS с финальным bundle ID; нужен для Google Sign-In iOS | Нет |
| Consent/branding/audience status | Название приложения, support email, разрешённая аудитория; список test users, если OAuth остаётся в testing | Настройки — нет; адреса тестировщиков — личные данные |
| Тестовый Google-аккаунт на устройстве | Пользователь входит самостоятельно при проверке | Пароль/коды передавать не нужно |

Локальные debug/release SHA-1 можно получить из keystore/signingReport — вручную искать их не нужно, если есть локальные файлы. Reversed iOS client URL scheme выводится из Google iOS client ID. Android client ID сам по себе не заменяет Web/server client ID.

Для выбранного native ID-token flow Google client secret в мобильное приложение не нужен. Для отдельного desktop browser/code flow набор параметров уточняется отдельно.

Основания: [Android implementation](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation), [package/SHA-1 и клиенты](https://codelabs.developers.google.com/sign-in-with-google-android), [iOS setup](https://developers.google.com/identity/sign-in/ios/start-integrating).

## Конкретные данные: Apple

| Данные | Где взять / зачем | Секрет? |
|---|---|---|
| Apple Developer Team ID | Membership details; для signing и server client-secret JWT | Нет |
| Финальный bundle ID / App ID | Certificates, Identifiers & Profiles; тот же ID в Xcode и серверном audience allowlist | Нет |
| Sign in with Apple capability | Включена у App ID; корректные entitlements/provisioning profile | Нет |
| Sign in with Apple Key ID | Идентификатор ключа, предназначенного для Sign in with Apple | Нет |
| Путь к private `.p8` key или secret-manager reference | Для серверного обмена authorization code и token revocation при удалении | **Да** |
| iOS signing setup | Выбранная Team и доступная Xcode signing identity/profile; для устройства/TestFlight | Private signing key — **да** |
| Services ID + return URL + domain | Только если добавляем Apple web/browser flow вне native iOS | IDs/URL — нет |

Ключ **Sign in with Apple** и ключ **App Store Connect API** — разные назначения. Для нативного входа нельзя автоматически подставить App Store Connect key вместо SIWA key. App Store Connect API key понадобится отдельно, только если автоматизируем загрузку сборок через API.

Основания: [Apple configuration](https://developer.apple.com/documentation/signinwithapple/configuring-your-environment-for-sign-in-with-apple), [private key](https://developer.apple.com/help/account/capabilities/create-a-sign-in-with-apple-private-key), [удаление и отзыв](https://developer.apple.com/documentation/technotes/tn3194-handling-account-deletions-and-revoking-tokens-for-sign-in-with-apple).

## Конкретные данные: backend и окружения

| Данные | Что требуется |
|---|---|
| Staging и production API URL | Например, выбранные вами HTTPS-домены; если домен не создан — указать владельца DNS и возможность настройки |
| Хостинг | Название сервиса/проект либо SSH host + username + путь к уже настроенному SSH key; предпочтительно отдельный доступ к конкретному приложению |
| PostgreSQL | Host, port, database, username, TLS requirements; пароль через secret manager/локальный защищённый файл |
| Existing data | Есть ли реальные пользователи/покупки/результаты? Нужны ли миграция существующей БД и сохранение текущих app IDs? |
| Секреты окружений | Место хранения/настройки для `DB_PASSWORD`, `JWT_SECRET` и Apple key; отдельные значения staging/prod |
| CI | Где расположен Git remote/CI и где хранить secrets; доступ к сборочным артефактам |
| Backup | Возможности managed backup либо место хранения, срок хранения и доступ для проверки восстановления |

`JWT_SECRET` можно сгенерировать при реализации — заранее придумывать его не нужно. `JWT_ISSUER`, `JWT_AUDIENCE`, размер пула и внутренний порт можно согласованно задать в конфигурации; это не пользовательские учётные данные.

Уже поддерживаемые сервером env: `DB_JDBC_URL`, `DB_USER`, `DB_PASSWORD`, `DB_MAX_POOL_SIZE`, `PORT`, `JWT_SECRET`, `JWT_ISSUER`, `JWT_AUDIENCE`, `GOOGLE_CLIENT_IDS`. Client base URL, Apple allowlist/nonce/code exchange пока требуют изменений кода; одного добавления новых env сейчас недостаточно.

## Конкретные данные: покупки и выпуск

| Данные | Что требуется |
|---|---|
| RevenueCat project и app references | Найти существующие настройки Android/iOS, а не создавать дубликаты |
| Android и iOS public SDK keys | Ключи реальных Play/App Store apps; текущие Test Store keys для release непригодны. Public SDK keys допустимы в клиенте |
| Offering ID и package → product mapping | Точные store product IDs для Android/iOS и привязка к RevenueCat packages |
| Entitlement mapping | Код сейчас ожидает Founder `harf_founder`; темы — entitlement IDs с префиксом `theme_`. Product ID и entitlement ID — разные сущности |
| Продукты и цены | Какие покупки одноразовые, что дают, цены/валюты/регионы; состояние продуктов в Play Console и App Store Connect |
| Store integration status | Подключены ли Play/App Store к RevenueCat, загружены ли нужные store credentials в его dashboard |
| Android signing | Есть ли upload keystore? Нужны путь, alias и ссылки на секреты паролей. Если нет — создать при настройке signing. Private Play App Signing key запрашивать не нужно |
| Store test setup | Play internal testing/license testers, Apple sandbox/TestFlight, устройства. Пароли и коды владелец вводит самостоятельно |
| Store pages | Privacy policy URL, support email, отображаемое имя/издатель, языки листинга, состояние обязательных соглашений/проверок в консолях |

RevenueCat secret API key не следует помещать в приложение. Для настройки магазинов можно работать через уже авторизованную консоль; пересылка raw service-account keys в чат не нужна.

## Что можно подготовить без внешних данных

- Исправления 401/refresh, удаления, очереди sync и сохранения завершённого раунда.
- Серверные проверки конфигурации, Apple audience/nonce contract, формат ошибок и регрессионные тесты.
- Ограничение test keys в release и исправление текущей iOS-линковки.
- Шаблоны конфигурации без реальных секретов, получение локальных signing fingerprints.
- Каркас реального OAuthClient с незаполненными публичными client IDs; настоящий вход проверяется после настройки провайдеров.

## Короткая форма для начала

```text
Первый релиз: Android / Android+iOS / другое
Приложения уже созданы в магазинах: да/нет; ссылки или IDs
Android applicationId:
iOS bundle ID:
Покупки в первом релизе: да/нет; что продаём
Backend/БД уже размещены: где / ещё нет
Домен API или домен, которым владею:
Бюджет/регион для нового хостинга, если нужен:
Google Cloud project ID:
Google Web client ID:
Google iOS client ID:
Play App Signing SHA-1: есть / приложение ещё не создано
Apple Team ID:
Apple SIWA Key ID и путь к .p8: есть / ещё не создан
RevenueCat project/apps и public SDK keys: есть / ещё не настроены
Upload keystore: путь / ещё нет
Реальные пользователи/данные уже есть: да/нет
Первые языки релиза:
```

Допустимый ответ для отсутствующих настроек — «ещё нет». Публичные client IDs, bundle IDs и отпечатки сертификатов можно передать текстом. Пароли, JWT secret, private `.p8`, private signing keys и service-account JSON хранить в secret manager или локальном защищённом файле; передавать только путь/ссылку на секрет, не содержимое.
