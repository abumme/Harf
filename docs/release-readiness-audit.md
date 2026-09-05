# Проверка готовности Harf к релизу

Дата: 2026-09-05. Режим: исследование по `.junie/commands/opsx-explore.md`, без изменения кода приложения.

## План и критерии проверки

- [x] 1. Архитектура: модули, платформы, зависимости, OpenSpec, незавершённые задачи. Сопоставить документацию с кодом.
- [x] 2. Бэкенд: запуск, конфигурация, PostgreSQL, схема и миграции, API, сессии, синхронизация. Проследить путь от UI до сервера. Проверить локально доступные сценарии.
- [x] 3. Google/Apple: получение ID token на клиенте, проверка сервером, привязка аккаунта, обновление сессии, выход и удаление. Составить точный список настроек провайдеров по официальной документации.
- [x] 4. Проверки: IDE MCP, JVM и тесты бэкенда, Android debug/release, Wasm/JS, iOS. Зафиксировать команды, результаты и ограничения среды.
- [x] 5. Android QA: Android CLI/adb, установка, онбординг, игра, статистика, настройки, авторизация, покупки, перезапуск; UI и crash/logcat. Работать с тестовым эмулятором.
- [x] 6. Отчёт: подтверждённые блокеры релиза, ссылки на код, непроверенные внешние настройки, последовательность исправлений и критерии повторной приёмки.

## Правила доказательности

- Отделять обнаруженное в коде от проверенного запуском и неизвестного без доступа к внешним сервисам.
- Не считать успешные mock-тесты доказательством работающего Google/Apple входа.
- Не публиковать секреты и токены в отчёте.
- Не менять production-данные, не публиковать сборки, не проводить реальные покупки.
- При ограничениях среды выполнить доступные проверки и явно указать пробелы.

## Результаты

## Вердикт

**Текущая версия не готова к релизу.** Проверен commit `a8b62d5`, с существующими локальными настройками. Android debug работает как прототип офлайн-игры. Android release падает при старте; полное iOS-приложение не линкуется. Google/Apple вход на клиенте заменён заглушками. Серверная основа существует и работает локально, но связка клиента с сервером содержит ошибки сессий, синхронизации и удаления.

Код приложения не изменялся. Проверки выполнены на отдельной временной БД и временном профиле Android. Отметка выполнения плана означает завершённую проверку, а не исправление найденных проблем.

## 1. Как устроен проект

```text
Android / Desktop / iOS / Web entry point
  → sharedUI: Compose + Koin + игровые ViewModel
      → WordPack / Scorer / DailyPuzzle: офлайн-игра
      → KSafe: настройки, результаты, текущий раунд, сессия
      → SyncManager → KtorAuthService / KtorSyncService
          → sharedData: DTO, маршруты, контракты
              → backend: Ktor Netty → сервисы → Exposed / HikariCP → PostgreSQL
      → RevenueCat: отдельный сервис покупок
```

Разделение `sharedData` / `backend` / `sharedUI` разумное: сервер и клиент используют общие контракты; доменная игровая логика тестируема и работает без сети. Версии зависимостей вынесены в каталог. Wrapper-модули остаются тонкими.

Однако документация отстаёт: `AGENTS.MD` всё ещё требует держать всё в `sharedUI`, хотя отдельные сервер и контракты уже созданы. В `harf-backend-foundation/tasks.md` отмечены выполненными нативный OAuth, post-link ordering, foreground retry и logout UI, но код не реализует эти пункты полностью. В `harf-play-release/tasks.md` открыты все 16 задач.

## 2. Результаты сборок и тестов

| Проверка | Результат |
|---|---|
| IDE MCP | Поиск `linkAccount`; инспекции `SyncManager`, `KtorServices`, `GameScreen`, `AppleOAuthVerifier`, `AuthServerService`: диагностик нет. Call-hierarchy tool в текущем наборе MCP отсутствует; связи проверены чтением вызовов. |
| JVM: sharedUI + desktopApp | Компиляция успешна. |
| sharedUI jvmTest, повторный запуск | **74/74**, без ошибок и пропусков. |
| sharedData jvmTest, повторный запуск | **5/5**, без ошибок и пропусков. |
| backend test, повторный запуск | **17/17**, без ошибок и пропусков, на изолированной PostgreSQL. |
| Android assembleDebug | Успешно, APK установлен и проверен. |
| Android bundleRelease / assembleRelease | Успешно, но AAB не подписан; release APK падает при запуске. |
| Android lintDebug | Успешно, 0 issues в XML-отчёте. |
| Web compileKotlinWasmJs / compileKotlinJs | Успешно. Проверена компиляция, не браузерный runtime. |
| iOS compileKotlinIosArm64 / IosSimulatorArm64 | Успешно. Это не полная сборка iOS-приложения. |
| Xcode generic simulator | Ошибка: запрошена отсутствующая Gradle-цель `ios_x64`. |
| Xcode simulator, явно ARCHS=arm64 | Ошибка линковки RevenueCat / PurchasesHybridCommon / PurchasesHybridCommonUI и связанных символов. |

Основные команды:

```sh
./gradlew :sharedUI:compileKotlinJvm :desktopApp:compileKotlin :sharedData:jvmTest :sharedUI:jvmTest :backend:test
./gradlew :sharedData:jvmTest :sharedUI:jvmTest --rerun-tasks
DB_JDBC_URL=jdbc:postgresql://127.0.0.1:55439/harf_audit DB_USER=harf DB_PASSWORD=harf_audit ./gradlew :backend:test --rerun-tasks
./gradlew :androidApp:assembleDebug :androidApp:bundleRelease :androidApp:lintDebug
./gradlew :webApp:compileKotlinWasmJs :webApp:compileKotlinJs
./gradlew :sharedUI:compileKotlinIosSimulatorArm64 :sharedUI:compileKotlinIosArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -derivedDataPath /tmp/harf-release-audit/xcode CODE_SIGNING_ALLOWED=NO build
# Повтор с ARCHS=arm64 ONLY_ACTIVE_ARCH=YES дошёл до ошибки линковки.
```

Docker daemon не работал. Использована установленная PostgreSQL 12 в `/tmp/harf-release-audit/pgdata`, отдельный порт 55439. **PostgreSQL 16 из docker-compose этим прогоном не проверена.** Backend-тесты вызывают `UsersTable.deleteAll()` — их нельзя направлять на рабочую БД.

## 3. Подтверждённые блокеры

### B1. Android release падает при старте

В локальных `revenuecat.androidKey` и `revenuecat.iosKey` стоят ключи **Test Store**. Значения не публикуются. Они попадают и в release через `sharedUI/build.gradle.kts:138`. Инициализация `RevenueCatPurchaseController` вызывает `Purchases.configure` без обработки ошибки конфигурации.

Release APK подписан локальным debug-сертификатом только для установки; флаг `DEBUGGABLE` у приложения отсутствовал. Дважды получены `FATAL EXCEPTION: main`, Koin `InstanceCreationException` для `PurchaseController` и `PurchasesException`. SDK перед этим прямо предупреждает о Test Store в production.

Доказательство: [android-release-crash.txt](release-audit-evidence/android-release-crash.txt). Исправление: отдельные реальные публичные SDK-ключи Play/App Store, запрет Test Store в release на этапе сборки, корректная настройка продуктов и entitlements. Проверить store sandbox на release-сборке. Это блокер текущей локальной конфигурации; он не означает, что все возможные внешние CI-настройки содержат те же ключи.

### B2. Полное iOS-приложение не собирается

Для текущего `purchases-kmp = 2.2.2+17.10.0` не подключены необходимые нативные frameworks в Xcode. Ошибки: `PurchasesHybridCommon`, `PurchasesHybridCommonUI`, `RevenueCat`, `RevenueCatUI`; undefined symbols `RCCommonFunctionality`, `RCPaywallViewController` и др. Дополнительно generic simulator запрашивает `ios_x64`, которого нет в Gradle.

Доказательство: [ios-link-errors.txt](release-audit-evidence/ios-link-errors.txt). Исправление: согласовать нативную интеграцию с используемой версией SDK и целевые архитектуры. У RevenueCat 3.x процесс интеграции изменён; нельзя смешивать инструкции 3.x с текущей 2.x. [Описание изменения у RevenueCat](https://www.revenuecat.com/blog/engineering/kmp-sdk-3).

### B3. Google/Apple вход не реализован на клиенте

`sharedUI/.../data/auth/OAuthClient.kt:11`: оба метода `NoOpOAuthClient` возвращают `null`. Именно этот класс зарегистрирован на Android (`PlatformModule.android.kt:22`), iOS и JVM; флаги лишь показывают кнопки. На Android нажатие **Link Google Account** оставило UI неизменным: не открылся провайдер, нет ошибки или прогресса.

Настройка OAuth Console сама по себе это не исправит. Нужны реальные платформенные реализации, обработка результата, ошибок/отмены, доступная сессия Harf и запрос `/auth/link`. Отдельного logout-действия в Settings нет, хотя серверный endpoint существует.

### B4. Клиент не настроен на сервер окружения

`KtorServices.kt:20,132` жёстко задаёт `http://localhost:8080`; Koin не передаёт другой URL. На устройстве это само устройство. README предлагает сменить host, но готового build/runtime параметра для этого нет. Production HTTPS URL или конфигурация деплоя в репозитории не найдены.

Через временный `adb reverse tcp:8080 tcp:58089` неизменённый Android-клиент действительно создал аккаунт на тестовом сервере. Значит, HTTP-адаптер работает при доступном адресе, но поставляемая конфигурация не соединяет мобильное приложение с production.

Нужен явный base URL для dev/staging/prod; для Android emulator — host mapping либо reverse, для production — HTTPS. `INTERNET` в итоговом merged manifest **есть** благодаря зависимостям. Не путать это с отсутствием явного permission в исходном manifest. Правила localhost на API 37 отличаются от старых Android; сетевую конфигурацию нужно проверить и на нижних поддерживаемых API. [Android Network Security Configuration](https://developer.android.com/privacy-and-security/security-config).

### B5. Автоматическое обновление access token ломается на настоящем 401

`backend/Application.kt:52` не задаёт JSON challenge. Реальный запрос `/api/v1/sync/stats` без токена или с неверным токеном вернул **HTTP 401, пустое тело, без Content-Type**.

Клиент в `KtorServices.kt:146,162` сначала делает `body<ApiErrorResponse>()`. Декодирование пустого ответа падает; `executeWithAuthRetry` возвращает `NETWORK_ERROR`. Ветка refresh на строке 178 не выполняется. При обычном истечении access token через 15 минут синхронизация перестанет работать. MockEngine-тест маскирует проблему: он вручную возвращает JSON с `error=unauthorized`.

Исправление: единый серверный формат ошибок и обработка HTTP 401 самим клиентом до JSON-декодирования; общий refresh/retry для link/delete/logout; сериализация параллельных refresh. Проверить клиент против реального Ktor, включая истёкший access token.

### B6. Удаление аккаунта скрывает ошибку сервера

`KtorServices.kt:112` очищает сессию даже при неуспешном DELETE. `SyncManager.kt:89` игнорирует ответ, очищает локальную статистику, запускает bootstrap и всегда возвращает `Success`.

Проверено отдельно: после остановки тестового сервера подтверждение Delete закрылось без сообщения об ошибке; аккаунт остался в БД (1 запись). При доступном сервере удаление работает, после него bootstrap создаёт новый анонимный аккаунт. Ошибочная ветка опасна тем, что пользователь считает запрос выполненным, а данные остаются на сервере; локальная сессия теряется.

Исправление: сохранять сессию и локальные данные до подтверждённого server success; показывать повторяемую ошибку; очищать также соответствующее состояние раундов и определить желаемый режим после удаления. Для Apple требуется отдельный отзыв Apple-токенов, которого сейчас нет.

### B7. Офлайн-результаты не отправляются при восстановлении связи

`SyncManager.bootstrap()` делает только pull. `pushStats()` вызывается из завершения раунда; нет durable pending state, повторной отправки при запуске/foreground и обработки ошибки upload.

Проверено на Android: после двух локальных результатов и восстановления соединения через reverse в PostgreSQL появились **1 user, 1 refresh token, 0 snapshots**. Только после следующего завершения раунда появился snapshot с **2 результатами**. До нового завершения игры локальные результаты не синхронизировались.

### B8. Привязка аккаунта нарушает правило server-wins

`SyncManager.linkAccount()` вызывает общий `pullStats()`, а тот выполняет `ResultLog.merge`, сохраняя локальные результаты и более новый локальный timestamp. `ResultLog.replace()` есть, но здесь не используется. Ошибка pull не препятствует возврату Success. Следующий upload может отправить локальную смесь в уже существующий аккаунт вместо принятия серверного snapshot.

На сервере `AuthServerService.kt:166` удаляет текущий аккаунт при совпадении identity с другим владельцем, не проверяя, что текущий аккаунт действительно анонимный. Нужно определить и защитить сценарий уже связанного аккаунта. Эти ветки проверены чтением кода; реальная привязка двух OAuth-аккаунтов не прогонялась из-за клиентских заглушек.

### B9. Apple token verification не проверяет audience

`AppleOAuthVerifier.kt:30–39` проверяет подпись, issuer и срок действия, но не требует `aud` и не сопоставляет его с App/Services ID. Даже существующий положительный тест создаёт токен без audience и проходит. Верификация токена для другого приложения не должна давать вход в Harf. Nonce также не предусмотрен текущим DTO/контрактом.

До публичного запуска: обязательные `aud`/`iss`/`exp`/`sub`, разрешённые алгоритмы, связывание запроса с проверяемым nonce и отрицательные тесты на чужую audience. Подпись Apple сама по себе недостаточна.

### B10. Сервер допускает небезопасную production-конфигурацию

`JwtService.kt:11` молча использует известный dev-secret при отсутствии `JWT_SECRET`. С такой конфигурацией access token можно подписать вне сервера. Нужна обязательная проверка секретов при старте production; dev defaults допустимы только в явном dev-режиме.

`AuthServerService.kt:123` хранит replacement refresh token открытым текстом в `graceReplacementToken`. Поле не очищается после 60-секундного grace window. Заявление «refresh tokens хранятся только хешами» поэтому неверно. Нужны защита временного значения и гарантированное удаление по TTL.

Дополнительно: нет видимой политики rate limits для anonymous/link/refresh, версионированных миграций, retention/очистки токенов, deployment/backup/restore конфигурации. `SchemaUtils.createMissingTablesAndColumns` на старте не заменяет управляемую миграцию. `StatusPages` отдаёт клиенту `cause.message`; детали внутренних ошибок следует оставлять в серверных логах. Внешняя инфраструктура могла бы закрывать часть этих пунктов, но доступ к ней не предоставлен.

### B11. Завершённый ежедневный раунд открывается заново пустым

`GameScreen.kt:109` удаляет сохранённый раунд при завершении. На строке 151 сохраняются только раунды со статусом Playing. Повторный вход не восстанавливает оконченный раунд из `ResultLog`.

Проверено: English → APPLE → STONE → **Solved in 2/6** → выход → повторный вход → пустая доска и активная клавиатура. При этом статистика уже показывает played=1, win=100%, streak=1. Нужно сохранять завершённое состояние и открывать результат дня без новой игры либо явно проектировать отдельный режим повтора.

Доказательства: [победа](release-audit-evidence/android-win.png), [сброшенный раунд](release-audit-evidence/android-finished-round-reset.png).

### B12. Релизный пакет и контент ещё не подготовлены

- AAB не имеет signature entries. Нет release signing config и CI публикации артефакта; versionCode фиксирован в 1. `applicationId=uz.abumme.harfgame.androidApp`, в релизном плане требуется `uz.abumme.harfgame`. Определить окончательный ID **до** настройки OAuth и магазинов.
- Нет настроенного R8/resource shrinking, предусмотренного проектным релизным планом. Это не само по себе запрет магазина, но релизная конфигурация и её поведение ещё не прошли приёмку.
- Ежедневные наборы: узбекский — **5** парных лексем, казахский — **16**, русский — **34**, английский — **38** ответов. Индексация `day mod size` даёт повтор узбекского ответа каждые 5 дней. Нужны полноценные проверенные носителями наборы и более широкий словарь допустимых ответов.
- Settings, Home и часть интерфейса содержат hardcoded English; наличие языков игры не означает локализованный продукт.
- Settings не прокручивается: проверить небольшой экран, landscape и увеличенный шрифт перед релизом. На протестированном Pixel портретный экран помещается.
- Privacy policy URL, магазинные декларации, подписи, sandbox IAP, реальные цены и storefront configurations не подтверждены. Тестовый paywall показывает `$99.99`; реальные покупки не проводились.

## 4. Что требуется для Google/Apple входа

### Общая обязательная цепочка

1. Определить окончательные Android applicationId, iOS bundle ID и HTTPS адрес API.
2. Настроить и развернуть сервер, обязательные production secrets, БД и проверки доступности.
3. Реализовать платформенный OAuthClient вместо NoOp: вернуть настоящий ID token или типизированную отмену/ошибку.
4. Получить/восстановить сессию Harf; отправить ID token на `/api/v1/auth/link` с её access token. Исправить refresh/retry и серверную верификацию.
5. Сохранить новую сессию, безопасно принять серверные данные при merge. Проверить повторный вход, второй девайс, logout и удаление.

### Google на Android

- Google Cloud / Google Auth Platform: OAuth consent/branding/audience, нужные тестовые пользователи или production publishing.
- Android OAuth client: окончательное имя пакета и SHA-1 сертификата. Отдельно учесть debug, локальный release и **Play App Signing certificate**; upload certificate не подменяет подпись установленного из Play приложения.
- Web application client ID для серверной audience. Передать его как `serverClientId` в Credential Manager; включить ожидаемый ID в серверный `GOOGLE_CLIENT_IDS`. Этот env — уже реализованный список через запятую.
- В `sharedUI/androidMain` подключить Credential Manager / Google ID зависимости через version catalog, получить `GoogleIdTokenCredential.idToken`, обработать отсутствие credentials и отмену. Не помещать client secret в APK.
- Реализовать выход с очисткой credential state и сессии Harf. Проверить настоящий Play-signed build.

Основания: [Google Android codelab — package/SHA-1 и типы клиентов](https://codelabs.developers.google.com/sign-in-with-google-android), [Credential Manager implementation — serverClientId, token, nonce, logout](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation).

### Google на iOS

- iOS OAuth client с окончательным bundle ID.
- Google Sign-In SDK, `GIDClientID`, reversed-client-ID URL scheme и обработчик callback/openURL. Для проверки на сервере настроить server client ID и соответствующую audience.
- Текущий `Info.plist` содержит только настройку частоты кадров; OAuth-настроек и интеграции Google в нём нет.

Основание: [Google Sign-In iOS setup](https://developers.google.com/identity/sign-in/ios/start-integrating).

### Apple на iOS

- Apple Developer App ID с окончательным bundle ID, capability **Sign in with Apple**, корректные entitlements и provisioning profile.
- Реальная интеграция `AuthenticationServices`/`ASAuthorizationAppleIDProvider` на iOS, получение identity token и authorization code, обработка отмены и ошибок.
- Серверный allowlist audience для bundle ID. Сейчас такого параметра у `AppleOAuthVerifier` нет: его нужно добавить, а не только установить env.
- Nonce, проверяемый сервером и связанный с запросом; валидация Apple JWKS, issuer, audience, expiration и subject.
- Для серверного обмена authorization code и отзыва токенов: Team ID, Key ID, private `.p8` key и генерация `client_secret` на сервере. Private key никогда не включать в приложение. Текущий контракт принимает лишь `idToken`; его нужно расширить для выбранного полноценного жизненного цикла.
- При удалении аккаунта отзывать Apple-токены. Сейчас удаляются только собственные таблицы Harf.
- Services ID, домен и return URL нужны при добавлении web/Android browser-flow. Для текущего native-iOS-only Apple сценария не следует подменять bundle audience случайным Services ID.

Основания: [настройка окружения Apple](https://developer.apple.com/documentation/signinwithapple/configuring-your-environment-for-sign-in-with-apple), [аутентификация и nonce](https://developer.apple.com/documentation/signinwithapple/authenticating-users-with-sign-in-with-apple), [серверный private key](https://developer.apple.com/help/account/capabilities/create-a-sign-in-with-apple-private-key), [удаление и revoke — TN3194](https://developer.apple.com/documentation/technotes/tn3194-handling-account-deletions-and-revoking-tokens-for-sign-in-with-apple).

### Настройки сервера

| Уже читается кодом | Что нужно задать |
|---|---|
| `DB_JDBC_URL`, `DB_USER`, `DB_PASSWORD`, `DB_MAX_POOL_SIZE` | Отдельные staging/prod PostgreSQL, доступ и размер пула. |
| `PORT` | Порт процесса; публичный HTTPS обычно завершается на ingress/reverse proxy. |
| `JWT_SECRET`, `JWT_ISSUER`, `JWT_AUDIENCE` | Случайный production secret и согласованные issuer/audience, без молчаливого dev fallback. |
| `GOOGLE_CLIENT_IDS` | Только ожидаемые audience IDs для фактических flows. |

**Ещё нет конфигурационного контракта:** client API base URL, Google platform client IDs, Apple allowed audiences, nonce flow, Apple code exchange/revoke credentials. Имена новых параметров следует определить при реализации; выдуманный env сейчас ничего не включит.

## 5. Что реально проверено в приложении и API

Android CLI **1.0.15857036**, AVD **Pixel_10a**, Android **17 / API 37**, `emulator-5554`, отдельный временный пользователь 10.

| Сценарий | Факт |
|---|---|
| Чистый профиль → онбординг → Start | Работает. |
| Home → Settings | Работает, Google доступен как кнопка, Apple на Android скрыт. |
| Google button | Никакого перехода/результата; UI до и после совпадает. |
| Support Harf | Тестовый RevenueCat paywall, Lifetime и две темы по $99.99; покупка не совершалась. |
| Неверное слово ZZZZZ | Отклоняется, сообщение Not in word list. |
| English: APPLE, затем перезапуск, STONE | Первый ход восстановился; победа 2/6. |
| Statistics | English played=1, win%=100, streak=1. |
| Share | Системный chooser содержит корректную текстовую сетку 2/6; получатель не выбран, сообщение не отправлено. |
| Повторный вход в завершённый English | Баг: пустой раунд. |
| Отключены Wi-Fi и mobile data → перезапуск → Uzbek BAHOR | Работает офлайн, победа 1/6. [Скриншот](release-audit-evidence/android-offline-win.png). |
| Android → локальный backend через adb reverse | Автоматически создана сессия, затем при завершении раунда в БД отправлены 2 результата. |
| Восстановление связи без нового раунда | Сессия создаётся, результаты сами не загружаются на сервер. |
| Delete при работающем сервере | Серверные данные удаляются, локальная статистика очищается, создаётся новая anonymous session. |
| Delete после остановки сервера | Диалог закрывается без ошибки; запись аккаунта остаётся в БД. |
| Android release start | Два воспроизводимых падения RevenueCat configuration. Debug затем восстановлен. |

HTTP-проверки на `127.0.0.1:58089`: health-like root 200; anonymous 201; authenticated GET/POST stats 200; refresh 200 с ротацией; invalid Google token 400; DELETE 200; refresh после DELETE 401. Access JWT после удаления ещё получает GET stats 200 с пустым snapshot: JWT-проверка не проверяет существование пользователя. Это не доказательство доступа к удалённым данным, но границы отзыва access token требуют явного решения.

UI-доказательства: [android-ui.txt](release-audit-evidence/android-ui.txt). Полные технические логи текущего прогона находятся в `/tmp/harf-audit-*.log`; в репозиторий включены только отобранные доказательства без токенов.

## 6. Порядок исправлений и повторная приёмка

1. **Сделать выпускаемый артефакт работоспособным:** реальные RevenueCat SDK keys, защита release от test keys; восстановить iOS linking; финальные app IDs, signing и CI. Приёмка: signed Android release стартует, iOS app линкуется и запускается.
2. **Настроить окружения бэкенда:** HTTPS/base URL, обязательные secrets, управляемые миграции, backup/restore, логирование и ограничения запросов. Приёмка: новый release-клиент сам получает сессию в staging без adb reverse.
3. **Закрыть auth/session ошибки:** Apple audience/nonce, 401/refresh, link/merge, logout, подтверждённое удаление и revoke. Приёмка: реальные Google/Apple аккаунты, неверные audience, истёкшая сессия, два устройства, повторная привязка, ошибки сети.
4. **Исправить синхронизацию:** pending uploads, launch/foreground retry, server-wins после merge, сериализация операций. Приёмка: офлайн-раунд появляется на втором устройстве после возврата сети без необходимости ещё раз играть.
5. **Довести игру:** завершённый раунд, наборы слов, локализация, небольшие экраны/увеличенный шрифт, доступность и минимальный Android API. Приёмка: ежедневный результат сохраняется при выходе/перезапуске, продуктовые наборы проверены носителями.
6. **Магазинная проверка:** реальные sandbox purchase/restore, продукты/entitlements/цены, privacy policy и декларации, подписанные артефакты и требования конкретных developer accounts. Приёмка: подтверждения из Play Console/App Store Connect и тест на store-installed build.

В этом аудите не проверены: реальные OAuth credentials/consent consoles, production hosting/БД, внешняя инфраструктура безопасности, покупка через Google Play/App Store, физические устройства, весь диапазон Android API и полноценный браузерный runtime. Эти ограничения не меняют вывод о текущих подтверждённых блокерах.

После проверки: тестовый backend и временная PostgreSQL остановлены, adb reverse удалён, сеть включена, debug APK восстановлен. Основной Android-профиль возвращён; временный профиль проверки удалён. Исходный код и настройки OAuth/магазинов не изменялись.
