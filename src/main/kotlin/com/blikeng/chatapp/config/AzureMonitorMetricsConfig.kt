package com.blikeng.chatapp.config

import io.micrometer.azuremonitor.AzureMonitorConfig
import io.micrometer.azuremonitor.AzureMonitorMeterRegistry
import io.micrometer.core.instrument.Clock
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AzureMonitorMetricsConfig {
    @Bean
    fun azureMonitorMeterRegistry(
        @Value(
            "\${APPLICATIONINSIGHTS_CONNECTION_STRING:InstrumentationKey=00000000-0000-0000-0000-000000000000;IngestionEndpoint=https://westeurope-0.in.applicationinsights.azure.com/}",
        )
        connectionString: String,
    ): AzureMonitorMeterRegistry {
        val config =
            object : AzureMonitorConfig {
                override fun get(key: String): String? = null

                override fun connectionString(): String = connectionString
            }

        return AzureMonitorMeterRegistry(config, Clock.SYSTEM)
    }
}
