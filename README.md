# WiFi Pulse — Сетевой комплекс: Python Network Suite & Android Wi-Fi Analyzer

Полнофункциональный набор сетевых инструментов для сканирования радиоэфира, выявления **Mesh-систем** и **скрытых репитеров (усилителей)**, а также проведения реальной сетевой диагностики (**Ping**, **Traceroute**, **Connection Info**).

Проект состоит из двух полноценных реализаций:
1. **Python Network Suite (Python 3.10+)** — десктопный и серверный кроссплатформенный инструмент (CLI + интерактивный терминальный интерфейс Rich + опциональный веб-дашборд на FastAPI + WebSockets).
2. **Android Application (Kotlin + Jetpack Compose)** — мобильное приложение под Android 8.0+ (API 26–34) с архитектурой MVVM и настроенным CI/CD на GitHub Actions.

---

## 🐍 1. Python Network Suite (Python 3.10+)

Инструмент реализован **без моков, симуляций или заглушек**. Все сетевые операции (сканирование эфира, пинг, трассировка, сетевые интерфейсы) выполняются реально через системные утилиты и сокеты операционной системы (Linux, Windows, macOS).

### 🚀 Быстрый запуск

Базовый функционал работает на **стандартной библиотеке Python** (нулевые обязательные зависимости):

```bash
# Интерактивное консольное меню:
python3 run_network_suite.py
# или:
python3 -m python_network_suite
```

#### Флаги командной строки:
```bash
# 1. Сканирование радиоэфира и анализ топологии Mesh/Репитеров:
python3 run_network_suite.py --scan

# 2. Анализ эталонного multi-BSSID радиоэфира (Keenetic Mesh, TP-Link Deco, ASUS + Tenda Repeater):
python3 run_network_suite.py --sample

# 3. Реальный Ping (потоковый вывод, RTT, расчет min/avg/max и потерь):
python3 run_network_suite.py --ping 8.8.8.8 -c 4

# 4. Реальный Traceroute (пошаговое определение маршрута, reverse DNS, таймауты):
python3 run_network_suite.py --trace google.com

# 5. Информация о текущем сетевом подключении (IP, Gateway, Interface, DNS, WAN IP):
python3 run_network_suite.py --info

# 6. Запуск веб-дашборда с Tailwind CSS и WebSockets:
python3 run_network_suite.py --web --port 8000
```

### 📦 Установка дополнительных возможностей (опционально)

```bash
pip install -r requirements.txt
```
- `rich` — добавляет форматированные цветные таблицы и панели в терминале.
- `fastapi` + `uvicorn` — для запуска веб-интерфейса в браузере.

---

### 🧠 Логика и эвристики анализатора Wi-Fi (Mesh vs Репитеры)

1. **Группировка по SSID:**
   В отличие от стандартных утилит, возвращающих плоский список BSSID (физических MAC-адресов радиомодулей), анализатор агрегирует все обнаруженные узлы с одинаковым SSID в единую группу.

2. **Детектирование вендоров по OUI (MAC Prefix):**
   Встроенная база Organizationally Unique Identifier сопоставляет первые 24 бита MAC-адреса с производителями: **Keenetic, TP-Link (Deco), Xiaomi, ASUS, Tenda, MikroTik, Ubiquiti UniFi, Huawei, Netgear, Apple, Google Nest** и др.

3. **Классификация топологии:**
   - **`MESH_NETWORK` (Mesh-система / Бесшовный роуминг):**
     Обнаружено несколько BSSID на одном SSID с одинаковым вендором, на разных каналах/диапазонах (2.4 ГГц и 5 ГГц), либо с обнаруженной поддержкой стандартов быстрого роуминга:
     - `802.11k` (Radio Resource Management)
     - `802.11r` (Fast BSS Transition)
     - `802.11v` (Wireless Network Management)
   - **`REPEATER_DETECTED` (Обнаружен репитер / усилитель):**
     На одном и том же SSID зафиксированы разные производители оборудования (например, основной роутер `ASUS`, а соседний узел с тем же именем сети — `Tenda Extender` с худшим уровнем сигнала).
   - **`ENTERPRISE_ROAMING`:** Корпоративные сети с аутентификацией 802.1X / WPA-Enterprise.
   - **`STANDALONE`:** Одиночная точка доступа.

4. **Определение ролей узлов:**
   - `MASTER_ROUTER`: Основной шлюз / точка с наилучшим уровнем сигнала.
   - `MESH_NODE`: Дочерний узел Mesh-системы.
   - `REPEATER_SUSPECT`: Подозрение на сторонний репитер/экстендер.

---

### 📡 Кроссплатформенное сканирование эфира

- **Linux:** `nmcli dev wifi list` (с автоматическим парсингом SSID, BSSID, Signal, Channel, Freq, Security) + fallback на `iwlist scan`.
- **Windows:** `netsh wlan show networks mode=bssid` (с поддержкой как русской, так и английской локализации Windows).
- **macOS:** Apple Airport utility (`/System/Library/PrivateFrameworks/Apple80211.framework/.../airport -s`).

---

### 🏓 Реальный Ping и Traceroute

- **Асинхронный Ping (`AsyncPingService`):**
  - Запускает системный процесс `ping` через `asyncio.create_subprocess_exec`.
  - Потоково считывает каждый пакет в реальном времени, парсит `bytes`, `ip`, `seq`, `ttl`, `time` (RTT в мс).
  - Считает итоговую статистику: переданные/полученные пакеты, процент потерь (packet loss), min/avg/max RTT.
  - Поддерживает безопасную отмену задачи (`CancelledError`) без утечек зомби-процессов.
- **Асинхронная трассировка (`AsyncTracerouteService`):**
  - Выполняет пошаговое зондирование маршрута с инкрементальным TTL (Time-To-Live).
  - Парсит ответы промежуточных маршрутизаторов (TTL Exceeded) и целевого хоста.
  - Производит обратное асинхронное DNS-резолвирование IP-адресов хопов в доменные имена (`socket.getnameinfo`).
  - Корректно фиксирует таймауты (`* * *`).

---

### 🌐 Определение параметров подключения (`NetworkInfoService`)

Определяет актуальные параметры хоста:
- **Локальный IP-адрес** (через сокет-проб без отправки реального трафика).
- **Основной шлюз (Default Gateway)** (парсинг `/proc/net/route` и `ip route` на Linux, `route print` на Windows, `route -n get default` на macOS).
- **Активный сетевой интерфейс** (`eth0`, `wlan0`, `en0`, сетевой адаптер).
- **MAC-адрес физического адаптера**.
- **DNS-серверы** (парсинг `/etc/resolv.conf` или системных DNS).
- **Публичный WAN IP** (асинхронный запрос к внешнему резолверу).

---

## 📱 2. Android-приложение (Kotlin, Jetpack Compose, MVVM)

Полноценное нативное мобильное приложение для Android 8.0+ (API 26+). APK собирается из Kotlin/Jetpack Compose и использует реальные Android API. Python-компонент не подменяет мобильное приложение: он предназначен для настольного CLI и опционального веб-дашборда.

### Стек технологий:
- **Язык:** Kotlin 2.0.0
- **UI:** Jetpack Compose (BOM 2024.06.00) + Material 3
- **Архитектура:** Clean Architecture + MVVM (`WifiViewModel`, `WifiRepository`, `StateFlow`)
- **Системные сервисы:** Android `WifiManager`, `ConnectivityManager`, `DhcpInfo`
- **Сборка:** Gradle 8.7 (Kotlin DSL `build.gradle.kts`, Version Catalog `libs.versions.toml`)
- **CI/CD:** GitHub Actions (`.github/workflows/build.yml`)

### Экраны мобильного приложения:
1. **Дашборд сканирования сетей:** Поиск сетей, фильтры по 2.4/5/6 ГГц, RSSI в дБм, защита от троттлинга (Scan Throttling).
2. **Анализ радиоэфира:** Живой график RSSI во времени, спектральный график интерференции каналов, автоматический рейтинг каналов (1–10).
3. **Информация о подключении:** IP-адрес, шлюз, скорость линка (Rx/Tx Link Speed), Wi-Fi standard (Wi-Fi 6/5/4), тест задержки.
4. **Сетевая диагностика:** Реальный Ping и Traceroute с терминальным логом и графиком задержки.

---

### 🚀 Публикация на GitHub и получение готового APK

1. **Инициализация Git в корне проекта:**
   ```bash
   git init
   git add .
   git commit -m "feat: complete WiFi Pulse suite with Python tools and Android app"
   ```
2. **Создание репозитория на GitHub:**
   - Перейдите на [github.com/new](https://github.com/new).
   - Создайте публичный или приватный репозиторий.
3. **Отправка кода:**
   ```bash
   git remote add origin https://github.com/<ваш-логин>/<имя-репозитория>.git
   git branch -M main
   git push -u origin main
   ```
4. **Скачивание готового APK:**
   - Откройте вкладку **Actions** в репозитории GitHub.
   - Дождитесь зелёного статуса workflow `Android Build & APK Generation`.
   - Откройте успешный запуск и скачайте артефакт **`WiFiPulse-Android-APK`**.
   - Распакуйте архив. `WiFiPulse.apk` — сборка release, `WiFiPulse-debug.apk` — отладочная сборка. Для установки на телефон достаточно одного файла `WiFiPulse.apk`.
   - В Android разрешите установку приложений из используемого файлового менеджера и откройте APK. Release в текущей конфигурации подписывается debug-ключом для тестовой установки; для публикации в RuStore/Google Play нужен собственный ключ подписи.

## Проверка сборки на GitHub Actions

В репозитории настроены два независимых workflow:

- `Python CI` — проверяет синтаксис Python на версиях 3.10–3.13, запускает CLI smoke-тесты и проверяет импорт веб-дашборда.
- `Android Build & APK Generation` — устанавливает Android SDK, собирает Debug и Release APK через Gradle, рассчитывает SHA-256 и публикует оба файла в артефакте `WiFiPulse-Android-APK`.

Для Android APK откройте вкладку **Actions**, выберите успешный запуск `Android Build & APK Generation` и скачайте артефакт `WiFiPulse-Android-APK`.

