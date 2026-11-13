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
            totalScore >= 85 -> "Отличное состояние сети" to "Беспроводная среда настроена корректно, помехи минимальны."
            totalScore >= 65 -> "Удовлетворительно, есть замечания" to "Обнаружены перегруженные каналы или устаревшие протоколы."
            else -> "Требуется вмешательство администратора" to "Зафиксированы критические уязвимости безопасности или сильные радиопомехи."
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
                    title = "Критическая зашумленность канала CH $targetChannel",
                    summary = "На канале $targetChannel одновременно вещают $sameChannelCount конкурирующих точек доступа. Это приводит к коллизиям кадров (Co-Channel Interference) и резкому росту пинга.",
                    actionGuide = "Войдите в панель управления роутера (обычно 192.168.1.1 или 192.168.0.1) в раздел «Беспроводная сеть» и смените канал с $targetChannel на свободный канал $cleanCandidate (или включите режим 5 ГГц).",
                    metricBadge = "$sameChannelCount сетей на канале"
                )
            }
            sameChannelCount >= 3 -> {
                AuditRecommendation(
                    id = "rec_channel_moderate",
                    category = AuditCategory.CHANNEL_CONGESTION,
                    severity = AuditSeverity.WARNING,
                    title = "Умеренная интерференция на канале CH $targetChannel",
                    summary = "На вашем канале зафиксировано $sameChannelCount соседних сетей. Возможны периодические микрофризы при передаче потокового видео и в онлайн-играх.",
                    actionGuide = "Рекомендуется переключить ширину канала с 40 МГц на 20 МГц в диапазоне 2.4 ГГц либо выбрать свободный канал $cleanCandidate.",
                    metricBadge = "Конкуренция: $sameChannelCount AP"
                )
            }
            else -> {
                AuditRecommendation(
                    id = "rec_channel_optimal",
                    category = AuditCategory.CHANNEL_CONGESTION,
                    severity = AuditSeverity.OPTIMAL,
                    title = "Канал CH $targetChannel чист от помех",
                    summary = "Текущий радиоканал свободен от взаимного влияния соседних точек доступа. Передача данных идет с максимальной пропускной способностью.",
                    actionGuide = "Никаких действий не требуется. Автоматический выбор каналов на роутере работает исправно.",
                    metricBadge = "Чистый спектр"
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
                    summary = "Уровень сигнала находится в красной зоне (< -78 дБм). Мобильное устройство вынуждено сбрасывать схему модуляции (MCS) до минимума, вызывая частые обрывы TCP-сессий.",
                    actionGuide = "Сократите расстояние до роутера, перенесите роутер из шкафа/ниши на открытое пространство или добавьте дополнительный Mesh-узел в данной комнате.",
                    metricBadge = "$rssi дБм (Критично)"
                )
            }
            rssi < -68 -> {
                AuditRecommendation(
                    id = "rec_signal_warning",
                    category = AuditCategory.SIGNAL_COVERAGE,
                    severity = AuditSeverity.WARNING,
                    title = "Пограничный уровень приема ($rssi дБм)",
                    summary = "Мощности сигнала достаточно для серфинга, однако при высокой нагрузке возможны задержки. Сигнал ослаблен межкомнатными стенами или перекрытиями.",
                    actionGuide = "Если ваше устройство подключено к 5 ГГц, проверьте, улучшится ли стабильность на 2.4 ГГц (обладает большей проникающей способностью сквозь стены).",
                    metricBadge = "$rssi дБм (Средний)"
                )
            }
            else -> {
                AuditRecommendation(
                    id = "rec_signal_optimal",
                    category = AuditCategory.SIGNAL_COVERAGE,
                    severity = AuditSeverity.OPTIMAL,
                    title = "Отличное радиопокрытие ($rssi дБм)",
                    summary = "Мощности сигнала достаточно для максимальных скоростей модуляции Wi-Fi (до 1024-QAM / 4096-QAM) без потерь пакетов.",
                    actionGuide = "Расположение оборудования оптимально.",
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
                    summary = "Весь пользовательский трафик в радиоэфире передается в открытом виде. Злоумышленник поблизости может перехватывать незащищенные запросы и DNS-обращения.",
                    actionGuide = "Немедленно установите пароль в настройках роутера. Выберите WPA2-Personal (AES) с уникальным паролем длиной не менее 12–16 символов.",
                    metricBadge = "Без защиты"
                )
            }
            SecurityType.WEP -> {
                AuditRecommendation(
                    id = "rec_sec_wep",
                    category = AuditCategory.SECURITY_PROTOCOL,
                    severity = AuditSeverity.CRITICAL,
                    title = "Устаревший протокол WEP взломан",
                    summary = "Алгоритм WEP использует уязвимый RC4 с короткими векторами инициализации (IV). Пароль от такой сети подбирается за 2-3 минуты.",
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
                    actionGuide = "Если используется WPA2-Personal (AES) и уникальный пароль от 12–16 символов, менять шифрование только ради версии протокола не требуется. Не используйте WEP или открытый режим.",
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
                    summary = "Под именем «${targetGroup.ssid}» работают точки разных производителей (${targetGroup.primaryVendor} и др.). Однодиапазонные репитеры делят полосу пополам и не поддерживают бесшовный роуминг.",
                    actionGuide = "Замените разрозненные репитеры на единую двухдиапазонную Mesh-систему (например, Keenetic, TP-Link Deco или ASUS AiMesh) для непрерывного переключения без обрывов связи.",
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
                    summary = "Сеть объединена в единую топологию вендора ${targetGroup.primaryVendor}. Обнаружена поддержка: $roamingText.",
                    actionGuide = "Клиентские устройства плавно переключаются на узел с лучшим уровнем сигнала без разрыва голосовых вызовов и загрузок.",
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
                    summary = "Зафиксирована потеря данных при пинге. Это свидетельствует о сильной радиоинтерференции или перегрузке центрального процессора роутера.",
                    actionGuide = "Перезагрузите роутер, проверьте надежность кабельного ввода и убедитесь, что никто в сети не загружает торренты на полную полосу.",
                    metricBadge = "${pingSummary.packetLossPercent}% потерь"
                )
            }
            pingSummary.avgRttMs > 75f -> {
                AuditRecommendation(
                    id = "rec_lat_high",
                    category = AuditCategory.NETWORK_LATENCY,
                    severity = AuditSeverity.WARNING,
                    title = "Повышенная задержка сети (${pingSummary.avgRttMs.toInt()} мс)",
                    summary = "Среднее время отклика превышает норму для локальных сетей (< 20 мс). В онлайн-играх и звонках может наблюдаться рассинхронизация.",
                    actionGuide = "Переключитесь с диапазона 2.4 ГГц на 5 ГГц для снижения сетевого буферблоата (Bufferbloat).",
                    metricBadge = "${pingSummary.avgRttMs.toInt()} мс (Задержка)"
                )
            }
            else -> {
                AuditRecommendation(
                    id = "rec_lat_opt",
                    category = AuditCategory.NETWORK_LATENCY,
                    severity = AuditSeverity.OPTIMAL,
                    title = "Идеальный отклик сети (${pingSummary.avgRttMs.toInt()} мс)",
                    summary = "Задержка минимальна, потери пакетов отсутствуют. Канал отлично подходит для онлайн-игр, 4K-стриминга и видеоконференций.",
                    actionGuide = "Качество канала в норме.",
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
                summary = "Ширина полосы до 80/160 МГц обеспечивает гигабитную скорость и минимальную зашумленность от бытовых приборов.",
                actionGuide = "Рекомендуется держать критичные устройства в диапазоне 5 ГГц / 6 ГГц.",
                metricBadge = bestBand.displayName
            )
        } else {
            AuditRecommendation(
                id = "rec_band_24g",
                category = AuditCategory.TOPOLOGY_EFFICIENCY,
                severity = AuditSeverity.WARNING,
                title = "Подключение работает в узком диапазоне 2.4 ГГц",
                summary = "Диапазон 2.4 ГГц ограничен по скорости и делит частоты с микроволновками, Bluetooth-устройствами и десятками соседних роутеров.",
                actionGuide = "Если ваш роутер поддерживает 5 ГГц (Dual-Band), подключитесь к сети 5G для прироста скорости в 3-5 раз.",
                metricBadge = "2.4 ГГц (Медленно)"
            )
        }
    }
}
