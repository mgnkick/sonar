# Чек-лист релизной подписи WiFi Sonar для RuStore

Текущая сборка проекта использует debug-подпись для Release. Это решение подходит для тестовой сборки, но не должно оставаться схемой официальной публикации.

## Один раз перед первой публикацией

1. Сгенерировать отдельный upload/release keystore с надёжным паролем.
2. Сохранить keystore вне Git-репозитория и сделать резервную копию в защищённом хранилище.
3. Не передавать keystore, пароли и alias в публичные Issues, Pull Requests или логи CI.
4. Добавить значения в GitHub Actions Secrets:
   - `ANDROID_KEYSTORE_BASE64` — keystore в Base64;
   - `ANDROID_KEYSTORE_PASSWORD`;
   - `ANDROID_KEY_ALIAS`;
   - `ANDROID_KEY_PASSWORD`.
5. Настроить release signingConfig так, чтобы release использовал только секреты CI. Если секреты отсутствуют — сборка для публикации должна завершаться ошибкой, а не подменять релиз debug-ключом.
6. Зафиксировать fingerprint сертификата и проверить его перед отправкой в RuStore.

## Каждая новая версия

- Не менять `applicationId = "com.mgnkick.wifisonar"`.
- Не менять release-сертификат после первой публикации.
- Увеличивать `versionCode` строго выше ранее загруженного значения.
- Собирать release APK/AAB, а не debug.
- Проверять подпись средствами Android SDK (`apksigner verify --verbose --print-certs`).
- Установить артефакт на чистое устройство и обновить поверх предыдущей версии.
- Хранить опубликованный артефакт и контрольную сумму.

## Примечание

RuStore поддерживает APK и AAB. Для APK требуется цифровая подпись; для новых версий подпись и package name должны совпадать с предыдущей опубликованной версией. Подробности — в официальной инструкции RuStore:
https://www.rustore.ru/help/developers/publishing-and-verifying-apps/app-publication
