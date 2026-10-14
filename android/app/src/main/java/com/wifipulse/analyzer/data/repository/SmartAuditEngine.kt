package com.wifipulse.analyzer.data.repository

import com.wifipulse.analyzer.data.model.*

class SmartAuditEngine {

    fun generateReport(
        networkGroups: List<WifiNetworkGroup>,
        connectionDetails: WifiConnectionDetails,
        lastPingSummary: PingSummary? = null
    ): AuditReport {
        val recommendations = mutableListOf<AuditRecommendation>()

        // Find current or strongest network
        val activeGroup = networkGroups.find { it.ssid == connectionDetails.ssid && connectionDetails.isConnected }
            ?: networkGroups.maxByOrNull { it.bestRssi }

        // 1. Channel Congestion Recommendation
        recommendations.add(evaluateChannelCongestion(networkGroups, activeGroup))

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

        // Calculate score
        var totalScore = 100
        var criticals = 0
        var warnings = 0
        var optimals = 0

        for (rec in recommendations) {
            when (rec.severity) {
                AuditSeverity.CRITICAL -> {
                    totalScore -= 25
                    criticals++
                }
                AuditSeverity.WARNING -> {
                    totalScore -= 12
                    warnings++
                }
                AuditSeverity.OPTIMAL -> {
                    optimals++
                }
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
            recommendations = recommendations.take(4),
            scannedNetworksCount = networkGroups.size,
            optimalCount = optimals,
            warningCount = warnings,
            criticalCount = criticals
        )
    }

    private fun evaluateChannelCongestion(
        allGroups: List<WifiNetworkGroup>,
        targetGroup: WifiNetworkGroup?
    ): AuditRecommendation {
        val allNodes = allGroups.flatMap { it.nodes }
        val targetChannel = targetGroup?.bestNode?.channel ?: 6
        val targetBand = targetGroup?.bestBand ?: WifiBand.BAND_2_4_GHZ

        // Count overlapping channels
        val sameChannelCount = allNodes.count { it.channel == targetChannel }

        // Find cleanest standard channel in 2.4 GHz (1, 6, 11) or 5 GHz (36, 44, 149)
        val cleanCandidate = if (targetBand == WifiBand.BAND_2_4_GHZ) {
            listOf(1, 6, 11).minByOrNull { ch -> allNodes.count { it.channel == ch } } ?: 1
        } else {
            listOf(36, 44, 48, 149).minByOrNull { ch -> allNodes.count { it.channel == ch } } ?: 36
        }

        return when {
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
        val sec = targetGroup?.bestSecurity ?: SecurityType.WPA2_PSK

        return when (sec) {
            SecurityType.OPEN -> {
                AuditRecommendation(
                    id = "rec_sec_open",
                    category = AuditCategory.SECURITY_PROTOCOL,
                    severity = AuditSeverity.CRITICAL,
                    title = "Открытая сеть без шифрования (Open)",
                    summary = "Весь пользовательский трафик в радиоэфире передается в открытом виде. Злоумышленник поблизости может перехватывать незащищенные запросы и DNS-обращения.",
                    actionGuide = "Немедленно установите пароль в настройках роутера. Выберите протокол WPA2-Personal (AES) или WPA3-Personal с паролем длиной от 12 символов.",
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
                    actionGuide = "Переключите тип безопасности роутера на WPA2-PSK (AES) или современный WPA3-SAE.",
                    metricBadge = "Устаревший WEP"
                )
            }
            SecurityType.WPA_PSK, SecurityType.WPA_WPA2_MIXED, SecurityType.WPA2_PSK -> {
                AuditRecommendation(
                    id = "rec_sec_wpa2",
                    category = AuditCategory.SECURITY_PROTOCOL,
                    severity = AuditSeverity.WARNING,
                    title = "Рекомендуется переход на WPA3-SAE",
                    summary = "Сеть защищена протоколом WPA2-PSK. Он надежен при длинном пароле, однако уязвим к атакам деаутентификации и оффлайн-перебору по перехваченному 4-way handshake.",
                    actionGuide = "Если ваш роутер поддерживает Wi-Fi 6 или обновленные прошивки, включите смешанный режим «WPA2/WPA3-Personal» для надежной защиты от подбора паролей.",
                    metricBadge = "WPA2-PSK (Базовый)"
                )
            }
            SecurityType.WPA3_SAE, SecurityType.WPA2_WPA3_MIXED -> {
                AuditRecommendation(
                    id = "rec_sec_wpa3",
                    category = AuditCategory.SECURITY_PROTOCOL,
                    severity = AuditSeverity.OPTIMAL,
                    title = "Высочайший уровень безопасности WPA3-SAE",
                    summary = "Активирован протокол одновременной аутентификации равных (Simultaneous Authentication of Equals). Защищен от перебора по словарю даже при простом пароле.",
                    actionGuide = "Конфигурация безопасности безупречна.",
                    metricBadge = "WPA3 Активен"
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
