# Релизная подпись WiFi Sonar для RuStore

## Что настроено в проекте

- Release APK/AAB больше не подписывается debug-ключом.
- Gradle использует отдельный keystore из переменных окружения.
- Сборка assembleRelease/bundleRelease без полного набора параметров подписи завершается ошибкой.
- Обычный Android CI компилирует Release Kotlin и собирает только debug APK для внутренней проверки. Он не публикует APK в GitHub Releases.
- Отдельный ручной workflow RuStore Signed Release собирает подписанный APK, проверяет подпись через apksigner, считает SHA-256 и публикует проверенный артефакт.

## Шаг 1. Создать keystore на своём компьютере

Установи JDK 17 или новее и выполни в PowerShell:

```powershell
keytool -genkeypair -v -keystore "$HOME\wifi-sonar-release.jks" -alias wifi-sonar -keyalg RSA -keysize 4096 -validity 10000
```

Команда запросит пароли интерактивно. Не добавляй пароли в командную строку и не присылай их в чат. Сохрани keystore в двух защищённых местах. Потеря ключа может сделать невозможной публикацию обновлений поверх уже установленного приложения.

## Шаг 2. Подготовить Base64

В PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\wifi-sonar-release.jks")) | Set-Clipboard
```

Base64 — не шифрование. Содержимое буфера обмена является секретом так же, как сам keystore.

## Шаг 3. Добавить GitHub Actions Secrets

Открой репозиторий: Settings → Secrets and variables → Actions → New repository secret.

Создай четыре секрета:

| Secret | Значение |
|---|---|
| ANDROID_KEYSTORE_BASE64 | Base64-содержимое wifi-sonar-release.jks |
| ANDROID_KEYSTORE_PASSWORD | пароль keystore |
| ANDROID_KEY_ALIAS | wifi-sonar |
| ANDROID_KEY_PASSWORD | пароль ключа |

Не создавай их в GitHub Variables. Не публикуй секреты в Issues, Pull Requests, логах или сообщениях.

## Шаг 4. Запустить подписанную сборку

1. Убедись, что все четыре секрета сохранены.
2. Открой Actions.
3. Выбери RuStore Signed Release.
4. Нажми Run workflow и выбери ветку main.
5. Дождись успешного завершения.

Workflow откажется работать не из main, проверит секреты, соберёт APK, проверит цифровую подпись и опубликует APK, version.json и SHA-256 в GitHub Releases. Если секреты не заданы, сборка остановится.

## Шаг 5. Проверить сертификат

В логе шага проверки подписи найди SHA-256 сертификата и сохрани его отдельно. Сравнивай его с сертификатом каждого следующего релиза.

Локальная проверка:

```powershell
apksigner verify --verbose --print-certs .\WiFi-Sonar-release.apk
```

## Перед загрузкой в RuStore

- Установи APK на тестовое устройство.
- Проверь обновление поверх предыдущей версии, если она уже установлена с тем же сертификатом.
- Проверь ключевые функции, разрешения, экспорт и поведение после перезапуска.
- Убедись, что applicationId остался com.mgnkick.wifisonar.
- Увеличь versionCode перед каждой следующей публикацией.
- Не меняй release keystore после публикации.
- Храни APK, SHA-256 и резервную копию ключа в защищённом месте.

Текущая версия исходников — 1.0.2, versionCode 126. Перед публикацией проверь тесты на реальном устройстве и совместимость сертификата с предыдущей публикацией (если такая уже была). Не загружай её в магазин, пока не завершены тесты на реальном устройстве и не подтверждена совместимость подписи с предыдущей публикацией (если такая уже была).

Официальная инструкция RuStore:
https://www.rustore.ru/help/developers/publishing-and-verifying-apps/app-publication
