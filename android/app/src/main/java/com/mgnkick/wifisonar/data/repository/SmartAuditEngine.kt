package com.mgnkick.wifisonar.data.repository

import com.mgnkick.wifisonar.data.model.*

class SmartAuditEngine {

    fun generateReport(
        networkGroups: List<WifiNetworkGroup>,
        connectionDetails: WifiConnectionDetails,
        lastPingSummary: PingSummary? = null
    ): AuditReport {
        val recommendations = mutableListOf<AuditRecommendation>()

        // Only audit the network that is actually identified as connected.
        // Never substitute the strongest nearby AP: it may be a different Mesh satellite/network.
        val activeGroup = if (connectionDetails.isConnected && connectionDetails.ssid.isNotBlank()) {
            networkGroups.find { it.ssid.equals(connectionDetails.ssid, ignoreCase = true) }
        } else {
            null
        }

        // 1. Channel Congestion Recommendation
        recommendations.add(evaluateChannelCongestion(networkGroups, activeGroup, connectionDetails))

        // 2. Signal Quality & Coverage Recommendation
        recommendations.add(evaluateSignalQuality(activeGroup, connectionDetails))

        // 3. Security Protocol Recommendation
        recommendations.add(evaluateSecurity(activeGroup))

        // 4. Mesh & Topology Recommendation (or Latency if Mesh is standard)
        if (activeGroup != null && activeGroup.totalNodesCount > 1) {
            recommendations.add(evaluateTopology(activeGroup))
        } else if (lastPingSummary != null) {
            recommendations.add(evaluateLatency(lastPingSummary))
        } else {
            recommendations.add(evaluateBandUtilization(activeGroup))
        }
        recommendations.addAll(evaluateAdditionalGuidance(networkGroups, activeGroup, connectionDetails))
        val orderedRecommendations = recommendations.sortedBy {
            when (it.severity) {
                AuditSeverity.CRITICAL -> 0
                AuditSeverity.WARNING -> 1
                AuditSeverity.OPTIMAL -> 2
            }
        }

        // Several useful tips may share one category. Score each category once so a richer
        // report does not make a healthy network look worse merely because it has more advice.
        var totalScore = 100
        var criticals = 0
        var warnings = 0
        var optimals = 0

        for (rec in orderedRecommendations) {
            when (rec.severity) {
                AuditSeverity.CRITICAL -> {
                    criticals++
                }
                AuditSeverity.WARNING -> {
                    warnings++
                }
                AuditSeverity.OPTIMAL -> {
                    optimals++
                }
            }
        }
        orderedRecommendations.groupBy { it.category }.values.forEach { categoryRecommendations ->
            when (categoryRecommendations.maxByOrNull { severityRank(it.severity) }?.severity) {
                AuditSeverity.CRITICAL -> totalScore -= 25
                AuditSeverity.WARNING -> totalScore -= 12
                else -> Unit
            }
        }
        totalScore = totalScore.coerceIn(10, 100)

        val (statusTitle, statusDesc) = when {
            totalScore >= 85 -> "Мало замечаний по доступным данным" to "Индекс высокий. Проверьте отдельные рекомендации: сканирование не измеряет скорость, занятость эфира и безопасность пароля напрямую."
            totalScore >= 65 -> "Есть пункты для проверки" to "Индекс учитывает обнаруженные признаки и их условную важность. Приоритет и подтверждающие измерения указаны в карточках ниже."
            else -> "Найдены важные пункты для проверки" to "Низкий индекс не является диагнозом причины. Начните с критичных карточек, подтвердите проблему повторными измерениями и проверьте настройки владельца сети."
        }

        return AuditReport(
            overallScore = totalScore,
            statusTitle = statusTitle,
            statusDescription = statusDesc,
            recommendations = orderedRecommendations.take(10),
            scannedNetworksCount = networkGroups.size,
            optimalCount = optimals,
            warningCount = warnings,
            criticalCount = criticals
        )
    }

    private fun severityRank(severity: AuditSeverity): Int = when (severity) {
        AuditSeverity.OPTIMAL -> 0
        AuditSeverity.WARNING -> 1
        AuditSeverity.CRITICAL -> 2
    }

    private fun evaluateAdditionalGuidance(
        allGroups: List<WifiNetworkGroup>,
        targetGroup: WifiNetworkGroup?,
        details: WifiConnectionDetails
    ): List<AuditRecommendation> = buildList {
        val allNodes = allGroups.flatMap { it.nodes }
        val openCount = allNodes.count { it.securityType == SecurityType.OPEN }
        if (openCount > 0 && targetGroup?.bestSecurity != SecurityType.OPEN) {
            add(AuditRecommendation(
                id = "rec_open_nearby",
                category = AuditCategory.SECURITY_PROTOCOL,
                severity = AuditSeverity.WARNING,
                title = "Рядом обнаружено открытых точек доступа: $openCount",
                summary = "Открытая сеть не требует пароля и обычно не шифрует радиоканал между устройством и точкой доступа. Наличие такой сети рядом само по себе не означает, что телефон подключён к ней.",
                actionGuide = "Не подключайтесь к неизвестным открытым точкам автоматически. Для своей сети включите WPA2-Personal (AES) или WPA3-Personal и отключите WPS, если он не нужен.",
                metricBadge = "$openCount без защиты"
            ))
        }

        val targetBand = when (details.frequency) {
            in 2400..2500 -> WifiBand.BAND_2_4_GHZ
            in 4900..5924 -> WifiBand.BAND_5_GHZ
            in 5925..7125 -> WifiBand.BAND_6_GHZ
            else -> targetGroup?.bestBand ?: WifiBand.UNKNOWN
        }
        val targetChannel = if (details.isConnected && details.frequency > 0)
            WifiRepository.frequencyToChannel(details.frequency) else targetGroup?.bestNode?.channel ?: 0
        val targetNode = targetGroup?.bestNode

        if (targetBand == WifiBand.BAND_2_4_GHZ && targetChannel > 0) {
            val overlap = allNodes.count {
                it.band == WifiBand.BAND_2_4_GHZ && it.channel != targetChannel && kotlin.math.abs(it.channel - targetChannel) <= 4
            }
            if (overlap >= 4) add(AuditRecommendation(
                id = "rec_channel_adjacent",
                category = AuditCategory.CHANNEL_CONGESTION,
                severity = AuditSeverity.WARNING,
                title = "Соседние каналы 2,4 ГГц перекрываются с CH $targetChannel",
                summary = "Обнаружено $overlap точек на близких каналах. В диапазоне 2,4 ГГц соседние частоты могут занимать одну и ту же полосу и ухудшать эфир даже при разном номере канала.",
                actionGuide = "В панели роутера проверьте канал 1, 6 или 11 и ширину 20 МГц. После изменения повторите сканирование в том же месте.",
                metricBadge = "$overlap соседних каналов"
            ))
        }

        if (targetBand == WifiBand.BAND_2_4_GHZ && (targetNode?.channelWidth ?: 0) >= 40) {
            add(AuditRecommendation(
                id = "rec_width_24",
                category = AuditCategory.CHANNEL_CONGESTION,
                severity = AuditSeverity.WARNING,
                title = "Ширина 40 МГц в перегруженном диапазоне 2,4 ГГц",
                summary = "Широкий канал захватывает больше соседних частот. Это может уменьшить доступное эфирное время для собственной и соседних сетей.",
                actionGuide = "Если рядом много точек доступа, установите ширину 20 МГц. Оставляйте 40 МГц только после замеров, если эфир действительно свободен и клиентам нужна дополнительная скорость.",
                metricBadge = "${targetNode?.channelWidth ?: 40} МГц"
            ))
        }

        if (targetBand == WifiBand.BAND_5_GHZ && targetChannel in 52..144) {
            add(AuditRecommendation(
                id = "rec_dfs_channel",
                category = AuditCategory.CHANNEL_CONGESTION,
                severity = AuditSeverity.WARNING,
                title = "Подключение использует DFS-канал CH $targetChannel",
                summary = "На DFS-частотах роутер обязан проверять наличие радаров и при их обнаружении менять канал. Это может вызвать краткую паузу или неожиданную смену канала.",
                actionGuide = "Если замечаете периодические паузы, сравните работу с разрешёнными для вашей страны не-DFS каналами роутера. Не выбирайте частоты, запрещённые местными правилами.",
                metricBadge = "DFS · CH $targetChannel"
            ))
        }

        if (targetBand == WifiBand.BAND_2_4_GHZ && targetChannel in 12..13) {
            add(AuditRecommendation(
                id = "rec_channel_compat",
                category = AuditCategory.TOPOLOGY_EFFICIENCY,
                severity = AuditSeverity.WARNING,
                title = "Канал CH $targetChannel может быть недоступен части клиентов",
                summary = "Поддержка каналов 12 и 13 зависит от региона и настроек страны на клиентских устройствах.",
                actionGuide = "Если отдельные устройства не видят сеть, проверьте регион роутера и попробуйте один из каналов 1, 6 или 11.",
                metricBadge = "Совместимость клиентов"
            ))
        }

        if (targetGroup != null && targetGroup.nodes.size > 1) {
            val levels = targetGroup.nodes.map { it.rssi }
            val spread = (levels.maxOrNull() ?: 0) - (levels.minOrNull() ?: 0)
            if (spread >= 20) add(AuditRecommendation(
                id = "rec_roaming_imbalance",
                category = AuditCategory.TOPOLOGY_EFFICIENCY,
                severity = AuditSeverity.WARNING,
                title = "Большая разница сигнала между узлами одной сети",
                summary = "Уровни сигнала BSSID одной сети отличаются на $spread дБ. Это может затруднять выбор точки роуминга на телефоне или ноутбуке.",
                actionGuide = "Сравните уровни в нескольких точках помещения. Проверьте размещение узлов, мощность передачи и поддержку 802.11k/v; решение о переходе всё равно принимает клиентское устройство.",
                metricBadge = "Разброс $spread дБ"
            ))
        }
    }

    private fun evaluateChannelCongestion(
        allGroups: List<WifiNetworkGroup>,
        targetGroup: WifiNetworkGroup?,
        connectionDetails: WifiConnectionDetails
    ): AuditRecommendation {
        val allNodes = allGroups.flatMap { it.nodes }
        // The connected radio's frequency is authoritative. A hidden SSID or unavailable
        // BSSID can prevent finding its Mesh group; never default that connection to 2.4 GHz.
        val connectedBand = when (connectionDetails.frequency) {
            in 2400..2500 -> WifiBand.BAND_2_4_GHZ
            in 4900..5924 -> WifiBand.BAND_5_GHZ
            in 5925..7125 -> WifiBand.BAND_6_GHZ
            else -> WifiBand.UNKNOWN
        }
        val targetBand = if (connectionDetails.isConnected) {
            connectedBand
        } else {
            targetGroup?.bestBand ?: WifiBand.UNKNOWN
        }
        // Read the computed/custom-getter property once; Kotlin cannot smart-cast
        // targetGroup.bestNode across repeated getter calls.
        val targetNode = targetGroup?.bestNode
        val targetChannel = when {
            connectionDetails.isConnected && connectionDetails.frequency > 0 ->
                WifiRepository.frequencyToChannel(connectionDetails.frequency)
            connectionDetails.isConnected -> 0
            else -> targetNode?.channel ?: 0
        }

        // Compare only APs in the connected radio band; channel numbers can overlap across bands.
        val sameBandNodes = allNodes.filter { it.band == targetBand }
        val sameChannelCount = if (targetChannel > 0) {
            sameBandNodes.count { it.channel == targetChannel }
        } else 0

        val cleanCandidate = when (targetBand) {
            WifiBand.BAND_2_4_GHZ ->
                listOf(1, 6, 11).minByOrNull { ch -> sameBandNodes.count { it.channel == ch } } ?: 1
            WifiBand.BAND_5_GHZ, WifiBand.BAND_6_GHZ ->
                listOf(36, 44, 48, 149).minByOrNull { ch -> sameBandNodes.count { it.channel == ch } } ?: 36
            else -> 0
        }

        return when {
            targetBand == WifiBand.UNKNOWN || targetChannel <= 0 -> {
                AuditRecommendation(
                    id = "rec_channel_unknown",
                    category = AuditCategory.CHANNEL_CONGESTION,
                    severity = AuditSeverity.WARNING,
                    title = "Не удалось определить диапазон или канал подключения",
                    summary = "Android не предоставил актуальную частоту подключения. Данные соседних точек доступа не используются для подстановки диапазона или канала.",
                    actionGuide = "Обновите данные подключения и повторите анализ, когда Android предоставит частоту Wi-Fi.",
                    metricBadge = "Недостаточно данных"
                )
            }
            sameChannelCount >= 6 -> {
                AuditRecommendation(
                    id = "rec_channel_crowded",
                    category = AuditCategory.CHANNEL_CONGESTION,
                    severity = AuditSeverity.CRITICAL,
                    title = "Много видимых точек на канале CH $targetChannel",
                    summary = "Обнаружено $sameChannelCount точек доступа на этом канале. Это показатель конкуренции, а не прямое измерение занятости эфира: сканер не видит скрытые сети и не измеряет эфирное время или помехи от не-Wi-Fi устройств.",
                    actionGuide = "Не меняйте настройки по одному снимку. Повторите сканирование в часы нагрузки и сравните в проблемной зоне RSSI, потери и задержку до шлюза. Если проблема подтверждается, проверьте разрешённые каналы и ширину в панели роутера; предложенный CH $cleanCandidate — только кандидат по числу видимых точек, не гарантия лучшего канала.",
                    metricBadge = "$sameChannelCount сетей на канале"
                )
            }
            sameChannelCount >= 3 -> {
                AuditRecommendation(
                    id = "rec_channel_moderate",
                    category = AuditCategory.CHANNEL_CONGESTION,
                    severity = AuditSeverity.WARNING,
                    title = "Есть соседние точки на канале CH $targetChannel",
                    summary = "На текущем канале видно $sameChannelCount точек. Их наличие само по себе не подтверждает помехи или просадку скорости: важны мощность сигнала, ширина каналов, эфирное время и нагрузка.",
                    actionGuide = "Сначала измерьте проблему в одном месте и в одинаковых условиях. В 2,4 ГГц обычно сравнивают 20 МГц и каналы 1/6/11; в 5/6 ГГц учитывайте региональные ограничения и совместимость клиентов. После изменения повторите замеры.",
                    metricBadge = "Конкуренция: $sameChannelCount AP"
                )
            }
            else -> {
                AuditRecommendation(
                    id = "rec_channel_optimal",
                    category = AuditCategory.CHANNEL_CONGESTION,
                    severity = AuditSeverity.OPTIMAL,
                    title = "Мало видимых точек на канале CH $targetChannel",
                    summary = "Сканирование обнаружило мало соседних точек на этом канале. Это не измеряет радиоэфир полностью и не гарантирует максимальную скорость: невидимые сети, эфирное время и помехи могут не попасть в результат.",
                    actionGuide = "Если пользователи не сообщают о проблеме, менять канал не нужно. Для проверки качества сравните скорость, потери и задержку в нужных местах и при типичной нагрузке.",
                    metricBadge = "Мало видимых сетей"
                )
            }
        }
    }

    private fun evaluateSignalQuality(
        targetGroup: WifiNetworkGroup?,
        connectionDetails: WifiConnectionDetails
    ): AuditRecommendation {
        val rssi = if (connectionDetails.isConnected && connectionDetails.rssi != 0) {
            connectionDetails.rssi
        } else {
            targetGroup?.bestRssi ?: -70
        }

        return when {
            rssi < -78 -> {
                AuditRecommendation(
                    id = "rec_signal_critical",
                    category = AuditCategory.SIGNAL_COVERAGE,
                    severity = AuditSeverity.CRITICAL,
                    title = "Критически слабый уровень сигнала ($rssi дБм)",
                    summary = "RSSI $rssi dBm — слабый уровень для многих сценариев и возможный фактор низкой скорости/нестабильности. Один RSSI не показывает шум, качество канала и фактическую скорость; обрывы этим значением не доказываются.",
                    actionGuide = "Измерьте RSSI и потери у точки и в проблемной комнате одним устройством. Переставьте точку выше и открыто, затем повторите замер. Дополнительную точку доступа ставьте там, где ещё есть хороший сигнал; проводной backhaul предпочтительнее, если доступен.",
                    metricBadge = "$rssi дБм (Критично)"
                )
            }
            rssi < -68 -> {
                AuditRecommendation(
                    id = "rec_signal_warning",
                    category = AuditCategory.SIGNAL_COVERAGE,
                    severity = AuditSeverity.WARNING,
                    title = "Пограничный уровень приема ($rssi дБм)",
                    summary = "RSSI $rssi dBm — пограничный для части требовательных сценариев уровень. Стены, ориентация антенн, помехи и сам клиент влияют на результат; по одному уровню нельзя определить скорость.",
                    actionGuide = "Сравните 2,4 и 5 ГГц в одном месте: 2,4 ГГц часто дальше распространяется, а 5 ГГц может дать больше пропускной способности при хорошем сигнале. Выберите по фактическим задержке и скорости, а не только по диапазону.",
                    metricBadge = "$rssi дБм (Средний)"
                )
            }
            else -> {
                AuditRecommendation(
                    id = "rec_signal_optimal",
                    category = AuditCategory.SIGNAL_COVERAGE,
                    severity = AuditSeverity.OPTIMAL,
                    title = "Отличное радиопокрытие ($rssi дБм)",
                    summary = "RSSI $rssi dBm — хороший уровень радиосигнала в точке замера. Он не подтверждает отсутствие потерь и не гарантирует максимальную скорость: влияют стандарт клиента, ширина канала, помехи и загрузка.",
                    actionGuide = "Зафиксируйте это место как контрольную точку. При жалобе пользователя проверьте серию Ping до шлюза и тест пропускной способности отдельно от интернет-канала.",
                    metricBadge = "$rssi дБм (Идеально)"
                )
            }
        }
    }

    private fun evaluateSecurity(targetGroup: WifiNetworkGroup?): AuditRecommendation {
        // Missing connection/group data is not evidence that the network uses WPA2.
        val sec = targetGroup?.bestSecurity ?: SecurityType.UNKNOWN

        return when (sec) {
            SecurityType.OPEN -> {
                AuditRecommendation(
                    id = "rec_sec_open",
                    category = AuditCategory.SECURITY_PROTOCOL,
                    severity = AuditSeverity.CRITICAL,
                    title = "Открытая сеть без шифрования (Open)",
                    summary = "Для точки доступа не обнаружено защиты Wi-Fi на канальном уровне. Это повышает риск локального перехвата трафика, но HTTPS/VPN и защита отдельных приложений могут шифровать свои данные.",
                    actionGuide = "Для собственной сети включите WPA2-Personal (AES) или WPA3-Personal с уникальной длинной парольной фразой; отключите WPS, если он не нужен. Не меняйте чужую сеть и предупредите владельца/администратора.",
                    metricBadge = "Без защиты"
                )
            }
            SecurityType.WEP -> {
                AuditRecommendation(
                    id = "rec_sec_wep",
                    category = AuditCategory.SECURITY_PROTOCOL,
                    severity = AuditSeverity.CRITICAL,
                    title = "Устаревший протокол WEP взломан",
                    summary = "WEP криптографически устарел и не считается безопасным. Время восстановления ключа зависит от трафика и условий, поэтому фиксированная оценка времени некорректна.",
                    actionGuide = "Переключите тип безопасности роутера на WPA2-PSK (AES) с уникальным длинным паролем.",
                    metricBadge = "Устаревший WEP"
                )
            }
            SecurityType.WPA_PSK, SecurityType.WPA_WPA2_MIXED, SecurityType.WPA2_PSK -> {
                AuditRecommendation(
                    id = "rec_sec_wpa2",
                    category = AuditCategory.SECURITY_PROTOCOL,
                    severity = AuditSeverity.WARNING,
                    title = "WPA2-PSK: защита зависит от пароля",
                    summary = "WPA2-Personal с AES остаётся распространённым и совместимым вариантом. Главные факторы защиты — уникальный длинный пароль и отключённый WPS.",
                    actionGuide = "Проверьте в панели роутера, что выбран WPA2-Personal с AES (не TKIP), задайте уникальную длинную парольную фразу и отключите WPS, если он не нужен. Длина 12–16 символов не является гарантией сама по себе; избегайте повторно используемых и словарных паролей.",
                    metricBadge = "WPA2-PSK"
                )
            }
            SecurityType.WPA3_SAE, SecurityType.WPA2_WPA3_MIXED -> {
                AuditRecommendation(
                    id = "rec_sec_wpa3",
                    category = AuditCategory.SECURITY_PROTOCOL,
                    severity = AuditSeverity.OPTIMAL,
                    title = if (sec == SecurityType.WPA3_SAE) "Обнаружен режим WPA3-SAE" else "Обнаружен смешанный режим WPA2/WPA3",
                    summary = "Приложение показывает фактически обнаруженный режим безопасности и не рекомендует переходить на WPA3 автоматически. Совместимость и политика шифрования зависят от роутера и подключаемых устройств.",
                    actionGuide = "Никаких изменений только на основании этой оценки не требуется. Проверяйте защиту по настройкам роутера и совместимости клиентских устройств.",
                    metricBadge = sec.displayName
                )
            }
            SecurityType.UNKNOWN -> {
                AuditRecommendation(
                    id = "rec_sec_unknown",
                    category = AuditCategory.SECURITY_PROTOCOL,
                    severity = AuditSeverity.WARNING,
                    title = "Тип защиты сети не удалось определить",
                    summary = "Android не предоставил достоверные сведения о защите именно подключённой сети. Это не означает, что сеть открытая или не зашифрована.",
                    actionGuide = "Проверьте тип защиты в настройках роутера или выберите соответствующую точку доступа в списке сканирования. Не меняйте настройки безопасности только на основании отсутствующих данных.",
                    metricBadge = "Защита неизвестна"
                )
            }
            SecurityType.ENTERPRISE -> {
                AuditRecommendation(
                    id = "rec_sec_ent",
                    category = AuditCategory.SECURITY_PROTOCOL,
                    severity = AuditSeverity.OPTIMAL,
                    title = "Корпоративная безопасность 802.1X (Enterprise)",
                    summary = "Используется централизованный RADIUS-сервер с индивидуальными сертификатами пользователей.",
                    actionGuide = "Корпоративная аутентификация соответствует высшим стандартам.",
                    metricBadge = "802.1X Enterprise"
                )
            }
        }
    }

    private fun evaluateTopology(targetGroup: WifiNetworkGroup): AuditRecommendation {
        return when (targetGroup.topologyType) {
            NetworkTopologyType.REPEATER_DETECTED -> {
                AuditRecommendation(
                    id = "rec_topo_repeater",
                    category = AuditCategory.TOPOLOGY_EFFICIENCY,
                    severity = AuditSeverity.WARNING,
                    title = "Обнаружен сторонний репитер (усилитель)",
                    summary = "Под именем «${targetGroup.ssid}» обнаружены BSSID, которые эвристика приложения связывает с разными производителями (${targetGroup.primaryVendor} и др.). Это может быть репитер, несколько точек или ошибка классификации; один SSID не подтверждает общую систему.",
                    actionGuide = "Сверьте BSSID и модель оборудования с инвентаризацией сети. Проверьте, подключены ли точки проводом или по радио, и измерьте скорость у каждой. Не заменяйте оборудование только по этому предположению; решения о роуминге принимает клиент с учётом настроек сети.",
                    metricBadge = "Репитер / Потеря скорости"
                )
            }
            NetworkTopologyType.MESH_NETWORK -> {
                val roaming = targetGroup.roamingSummary
                val roamingText = if (roaming.isSupported) roaming.summaryText else "Роуминг на стороне клиента"
                AuditRecommendation(
                    id = "rec_topo_mesh",
                    category = AuditCategory.TOPOLOGY_EFFICIENCY,
                    severity = AuditSeverity.OPTIMAL,
                    title = "Бесшовная Mesh-система (${targetGroup.totalNodesCount} узла)",
                    summary = "По совпадающим признакам BSSID приложение предполагает Mesh-топологию вендора ${targetGroup.primaryVendor}. Обнаружена поддержка: $roamingText. Это классификация радиообъявлений, а не проверка фактического бесшовного перехода.",
                    actionGuide = "Проверьте переход на клиентском устройстве во время непрерывного Ping или звонка и пройдите зону покрытия. Параметры 802.11k/v/r и решение о переключении зависят от инфраструктуры и самого клиента.",
                    metricBadge = "Mesh Активен"
                )
            }
            else -> {
                AuditRecommendation(
                    id = "rec_topo_std",
                    category = AuditCategory.TOPOLOGY_EFFICIENCY,
                    severity = AuditSeverity.OPTIMAL,
                    title = "Стандартная топология точки доступа",
                    summary = "Сеть состоит из единого физического радиомодуля.",
                    actionGuide = "Конфигурация стабильна.",
                    metricBadge = "1 AP"
                )
            }
        }
    }

    private fun evaluateLatency(pingSummary: PingSummary): AuditRecommendation {
        return when {
            pingSummary.packetLossPercent > 5f -> {
                AuditRecommendation(
                    id = "rec_lat_loss",
                    category = AuditCategory.NETWORK_LATENCY,
                    severity = AuditSeverity.CRITICAL,
                    title = "Потеря пакетов в канале (${pingSummary.packetLossPercent}%)",
                    summary = "На цель отправлено ${pingSummary.packetsTransmitted} проб, потери составили ${pingSummary.packetLossPercent}%. Это признак потери ICMP-ответов на данном маршруте, но не указывает автоматически на радиопомехи, роутер или провайдера.",
                    actionGuide = "Повторите серию сначала до шлюза, затем до внешнего IP. Если потери есть уже до шлюза — проверьте Wi-Fi и локальный сегмент; если только дальше — проверьте WAN/маршрут. ICMP может ограничиваться, поэтому подтвердите проблему другим сервисом.",
                    metricBadge = "${pingSummary.packetLossPercent}% потерь"
                )
            }
            pingSummary.avgRttMs > 75f -> {
                AuditRecommendation(
                    id = "rec_lat_high",
                    category = AuditCategory.NETWORK_LATENCY,
                    severity = AuditSeverity.WARNING,
                    title = "Повышенная задержка сети (${pingSummary.avgRttMs.toInt()} мс)",
                    summary = "Средний RTT до измеренной цели составил ${pingSummary.avgRttMs.toInt()} мс. Оценка зависит от расстояния и маршрута: для локального шлюза и удалённого сервера это разные базовые уровни. Одно измерение не выявляет буферблоат.",
                    actionGuide = "Сравните RTT до шлюза и внешней цели в простое и при загрузке канала. Высокая задержка уже до шлюза — проверьте локальную сеть; рост только под нагрузкой может указывать на очереди/буферблоат. Смена диапазона сама по себе его не устраняет.",
                    metricBadge = "${pingSummary.avgRttMs.toInt()} мс (Задержка)"
                )
            }
            else -> {
                AuditRecommendation(
                    id = "rec_lat_opt",
                    category = AuditCategory.NETWORK_LATENCY,
                    severity = AuditSeverity.OPTIMAL,
                    title = "Потери не обнаружены в этой серии Ping",
                    summary = "До выбранной цели получен ответ со средним RTT ${pingSummary.avgRttMs.toInt()} мс и без зарегистрированных потерь в ${pingSummary.packetsTransmitted} пробах. Небольшая выборка не гарантирует качество при нагрузке или для других сервисов.",
                    actionGuide = "Сохраните результат как контрольный замер. Для голосовых и игровых задач повторите тест при обычной нагрузке и сравните минимальный, средний и максимальный RTT.",
                    metricBadge = "${pingSummary.avgRttMs.toInt()} мс"
                )
            }
        }
    }

    private fun evaluateBandUtilization(targetGroup: WifiNetworkGroup?): AuditRecommendation {
        val bestBand = targetGroup?.bestBand ?: WifiBand.BAND_2_4_GHZ
        return if (bestBand == WifiBand.BAND_5_GHZ || bestBand == WifiBand.BAND_6_GHZ) {
            AuditRecommendation(
                id = "rec_band_5g",
                category = AuditCategory.TOPOLOGY_EFFICIENCY,
                severity = AuditSeverity.OPTIMAL,
                title = "Используется высокоскоростной диапазон $bestBand",
                summary = "Подключение обнаружено в диапазоне $bestBand. Реальная скорость определяется стандартом и возможностями клиента и точки, шириной канала, сигналом, эфирным временем и интернет-тарифом; сам диапазон не гарантирует гигабит.",
                actionGuide = "Для каждого важного устройства проверьте фактическую скорость и стабильность в месте использования. Учитывайте дальность, стены, поддержку 6 ГГц и правила страны; диапазон 2,4 ГГц иногда стабильнее на большом расстоянии.",
                metricBadge = bestBand.displayName
            )
        } else {
            AuditRecommendation(
                id = "rec_band_24g",
                category = AuditCategory.TOPOLOGY_EFFICIENCY,
                severity = AuditSeverity.WARNING,
                title = "Подключение работает в узком диапазоне 2.4 ГГц",
                summary = "Устройство использует 2,4 ГГц. Этот диапазон обычно лучше проходит через препятствия, но имеет меньше неперекрывающихся каналов и может быть загружен Wi-Fi и другими радиоустройствами.",
                actionGuide = "Если клиент и точка поддерживают 5 ГГц, сравните фактическую скорость и потери в нужном месте. 5 ГГц часто быстрее рядом с точкой, но быстрее ослабевает через стены; гарантированного кратного прироста нет.",
                metricBadge = "2.4 ГГц (Медленно)"
            )
        }
    }
}
