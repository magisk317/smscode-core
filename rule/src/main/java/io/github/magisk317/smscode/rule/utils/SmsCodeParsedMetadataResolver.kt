package io.github.magisk317.smscode.rule.utils

object SmsCodeParsedMetadataResolver {
    data class Result(
        val company: String,
        val packageName: String?,
    )

    fun resolve(
        body: String,
        parseCompanyCandidates: (String) -> List<String>,
        parseCompany: (String) -> String,
        findPackageNameByLabel: (String) -> String?,
    ): Result {
        val candidates = parseCompanyCandidates(body)
            .map(::normalizeCompanyLabel)
            .filter { it.isNotBlank() }
        var company = normalizeCompanyLabel(parseCompany(body))
        var resolvedPackageName: String? = null

        for (candidate in candidates) {
            val packageName = findPackageNameByLabel(candidate)
            if (!packageName.isNullOrBlank()) {
                company = candidate
                resolvedPackageName = packageName
                break
            }
        }

        if (resolvedPackageName.isNullOrBlank()) {
            resolvedPackageName = findPackageNameByLabel(company)
        }
        return Result(
            company = company,
            packageName = resolvedPackageName,
        )
    }

    fun normalizeCompanyLabel(value: String): String {
        return value.trim().trim('【', '】', '[', ']')
    }
}
