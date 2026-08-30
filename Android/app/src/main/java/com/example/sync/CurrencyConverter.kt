package com.example.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object CurrencyConverter {
    private val ratesCache = ConcurrentHashMap<Pair<String, String>, Double>()
    private val rateTimes = ConcurrentHashMap<Pair<String, String>, Long>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    val supportedCurrencies = listOf(
        "CNY", "HKD", "USD", "EUR", "GBP", "JPY", "TWD", "KRW", "SGD", "AUD", "CAD"
    )

    private val fallbackRates = mapOf(
        Pair("HKD", "CNY") to 0.92,
        Pair("CNY", "HKD") to 1.087,
        Pair("USD", "CNY") to 7.25,
        Pair("CNY", "USD") to 0.138,
        Pair("USD", "HKD") to 7.82,
        Pair("HKD", "USD") to 0.128,
        Pair("EUR", "CNY") to 7.85,
        Pair("CNY", "EUR") to 0.127,
        Pair("GBP", "CNY") to 9.15,
        Pair("CNY", "GBP") to 0.109,
        Pair("JPY", "CNY") to 0.048,
        Pair("CNY", "JPY") to 20.83,
        Pair("USD", "EUR") to 0.92,
        Pair("EUR", "USD") to 1.087,
        Pair("USD", "GBP") to 0.79,
        Pair("GBP", "USD") to 1.265,
        Pair("USD", "JPY") to 155.0,
        Pair("JPY", "USD") to 0.00645,
        Pair("USD", "TWD") to 32.5,
        Pair("TWD", "USD") to 0.0308,
        Pair("USD", "KRW") to 1380.0,
        Pair("KRW", "USD") to 0.00072,
        Pair("USD", "SGD") to 1.35,
        Pair("SGD", "USD") to 0.74,
        Pair("USD", "AUD") to 1.52,
        Pair("AUD", "USD") to 0.658,
        Pair("USD", "CAD") to 1.36,
        Pair("CAD", "USD") to 0.735
    )

    fun symbol(currency: String): String {
        return when (currency.uppercase()) {
            "CNY" -> "¥"
            "HKD" -> "HK$"
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            "JPY" -> "¥"
            "TWD" -> "NT$"
            "KRW" -> "₩"
            "SGD" -> "S$"
            "AUD" -> "A$"
            "CAD" -> "C$"
            else -> "$currency "
        }
    }

    fun getRate(fromCurrency: String, toCurrency: String): Double {
        val from = fromCurrency.uppercase().trim()
        val to = toCurrency.uppercase().trim()
        if (from == to || from.isEmpty() || to.isEmpty()) return 1.0

        val key = Pair(from, to)
        ratesCache[key]?.let { return it }

        fallbackRates[key]?.let {
            ratesCache[key] = it
            return it
        }

        val reverseKey = Pair(to, from)
        fallbackRates[reverseKey]?.let {
            val inverted = 1.0 / it
            ratesCache[key] = inverted
            return inverted
        }

        // Cross-rate via USD
        val fromToUsd = if (from == "USD") 1.0 else (fallbackRates[Pair(from, "USD")] ?: (1.0 / (fallbackRates[Pair("USD", from)] ?: 1.0)))
        val usdToTarget = if (to == "USD") 1.0 else (fallbackRates[Pair("USD", to)] ?: (1.0 / (fallbackRates[Pair(to, "USD")] ?: 1.0)))
        val rate = fromToUsd * usdToTarget
        ratesCache[key] = rate
        return rate
    }

    fun convert(amount: Double, fromCurrency: String, toCurrency: String): Double {
        return amount * getRate(fromCurrency, toCurrency)
    }

    fun getRateTime(fromCurrency: String, toCurrency: String): Long? {
        return rateTimes[Pair(fromCurrency.uppercase(), toCurrency.uppercase())]
    }

    fun refreshCache() {
        ratesCache.clear()
        rateTimes.clear()
    }

    suspend fun fetchLiveRates(baseCurrency: String, currencies: List<String>): Map<String, Double> {
        return withContext(Dispatchers.IO) {
            val base = baseCurrency.uppercase()
            val targets = currencies.map { it.uppercase() }.distinct()
            val result = mutableMapOf<String, Double>()
            result[base] = 1.0
            val now = System.currentTimeMillis()

            // base -> cur：直接抓 latest/{base} 讀 rates[cur]
            // （與 Python 端 get_rate(base_currency, code)/get_all_rates() 一致）
            try {
                val request = Request.Builder().url("https://open.er-api.com/v6/latest/$base").build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val json = JSONObject(body)
                        if (json.optString("result") == "success" && json.has("rates")) {
                            val ratesObj = json.getJSONObject("rates")
                            for (c in targets) {
                                if (c == base) continue
                                if (ratesObj.has(c)) {
                                    val r = ratesObj.getDouble(c) // base -> cur 直接匯率
                                    result[c] = r
                                    ratesCache[Pair(base, c)] = r
                                    rateTimes[Pair(base, c)] = now
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // 忽略：單一請求失敗不影響其餘幣別
            }

            // cur -> base：對每個幣別抓 latest/{cur} 直接讀 rates[base]
            // （與 Python 端 get_rate(cur, base_currency) 一致；不從 base 表取倒數，
            //   避免倒數捨入差——CNY 大部位會差約 0.81 HKD）
            for (cur in targets) {
                val c = cur.uppercase()
                if (c == base) continue
                try {
                    val request = Request.Builder().url("https://open.er-api.com/v6/latest/$c").build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            val json = JSONObject(body)
                            if (json.optString("result") == "success" && json.has("rates")) {
                                val ratesObj = json.getJSONObject("rates")
                                if (ratesObj.has(base)) {
                                    val direct = ratesObj.getDouble(base) // cur -> base 直接匯率
                                    ratesCache[Pair(c, base)] = direct
                                    rateTimes[Pair(c, base)] = now
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // 單一幣別失敗：退化為離線/靜態匯率
                    if (!ratesCache.containsKey(Pair(c, base))) {
                        ratesCache[Pair(c, base)] = getRate(c, base)
                        ratesCache[Pair(base, c)] = getRate(base, c)
                    }
                }
            }
            result
        }
    }
}
