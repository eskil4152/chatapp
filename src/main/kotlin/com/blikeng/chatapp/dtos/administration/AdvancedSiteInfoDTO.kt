package com.blikeng.chatapp.dtos.administration

data class HttpStatusCount(
    val status: Int,
    val count: Long,
)

data class HttpEndpointMetric(
    val uri: String,
    val method: String,
    val statuses: List<HttpStatusCount>,
    val totalCount: Long,
    val errorRate: Double,
    val meanMs: Double,
    val maxMs: Double,
)

data class AdvancedSiteInfoDTO(
    val jvmMemoryUsedMb: Double,
    val jvmMemoryMaxMb: Double,
    val jvmMemoryCommittedMb: Double,
    val jvmThreadsLive: Int,
    val jvmThreadsPeak: Int,
    val cpuUsagePercent: Double,
    val gcPauseMeanMs: Double,
    val gcPauseMaxMs: Double,
    val uptimeSeconds: Long,
    val httpRequests: List<HttpEndpointMetric>,
)
