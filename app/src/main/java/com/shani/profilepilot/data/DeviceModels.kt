package com.shani.profilepilot.data

/**
 * Searchable device-model picker: real popular Android models with real
 * Chrome-Mobile user-agent strings. Picking a model sets the account's UA,
 * which is what Facebook shows under "Where you're logged in".
 * Android 12–14, Chrome 120–128. Only real public model identifiers are used.
 */
data class DeviceModel(val name: String, val userAgent: String)

object DeviceModels {

    private fun ua(android: Int, model: String, chrome: Int): String =
        "Mozilla/5.0 (Linux; Android $android; $model) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/$chrome.0.0.0 Mobile Safari/537.36"

    val models: List<DeviceModel> = listOf(
        // ---- Samsung ----
        DeviceModel("Samsung Galaxy S24", ua(14, "SM-S921B", 126)),
        DeviceModel("Samsung Galaxy S24+", ua(14, "SM-S926B", 126)),
        DeviceModel("Samsung Galaxy S24 Ultra", ua(14, "SM-S928B", 127)),
        DeviceModel("Samsung Galaxy S24 FE", ua(14, "SM-S721B", 128)),
        DeviceModel("Samsung Galaxy S23", ua(14, "SM-S911B", 125)),
        DeviceModel("Samsung Galaxy S23 FE", ua(14, "SM-S711B", 126)),
        DeviceModel("Samsung Galaxy S23 Ultra", ua(14, "SM-S918B", 125)),
        DeviceModel("Samsung Galaxy S22", ua(13, "SM-S901B", 124)),
        DeviceModel("Samsung Galaxy S22 Ultra", ua(13, "SM-S908B", 124)),
        DeviceModel("Samsung Galaxy S21", ua(13, "SM-G991B", 123)),
        DeviceModel("Samsung Galaxy A55", ua(14, "SM-A556B", 127)),
        DeviceModel("Samsung Galaxy A54", ua(14, "SM-A546B", 126)),
        DeviceModel("Samsung Galaxy A53", ua(14, "SM-A536B", 125)),
        DeviceModel("Samsung Galaxy A35", ua(14, "SM-A356B", 127)),
        DeviceModel("Samsung Galaxy A34", ua(14, "SM-A346B", 126)),
        DeviceModel("Samsung Galaxy A25", ua(14, "SM-A256B", 127)),
        DeviceModel("Samsung Galaxy A15", ua(14, "SM-A155F", 126)),
        DeviceModel("Samsung Galaxy A14", ua(13, "SM-A145F", 124)),
        DeviceModel("Samsung Galaxy A05s", ua(13, "SM-A057F", 124)),
        DeviceModel("Samsung Galaxy Z Flip 5", ua(14, "SM-F731B", 126)),
        DeviceModel("Samsung Galaxy Z Fold 5", ua(14, "SM-F946B", 126)),
        DeviceModel("Samsung Galaxy Z Flip 4", ua(13, "SM-F721B", 123)),
        DeviceModel("Samsung Galaxy M34", ua(14, "SM-M346B", 126)),
        DeviceModel("Samsung Galaxy M14", ua(13, "SM-M146B", 124)),
        // ---- Google Pixel ----
        DeviceModel("Google Pixel 8 Pro", ua(14, "Pixel 8 Pro", 126)),
        DeviceModel("Google Pixel 8", ua(14, "Pixel 8", 126)),
        DeviceModel("Google Pixel 8a", ua(14, "Pixel 8a", 127)),
        DeviceModel("Google Pixel 7 Pro", ua(14, "Pixel 7 Pro", 125)),
        DeviceModel("Google Pixel 7", ua(14, "Pixel 7", 125)),
        DeviceModel("Google Pixel 7a", ua(14, "Pixel 7a", 125)),
        DeviceModel("Google Pixel 6 Pro", ua(13, "Pixel 6 Pro", 123)),
        DeviceModel("Google Pixel 6", ua(13, "Pixel 6", 123)),
        DeviceModel("Google Pixel 6a", ua(14, "Pixel 6a", 124)),
        DeviceModel("Google Pixel Fold", ua(14, "Pixel Fold", 126)),
        // ---- Xiaomi ----
        DeviceModel("Xiaomi 14", ua(14, "23127PN0CG", 127)),
        DeviceModel("Xiaomi 13", ua(13, "2211133G", 124)),
        DeviceModel("Xiaomi 13 Pro", ua(13, "2210132G", 124)),
        DeviceModel("Xiaomi 13T", ua(14, "2306EPN60G", 126)),
        DeviceModel("Xiaomi 13T Pro", ua(14, "23078PND5G", 126)),
        DeviceModel("Xiaomi 12", ua(13, "2201123G", 123)),
        DeviceModel("Xiaomi 12T", ua(13, "22071212AG", 124)),
        DeviceModel("Xiaomi 11T", ua(12, "2109119DG", 121)),
        // ---- Redmi ----
        DeviceModel("Redmi Note 13 Pro", ua(14, "2312DRA50G", 127)),
        DeviceModel("Redmi Note 13", ua(14, "23129RAA4G", 127)),
        DeviceModel("Redmi Note 12 Pro", ua(13, "22101316G", 124)),
        DeviceModel("Redmi Note 12", ua(13, "23021RAAEG", 124)),
        DeviceModel("Redmi Note 11", ua(12, "2201117TG", 121)),
        DeviceModel("Redmi 13C", ua(13, "23124RN87G", 125)),
        DeviceModel("Redmi 12", ua(13, "23053RN02A", 124)),
        // ---- POCO ----
        DeviceModel("POCO X6 Pro", ua(14, "23122PCD1G", 127)),
        DeviceModel("POCO X6", ua(14, "23122PCD1I", 127)),
        DeviceModel("POCO X5 Pro", ua(12, "22101320G", 122)),
        DeviceModel("POCO F5 Pro", ua(13, "23013PC75I", 124)),
        DeviceModel("POCO F5", ua(13, "23013PC75G", 124)),
        DeviceModel("POCO M6 Pro", ua(13, "2312FPCA6G", 125)),
        // ---- Oppo ----
        DeviceModel("Oppo Find X7 Ultra", ua(14, "PKT110", 127)),
        DeviceModel("Oppo Find X6 Pro", ua(13, "PGM110", 124)),
        DeviceModel("Oppo Reno 11 Pro", ua(14, "CPH2609", 126)),
        DeviceModel("Oppo Reno 11", ua(14, "CPH2607", 126)),
        DeviceModel("Oppo Reno 10", ua(13, "CPH2521", 124)),
        DeviceModel("Oppo A78", ua(13, "CPH2565", 124)),
        DeviceModel("Oppo A58", ua(13, "CPH2577", 124)),
        // ---- Vivo ----
        DeviceModel("Vivo X100", ua(14, "V2308", 127)),
        DeviceModel("Vivo X90", ua(13, "V2219", 123)),
        DeviceModel("Vivo V30 Pro", ua(14, "V2319", 127)),
        DeviceModel("Vivo V30", ua(14, "V2318", 127)),
        DeviceModel("Vivo V29", ua(13, "V2250", 125)),
        DeviceModel("Vivo Y36", ua(13, "V2247", 124)),
        DeviceModel("Vivo Y28", ua(14, "V2352", 127)),
        DeviceModel("Vivo Y27", ua(13, "V2248", 124)),
        // ---- Realme ----
        DeviceModel("Realme GT 6", ua(14, "RMX3851", 127)),
        DeviceModel("Realme 12 Pro+", ua(14, "RMX3840", 127)),
        DeviceModel("Realme 12 Pro", ua(14, "RMX3842", 127)),
        DeviceModel("Realme 11 Pro+", ua(13, "RMX3741", 124)),
        DeviceModel("Realme 11", ua(13, "RMX3636", 124)),
        DeviceModel("Realme 10", ua(12, "RMX3630", 121)),
        DeviceModel("Realme C67", ua(14, "RMX3890", 126)),
        DeviceModel("Realme C55", ua(13, "RMX3710", 124)),
        // ---- OnePlus ----
        DeviceModel("OnePlus 12", ua(14, "CPH2581", 127)),
        DeviceModel("OnePlus 12R", ua(14, "CPH2585", 127)),
        DeviceModel("OnePlus 11", ua(14, "CPH2449", 125)),
        DeviceModel("OnePlus 11R", ua(13, "CPH2487", 124)),
        DeviceModel("OnePlus 10 Pro", ua(13, "NE2213", 123)),
        DeviceModel("OnePlus Nord 4", ua(14, "CPH2663", 127)),
        DeviceModel("OnePlus Nord 3", ua(13, "CPH2491", 124)),
        DeviceModel("OnePlus Nord CE 3 Lite", ua(13, "CPH2465", 124)),
        // ---- Motorola ----
        DeviceModel("Moto G85", ua(14, "XT2427", 127)),
        DeviceModel("Moto G73", ua(13, "XT2337", 124)),
        DeviceModel("Moto G54", ua(13, "XT2343", 124)),
        DeviceModel("Moto G34", ua(14, "XT2399", 126)),
        DeviceModel("Moto Edge 40 Neo", ua(13, "XT2307", 124)),
        DeviceModel("Moto Edge 40", ua(13, "XT2303", 124)),
        // ---- Infinix ----
        DeviceModel("Infinix GT 20", ua(14, "X6812", 127)),
        DeviceModel("Infinix Note 40 Pro", ua(14, "X6850", 127)),
        DeviceModel("Infinix Note 40", ua(14, "X6853", 127)),
        DeviceModel("Infinix Zero 30", ua(13, "X6731", 124)),
        DeviceModel("Infinix Hot 40", ua(13, "X6836", 125)),
        DeviceModel("Infinix Hot 40i", ua(13, "X6833B", 125)),
        // ---- Tecno ----
        DeviceModel("Tecno Pova 6", ua(14, "LI7", 127)),
        DeviceModel("Tecno Camon 30 Pro", ua(14, "CL7", 127)),
        DeviceModel("Tecno Camon 30", ua(14, "CL6", 127)),
        DeviceModel("Tecno Spark 20 Pro", ua(13, "KJ6", 125)),
        DeviceModel("Tecno Spark 20", ua(13, "KJ5", 125)),
        // ---- Honor ----
        DeviceModel("Honor Magic 6 Lite", ua(14, "ALI-NX1", 127)),
        DeviceModel("Honor Magic 5 Lite", ua(13, "RMO-NX3", 124)),
        DeviceModel("Honor 200 Lite", ua(14, "LLY-NX1", 127)),
        DeviceModel("Honor 90", ua(13, "REA-NX9", 124)),
        DeviceModel("Honor X9b", ua(13, "ALI-NX1", 125)),
        // ---- Nothing ----
        DeviceModel("Nothing Phone 2a", ua(14, "A142", 127)),
        DeviceModel("Nothing Phone 2", ua(14, "A065", 126)),
        DeviceModel("Nothing Phone 1", ua(13, "A063", 123))
    )

    /**
     * v1.5: brand folders — "Samsung Galaxy S24" -> brand "Samsung".
     * Order wahi jo list me hai.
     */
    val brands: List<Pair<String, List<DeviceModel>>> by lazy {
        val order = linkedMapOf<String, MutableList<DeviceModel>>()
        for (m in models) {
            val brand = m.name.substringBefore(" ")
            order.getOrPut(brand) { mutableListOf() }.add(m)
        }
        order.map { it.key to it.value.toList() }
    }
}


