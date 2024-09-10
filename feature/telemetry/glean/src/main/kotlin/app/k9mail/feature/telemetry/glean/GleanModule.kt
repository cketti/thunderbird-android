package app.k9mail.feature.telemetry.glean

import app.k9mail.feature.telemetry.api.TelemetryManager
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

val gleanModule: Module = module {
    single {
        GleanTelemetryManager(
            context = get(),
            okHttpClient = lazy { get() },
        )
    } bind TelemetryManager::class
}
