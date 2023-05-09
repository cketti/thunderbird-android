package app.k9mail.autodiscovery.autoconfig

import app.k9mail.autodiscovery.api.ConnectionSettingsDiscovery
import app.k9mail.autodiscovery.api.DiscoveryResults
import com.fsck.k9.helper.EmailHelper

class MxLookupAutoconfigDiscovery(
    private val mxResolver: MxResolver,
    private val baseDomainExtractor: BaseDomainExtractor,
    private val urlProvider: AutoconfigUrlProvider,
    private val fetcher: AutoconfigFetcher,
    private val parser: AutoconfigParser,
) : ConnectionSettingsDiscovery {

    override fun discover(email: String): DiscoveryResults? {
        val domain = requireNotNull(EmailHelper.getDomainFromEmailAddress(email)) {
            "Couldn't extract domain from email address: $email"
        }

        return mxResolver.lookup(domain)
            .asSequence()
            .map { mxName -> baseDomainExtractor.extractBaseDomain(mxName) }
            .distinct()
            .filterNot { baseDomain -> baseDomain == domain }
            .flatMap { baseDomain -> urlProvider.getAutoconfigUrls(baseDomain) }
            .mapNotNull { autoconfigUrl ->
                fetcher.fetchAutoconfigFile(autoconfigUrl)?.use { inputStream ->
                    parser.parseSettings(inputStream, email)
                }
            }
            .firstOrNull()
    }
}
