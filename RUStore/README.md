# Release Notes — готовность WiFi Sonar к публикации в RuStore

## Текущая проверка репозитория

- Package: `com.mgnkick.wifisonar`
- Целевая версия релизной сборки: `1.0.1` / `versionCode 127`
- minSdk: 26 (Android 8.0)
- Release signing is configured through a dedicated keystore stored in GitHub Actions Secrets.
- VPN: detected through Android network capabilities; warning + system VPN settings intent; no tunnel/client implementation found in reviewed Android source.
- DNS: current Wi-Fi DNS values are read and checked; no DNS setter/configuration path found in reviewed Android source.
- Help mode: global `?` toggle is present; contextual `i` hints are shown for described actions.

## RuStore documentation

- [Moderation rules](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps)
- [App publication](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/app-publication)

Important moderation rule: RuStore prohibits applications that actually provide tools to bypass access restrictions, including VPN/proxy/anonymizer/tunneling and DNS/IP masking functions. The app should be presented transparently as a network diagnostics utility; the reviewer statement must be consistent with source code and behavior. Do not claim approval is guaranteed.

## Deliverables in this folder

- `Пакет публикации RuStore.md` — store description, moderation note, permission table, screenshot plan, preflight checklist.
- `RELEASE-SIGNING-CHECKLIST.md` — secure release signing procedure.

## Not done / still required

- Publish a real privacy policy after reviewing all data storage/export/network behavior.
- Capture genuine screenshots on a device and verify screen orientation/files.
- Validate app on physical Android devices.
- Check target SDK and RuStore compatibility at submission time.
