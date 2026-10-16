package com.wifipulse.analyzer.ui.components

import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

enum class AppLanguage { RU, EN }

val LocalAppLanguage = compositionLocalOf { AppLanguage.RU }

/** Central runtime translation for the app's Compose UI strings. */
fun appUiText(source: String, language: AppLanguage): String {
    if (language == AppLanguage.RU) return source
    translations[source]?.let { return it }

    var result = source
    orderedPhraseTranslations.forEach { (ru, en) ->
        result = result.replace(ru, en, ignoreCase = false)
    }
    return result
}

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = LocalTextStyle.current
) {
    MaterialText(
        text = appUiText(text, LocalAppLanguage.current), modifier = modifier, color = color,
        fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
        fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
        softWrap = softWrap, maxLines = maxLines, minLines = minLines, style = style
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = LocalTextStyle.current
) {
    MaterialText(
        text = text, modifier = modifier, color = color, fontSize = fontSize,
        fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = lineHeight, overflow = overflow, softWrap = softWrap,
        maxLines = maxLines, minLines = minLines, style = style
    )
}

private val translations = mapOf(
    "Сканер (Mesh)" to "Scanner (Mesh)", "Смарт-Аудит" to "Smart Audit",
    "Диагностика" to "Diagnostics", "Спектр/Инфо" to "Spectrum / Info", "Карта" to "Map",
    "О программе" to "About", "Страница проекта ↗" to "Project page ↗", "Понятно" to "Got it",
    "Анализ сигнала и спектра" to "Signal and spectrum analysis", "Диагностика сети" to "Network diagnostics",
    "Карта покрытия" to "Coverage map", "История подключений" to "Connection history",
    "Информация о подключении" to "Connection information", "Обследование квартиры" to "Wi-Fi survey",
    "Сканирование сетей" to "Network scan", "Интеллектуальный аудит" to "Smart audit",
    "Проверить отклик" to "Check response", "Пинг" to "Ping", "Трассировка" to "Trace route",
    "Трассировка с анализом потерь (ТСАП)" to "Trace with packet-loss analysis (TPLA)",
    "ТСАП" to "TPLA", "Цель достигнута" to "Destination reached", "Конечный узел" to "Destination",
    "Сети и каналы" to "Networks and channels", "Сигнал" to "Signal", "Спектр" to "Spectrum",
    "Рейтинг" to "Ranking", "Рейтинг каналов" to "Channel ranking", "График сигнала" to "Signal graph",
    "Рейтинг 2.4 ГГц" to "2.4 GHz ranking", "Рейтинг 5 ГГц" to "5 GHz ranking",
    "Диапазон 2.4 ГГц" to "2.4 GHz band", "Диапазон 5 ГГц" to "5 GHz band",
    "Все диапазоны" to "All bands", "6 ГГц (Wi-Fi 6E)" to "6 GHz (Wi-Fi 6E)",
    "На весь экран" to "Full screen", "На весь экран · альбомно" to "Full screen · landscape",
    "Сбросить масштаб" to "Reset zoom", "Сброс масштаба" to "Reset zoom", "Сбросить" to "Reset",
    "Загрузить план" to "Load floor plan", "Сделать фото" to "Take a photo",
    "Убрать изображение и начать заново" to "Remove image and start over",
    "Удалить выбранное" to "Delete selected", "Удалить последнюю точку" to "Delete last measurement",
    "Отменить последнюю фигуру" to "Undo last shape", "Выбрать объект" to "Select object",
    "Ластик" to "Eraser", "Замер" to "Measure", "Линия" to "Line", "Прямоугольник" to "Rectangle",
    "Круг" to "Circle", "Выбрать сеть Wi-Fi" to "Select Wi-Fi network",
    "Запустить аудит эфира" to "Run spectrum audit", "Запустить в фоне" to "Run in background",
    "Остановить мониторинг" to "Stop monitoring", "Имя сети, RSSI и текущий трафик в системном уведомлении" to "Network name, RSSI and current traffic in a system notification",
    "Подключитесь к сети в настройках Android для отображения сетевых параметров" to "Connect to Wi-Fi in Android settings to view network details",
    "Подключитесь к Wi-Fi для анализа локальной беспроводной сети." to "Connect to Wi-Fi to analyze the local wireless network.",
    "Находит доступные сети и показывает точки доступа, уровень сигнала и каналы." to "Finds nearby networks and shows access points, signal strength and channels.",
    "Запускает проверку связи и трассировку с анализом потерь (ТСАП). Вывод консоли хранится, пока вы его не очистите." to "Runs connectivity checks and trace with packet-loss analysis (TPLA). Console output is kept until you clear it.",
    "Показывает сигнал, спектр каналов, сведения о текущем подключении и историю подключений." to "Shows signal strength, channel spectrum, current connection details and connection history.",
    "Позволяет загрузить план помещения или сделать фото, рисовать объекты и сохранять замеры сигнала на плане." to "Load or photograph a floor plan, draw objects and save signal measurements on the plan.",
    "Пока нет событий. Оставьте приложение работающим при подключении или переходе между точками доступа." to "No events yet. Keep the app running while connecting or roaming between access points.",
    "Закрыть" to "Close", "Очистить всё" to "Clear all", "Очистить" to "Clear",
    "Сохранить" to "Save", "Удалить пароль" to "Delete password", "Задать пароль" to "Set password",
    "Изменить пароль" to "Change password", "Пароль Wi-Fi" to "Wi-Fi password", "Пароль сети" to "Network password",
    "Введите пароль, который известен монтажнику. Приложение не читает сохранённые пароли Android." to "Enter a password known to the installer. The app cannot read passwords saved by Android.",
    "ВРЕМЯ СОБЫТИЯ" to "EVENT TIME", "ХРОНОЛОГИЯ · СТАРЫЕ СОБЫТИЯ СВЕРХУ" to "TIMELINE · OLDEST EVENTS FIRST",
    "Поддерживаются JPG/JPEG, PNG, BMP, GIF и WebP (у анимации берётся первый кадр). HEIC/HEIF — Android 9+, AVIF — Android 12+ при поддержке устройством. PDF и SVG не поддерживаются." to "Supported: JPG/JPEG, PNG, BMP, GIF and WebP (the first frame is used). HEIC/HEIF on Android 9+, AVIF on Android 12+ where supported. PDF and SVG are not supported.",
    "Не удалось открыть изображение. Выберите JPG или PNG и попробуйте ещё раз." to "Could not open image. Choose a JPG or PNG file and try again.",
    "ПУСТАЯ СХЕМА · рисуйте помещение инструментами выше. Точки хранятся локально; цвет между ними — интерполяция измеренного RSSI, не прогноз покрытия." to "EMPTY PLAN · draw the room with the tools above. Points are stored locally; colors between them interpolate measured RSSI and do not predict coverage.",
    "ПЛАН/ФОТО · точки привязаны к изображению и хранятся локально. Интерполяция показывает RSSI между реальными замерами." to "PLAN/PHOTO · points are anchored to the image and stored locally. Interpolation shows RSSI between actual measurements.",
    "Коснитесь плана, чтобы поставить замер; двумя пальцами можно менять масштаб и перемещать план." to "Tap the plan to add a measurement; pinch to zoom and drag to move it.",
    "Проверить" to "Check", "Обновить вручную" to "Refresh", "Обновить список видимых сетей" to "Refresh visible networks",
    "Обновить" to "Refresh", "Подключение" to "Connection", "Настройки сети" to "Network settings",
    "Настройки VPN" to "VPN settings", "Настройки роутера" to "Router settings",
    "Подключено: Подключено к Wi-Fi (SSID скрыт системой)" to "Connected: Connected to Wi-Fi (SSID hidden by the system)",
    "Доступность шлюза" to "Gateway reachability", "Пропускная способность" to "Throughput",
    "Текущая сеть" to "Current network", "Уровень сигнала" to "Signal strength",
    "Сила сигнала" to "Signal strength", "Текущий сигнал" to "Current signal",
    "Проверка стабильности" to "Stability check", "Анализ потерь" to "Packet-loss analysis",
    "Начать диагностику" to "Start diagnostics", "Начать сканирование" to "Start scan",
    "Запуск" to "Start", "Сканировать" to "Scan", "Сканирование сетей" to "Scanning networks",
    "Сети и каналы (" to "Networks and channels (", "Консоль вывода (" to "Console output (",
    "Найденные сети" to "Networks found", "Результаты сканирования" to "Scan results",
    "Параметры подключения" to "Connection details", "Сведения о сети" to "Network details",
    "Имя сети" to "Network name", "Точка доступа" to "Access point", "BSSID" to "BSSID",
    "MAC-адрес" to "MAC address", "Частота" to "Frequency", "Ширина канала" to "Channel width",
    "Скорость соединения" to "Link speed", "Шлюз по умолчанию" to "Default gateway",
    "Первичный DNS" to "Primary DNS", "Вторичный DNS" to "Secondary DNS",
    "IP-адрес" to "IP address", "Маска подсети" to "Subnet mask", "Производитель" to "Vendor",
    "Безопасность" to "Security", "Шифрование" to "Encryption", "Тип защиты" to "Security type",
    "Канал" to "Channel", "Канал Wi-Fi" to "Wi-Fi channel", "Расстояние" to "Distance",
    "Шум" to "Noise", "Уровень шума" to "Noise level", "Качество сигнала" to "Signal quality",
    "Рекомендации" to "Recommendations", "Рекомендация" to "Recommendation",
    "Советы" to "Tips", "Подробности" to "Details", "Показать подробности" to "Show details",
    "Скрыть подробности" to "Hide details", "Обновить данные" to "Refresh data",
    "Автоматически: подключённый сателлит" to "Automatic: connected access point",
    "График переключается вместе с роумингом" to "The chart follows roaming changes",
    "Автоотслеживание включено: график следует за подключённым сателлитом." to "Auto tracking is on: the chart follows the connected access point.",
    "Выбрать сателлит" to "Select access point", "Отмена" to "Cancel", "Готово" to "Done",
    "Новый замер" to "New measurement", "Имя сети / SSID" to "Network name / SSID",
    "Обследование Wi-Fi" to "Wi-Fi survey", "Сохранить замер" to "Save measurement",
    "Карта покрытия по зафиксированным точкам" to "Coverage map from recorded measurements",
    "Цвет показывает уровень RSSI; это схема замеров, а не план помещения." to "Color represents RSSI; this is a measurement chart, not a floor plan.",
    "РИСОВАНИЕ И ЗАМЕРЫ" to "DRAWING AND MEASUREMENTS", "КАРТА ПОКРЫТИЯ" to "COVERAGE MAP",
    "Выберите диапазон" to "Select band", "Выберите сеть" to "Select network",
    "Качество канала в норме." to "Channel quality is good.",
    "Нет обнаруженных сетей. Это не доказывает, что эфир свободен." to "No networks detected. This does not prove that the spectrum is clear.",
    "Достоверность: данных нет" to "Confidence: no data",
    "Достоверность: ограниченная (один снимок сканирования)" to "Confidence: limited (one scan snapshot)",
    "Запустите сканирование радиоэфира для автоматического аудита частот, помех и безопасности." to "Scan the radio spectrum to automatically assess channels, interference and security.",
    "Пока нет событий. Оставьте приложение работающим при подключении или переходе между точками доступа." to "No events yet. Keep the app running while connecting or roaming between access points.",
    "Включить светлую тему" to "Switch to light theme", "Включить тёмную тему" to "Switch to dark theme",
    "Понятно" to "OK"
)

private val phraseTranslations = mapOf(
    "Подключено" to "Connected", "подключён" to "not connected", "Подключитесь" to "Connect",
    "Сканирование" to "Scanning", "Сети не обнаружены" to "No networks found",
    "сети не обнаружены" to "no networks found", "Выберите сеть" to "Select a network",
    "Скрытая сеть" to "Hidden network", "SSID недоступен приложению" to "SSID unavailable to the app",
    "Обнаружено сетей" to "Networks found", "Диапазон" to "Band", "канал" to "channel",
    "МГц" to "MHz", "Мбит/с" to "Mbps", "дБм" to "dBm", "потерь" to "loss",
    "потери" to "loss", "Задержка" to "Latency", "Пакеты" to "Packets",
    "Трассировка с анализом потерь" to "Trace with packet-loss analysis",
    "История подключений" to "Connection history", "Сателлит" to "Access point",
    "точка доступа" to "access point", "Сигнал" to "Signal", "слабый" to "weak",
    "сильный" to "strong", "Критический" to "Critical", "Критическое" to "Critical",
    "Хороший" to "Good", "Отличный" to "Excellent", "Средний" to "Fair",
    "Неизвестно" to "Unknown", "Достоверность" to "Confidence", "Данных нет" to "no data",
    "Для выбранного диапазона" to "For the selected band", "не обнаружены" to "not found",
    "Нажмите" to "Tap", "Сканировать" to "Scan", "сканирование" to "scanning",
    "Запустить" to "Start", "Остановить" to "Stop", "Остановлен" to "Stopped",
    "включено" to "enabled", "выключено" to "disabled", "Автоматически" to "Automatic",
    "Автоотслеживание" to "Auto tracking", "Сброс" to "Reset", "Удалить" to "Delete",
    "Последнюю точку" to "Last point", "Выбранное" to "Selected", "Выбрана" to "Selected",
    "Весь экран" to "Full screen", "Карта покрытия" to "Coverage map", "Замеров" to "Measurements",
    "Измерений" to "Measurements", "Комната / место" to "Room / location", "Например, спальня" to "e.g. bedroom",
    "Зафиксировать точку" to "Save measurement", "Экспорт отчёта PDF" to "Export PDF report",
    "PDF сохранён" to "PDF saved", "Не удалось сохранить PDF" to "Could not save PDF",
    "Проверка шлюза" to "Gateway check", "Проверка DNS" to "DNS check",
    "Проверка внешней доступности" to "Internet reachability check", "Следующие действия" to "Next steps",
    "проверьте" to "check", "Проверьте" to "Check", "Шлюз" to "Gateway",
    "Интернет доступен" to "Internet is available", "Интернет недоступен" to "Internet is unavailable",
    "Нет ответа" to "No response", "Потери" to "Packet loss", "Стабильность" to "Stability",
    "Скорость" to "Speed", "качество" to "quality", "Качество" to "Quality",
    "Доступно" to "Available", "Не удалось" to "Could not", "ошибка" to "error",
    "Подключено к Wi-Fi" to "Connected to Wi-Fi", "SSID скрыт системой" to "SSID hidden by the system",
    "Ручное обновление" to "Manual refresh", "Обновлено" to "Updated", "назад" to "ago",
    "Сканирование завершено" to "Scan complete", "Сканирование сетей" to "Scanning networks",
    "Результаты" to "Results", "Список сетей" to "Network list", "Количество сетей" to "Network count",
    "сети" to "networks", "Сети" to "Networks", "Сеть" to "Network", "сети Wi-Fi" to "Wi-Fi networks",
    "Подключение к" to "Connected to", "Подключённая сеть" to "Connected network",
    "Текущая сеть" to "Current network", "Имя сети" to "Network name", "Сила сигнала" to "Signal strength",
    "Уровень сигнала" to "Signal strength", "Скорость соединения" to "Link speed",
    "Значение" to "Value", "Значения" to "Values", "Название" to "Name", "Имя" to "Name",
    "Включить" to "Enable", "Выключить" to "Disable", "светлую тему" to "light theme",
    "тёмную тему" to "dark theme", "История" to "History", "Закрыть" to "Close",
    "Сохранить" to "Save", "Отмена" to "Cancel", "Готово" to "Done", "Понятно" to "OK",
    "Настройка" to "Settings", "помех" to "interference", "Помех" to "Interference",
    "Радиоэфир" to "Radio spectrum", "каналы" to "channels", "Каналы" to "Channels",
    "Текущий" to "Current", "Рекомендуемый" to "Recommended", "рекомендуемый" to "recommended",
    "Слишком" to "Too", "высокий" to "high", "низкий" to "low", "повышен" to "elevated",
    "минимален" to "minimal", "отсутствуют" to "none", "доступен" to "available",
    "недоступен" to "unavailable", "стабильность" to "stability", "Стабильность" to "Stability",
    "Настройте" to "Configure", "настройте" to "configure", "Установите" to "Set",
    "Пожалуйста" to "Please", "Для" to "For", "данных" to "data", "пакетов" to "packets",
    "соединения" to "connection", "подключения" to "connection", "сети" to "network",
    "Сохранить пароль" to "Save password", "Сохранить замер" to "Save measurement",
    "Проверьте" to "Check", "проверьте" to "check", "включите" to "enable",
    "Интернет" to "Internet", "Время" to "Time", "Дата" to "Date", "Трассировка" to "Trace",
    "Пинг" to "Ping", "Диагностика" to "Diagnostics", "сигнала" to "signal", "Сигнала" to "Signal"
)

private val orderedPhraseTranslations = phraseTranslations.entries.sortedByDescending { it.key.length }
