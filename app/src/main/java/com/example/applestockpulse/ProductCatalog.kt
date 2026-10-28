package com.example.applestockpulse

import androidx.compose.ui.graphics.Color

data class ProductInfo(
    val sku: String,
    val name: String,
    val model: String,
    val storage: String,
    val color: String,
    val colorHex: Color = Color.Gray,
    val isFlagship: Boolean = true
)

object ProductCatalog {

    val CATALOG: List<ProductInfo> = listOf(
        // ==================== iPhone Duo (Foldable) ====================
        ProductInfo(
            sku = "MK244VC/A",
            name = "iPhone Duo 256GB Star White",
            model = "iPhone Duo",
            storage = "256GB",
            color = "Star White",
            colorHex = Color(0xFFF8FAFC)
        ),
        ProductInfo(
            sku = "MK254VC/A",
            name = "iPhone Duo 256GB Night Sky",
            model = "iPhone Duo",
            storage = "256GB",
            color = "Night Sky",
            colorHex = Color(0xFF0F172A)
        ),
        ProductInfo(
            sku = "MK264VC/A",
            name = "iPhone Duo 512GB Star White",
            model = "iPhone Duo",
            storage = "512GB",
            color = "Star White",
            colorHex = Color(0xFFF8FAFC)
        ),
        ProductInfo(
            sku = "MK274VC/A",
            name = "iPhone Duo 512GB Night Sky",
            model = "iPhone Duo",
            storage = "512GB",
            color = "Night Sky",
            colorHex = Color(0xFF0F172A)
        ),
        ProductInfo(
            sku = "MK284VC/A",
            name = "iPhone Duo 1TB Star White",
            model = "iPhone Duo",
            storage = "1TB",
            color = "Star White",
            colorHex = Color(0xFFF8FAFC)
        ),
        ProductInfo(
            sku = "MK294VC/A",
            name = "iPhone Duo 1TB Night Sky",
            model = "iPhone Duo",
            storage = "1TB",
            color = "Night Sky",
            colorHex = Color(0xFF0F172A)
        ),
        ProductInfo(
            sku = "MK2A4VC/A",
            name = "iPhone Duo 2TB Star White",
            model = "iPhone Duo",
            storage = "2TB",
            color = "Star White",
            colorHex = Color(0xFFF8FAFC)
        ),
        ProductInfo(
            sku = "MK2C4VC/A",
            name = "iPhone Duo 2TB Night Sky",
            model = "iPhone Duo",
            storage = "2TB",
            color = "Night Sky",
            colorHex = Color(0xFF0F172A)
        ),

        // ==================== iPhone 18 Pro ====================
        // 256GB
        ProductInfo(
            sku = "MJR64VC/A",
            name = "iPhone 18 Pro 256GB Silver",
            model = "iPhone 18 Pro",
            storage = "256GB",
            color = "Silver",
            colorHex = Color(0xFFE2E8F0)
        ),
        ProductInfo(
            sku = "MJR54VC/A",
            name = "iPhone 18 Pro 256GB Black",
            model = "iPhone 18 Pro",
            storage = "256GB",
            color = "Black",
            colorHex = Color(0xFF1E293B)
        ),
        ProductInfo(
            sku = "MJR74VC/A",
            name = "iPhone 18 Pro 256GB Burgundy",
            model = "iPhone 18 Pro",
            storage = "256GB",
            color = "Burgundy",
            colorHex = Color(0xFF881337)
        ),
        ProductInfo(
            sku = "MJR84VC/A",
            name = "iPhone 18 Pro 256GB Glacier",
            model = "iPhone 18 Pro",
            storage = "256GB",
            color = "Glacier",
            colorHex = Color(0xFF38BDF8)
        ),
        // 512GB
        ProductInfo(
            sku = "MJRC4VC/A",
            name = "iPhone 18 Pro 512GB Silver",
            model = "iPhone 18 Pro",
            storage = "512GB",
            color = "Silver",
            colorHex = Color(0xFFE2E8F0)
        ),
        ProductInfo(
            sku = "MJR94VC/A",
            name = "iPhone 18 Pro 512GB Black",
            model = "iPhone 18 Pro",
            storage = "512GB",
            color = "Black",
            colorHex = Color(0xFF1E293B)
        ),
        ProductInfo(
            sku = "MJRD4VC/A",
            name = "iPhone 18 Pro 512GB Burgundy",
            model = "iPhone 18 Pro",
            storage = "512GB",
            color = "Burgundy",
            colorHex = Color(0xFF881337)
        ),
        ProductInfo(
            sku = "MJRE4VC/A",
            name = "iPhone 18 Pro 512GB Glacier",
            model = "iPhone 18 Pro",
            storage = "512GB",
            color = "Glacier",
            colorHex = Color(0xFF38BDF8)
        ),
        // 1TB
        ProductInfo(
            sku = "MJRG4VC/A",
            name = "iPhone 18 Pro 1TB Silver",
            model = "iPhone 18 Pro",
            storage = "1TB",
            color = "Silver",
            colorHex = Color(0xFFE2E8F0)
        ),
        ProductInfo(
            sku = "MJRF4VC/A",
            name = "iPhone 18 Pro 1TB Black",
            model = "iPhone 18 Pro",
            storage = "1TB",
            color = "Black",
            colorHex = Color(0xFF1E293B)
        ),
        ProductInfo(
            sku = "MJRH4VC/A",
            name = "iPhone 18 Pro 1TB Burgundy",
            model = "iPhone 18 Pro",
            storage = "1TB",
            color = "Burgundy",
            colorHex = Color(0xFF881337)
        ),
        ProductInfo(
            sku = "MJRJ4VC/A",
            name = "iPhone 18 Pro 1TB Glacier",
            model = "iPhone 18 Pro",
            storage = "1TB",
            color = "Glacier",
            colorHex = Color(0xFF38BDF8)
        ),

        // ==================== iPhone 18 Pro Max ====================
        // 256GB
        ProductInfo(
            sku = "MJX64VC/A",
            name = "iPhone 18 Pro Max 256GB Silver",
            model = "iPhone 18 Pro Max",
            storage = "256GB",
            color = "Silver",
            colorHex = Color(0xFFE2E8F0)
        ),
        ProductInfo(
            sku = "MJX54VC/A",
            name = "iPhone 18 Pro Max 256GB Black",
            model = "iPhone 18 Pro Max",
            storage = "256GB",
            color = "Black",
            colorHex = Color(0xFF1E293B)
        ),
        ProductInfo(
            sku = "MJX74VC/A",
            name = "iPhone 18 Pro Max 256GB Burgundy",
            model = "iPhone 18 Pro Max",
            storage = "256GB",
            color = "Burgundy",
            colorHex = Color(0xFF881337)
        ),
        ProductInfo(
            sku = "MJX84VC/A",
            name = "iPhone 18 Pro Max 256GB Glacier",
            model = "iPhone 18 Pro Max",
            storage = "256GB",
            color = "Glacier",
            colorHex = Color(0xFF38BDF8)
        ),
        // 512GB
        ProductInfo(
            sku = "MJXA4VC/A",
            name = "iPhone 18 Pro Max 512GB Silver",
            model = "iPhone 18 Pro Max",
            storage = "512GB",
            color = "Silver",
            colorHex = Color(0xFFE2E8F0)
        ),
        ProductInfo(
            sku = "MJX94VC/A",
            name = "iPhone 18 Pro Max 512GB Black",
            model = "iPhone 18 Pro Max",
            storage = "512GB",
            color = "Black",
            colorHex = Color(0xFF1E293B)
        ),
        ProductInfo(
            sku = "MJXC4VC/A",
            name = "iPhone 18 Pro Max 512GB Burgundy",
            model = "iPhone 18 Pro Max",
            storage = "512GB",
            color = "Burgundy",
            colorHex = Color(0xFF881337)
        ),
        ProductInfo(
            sku = "MJXD4VC/A",
            name = "iPhone 18 Pro Max 512GB Glacier",
            model = "iPhone 18 Pro Max",
            storage = "512GB",
            color = "Glacier",
            colorHex = Color(0xFF38BDF8)
        ),
        // 1TB
        ProductInfo(
            sku = "MJXF4VC/A",
            name = "iPhone 18 Pro Max 1TB Silver",
            model = "iPhone 18 Pro Max",
            storage = "1TB",
            color = "Silver",
            colorHex = Color(0xFFE2E8F0)
        ),
        ProductInfo(
            sku = "MJXE4VC/A",
            name = "iPhone 18 Pro Max 1TB Black",
            model = "iPhone 18 Pro Max",
            storage = "1TB",
            color = "Black",
            colorHex = Color(0xFF1E293B)
        ),
        ProductInfo(
            sku = "MJXG4VC/A",
            name = "iPhone 18 Pro Max 1TB Burgundy",
            model = "iPhone 18 Pro Max",
            storage = "1TB",
            color = "Burgundy",
            colorHex = Color(0xFF881337)
        ),
        ProductInfo(
            sku = "MJXH4VC/A",
            name = "iPhone 18 Pro Max 1TB Glacier",
            model = "iPhone 18 Pro Max",
            storage = "1TB",
            color = "Glacier",
            colorHex = Color(0xFF38BDF8)
        ),

        // ==================== iPhone Air ====================
        ProductInfo(
            sku = "MG194VC/A",
            name = "iPhone Air 256GB Cloud White",
            model = "iPhone Air",
            storage = "256GB",
            color = "Cloud White",
            colorHex = Color(0xFFF8FAFC)
        ),
        ProductInfo(
            sku = "MG184VC/A",
            name = "iPhone Air 256GB Space Black",
            model = "iPhone Air",
            storage = "256GB",
            color = "Space Black",
            colorHex = Color(0xFF1E293B)
        ),
        ProductInfo(
            sku = "MG1A4VC/A",
            name = "iPhone Air 256GB Light Gold",
            model = "iPhone Air",
            storage = "256GB",
            color = "Light Gold",
            colorHex = Color(0xFFFCD34D)
        ),
        ProductInfo(
            sku = "MG1D4VC/A",
            name = "iPhone Air 256GB Sky Blue",
            model = "iPhone Air",
            storage = "256GB",
            color = "Sky Blue",
            colorHex = Color(0xFF7DD3FC)
        ),
        ProductInfo(
            sku = "MG1G4VC/A",
            name = "iPhone Air 512GB Cloud White",
            model = "iPhone Air",
            storage = "512GB",
            color = "Cloud White",
            colorHex = Color(0xFFF8FAFC)
        ),
        ProductInfo(
            sku = "MG1E4VC/A",
            name = "iPhone Air 512GB Space Black",
            model = "iPhone Air",
            storage = "512GB",
            color = "Space Black",
            colorHex = Color(0xFF1E293B)
        ),

        // ==================== iPhone 16 Lineup ====================
        ProductInfo(
            sku = "MYDR3VC/A",
            name = "iPhone 16 128GB White",
            model = "iPhone 16",
            storage = "128GB",
            color = "White",
            colorHex = Color(0xFFF8FAFC)
        ),
        ProductInfo(
            sku = "MYDQ3VC/A",
            name = "iPhone 16 128GB Black",
            model = "iPhone 16",
            storage = "128GB",
            color = "Black",
            colorHex = Color(0xFF1E293B)
        ),
        ProductInfo(
            sku = "MYDT3VC/A",
            name = "iPhone 16 128GB Pink",
            model = "iPhone 16",
            storage = "128GB",
            color = "Pink",
            colorHex = Color(0xFFF472B6)
        ),
        ProductInfo(
            sku = "MYDU3VC/A",
            name = "iPhone 16 128GB Ultramarine",
            model = "iPhone 16",
            storage = "128GB",
            color = "Ultramarine",
            colorHex = Color(0xFF38BDF8)
        ),
        ProductInfo(
            sku = "MYDV3VC/A",
            name = "iPhone 16 128GB Teal",
            model = "iPhone 16",
            storage = "128GB",
            color = "Teal",
            colorHex = Color(0xFF2DD4BF)
        )
    )

    private val skuToInfoMap = CATALOG.associateBy { it.sku }

    fun getProduct(sku: String): ProductInfo? {
        val clean = sku.trim().uppercase()
        return skuToInfoMap[clean]
    }

    fun getProductName(sku: String, fallback: String = ""): String {
        val clean = sku.trim().uppercase()
        return skuToInfoMap[clean]?.name ?: fallback.ifBlank { clean }
    }

    /**
     * Preset Groups for Wide Alerts & One-Tap Batch Tracking
     */
    data class PresetGroup(
        val title: String,
        val subtitle: String,
        val skus: List<String>
    )

    val PRESET_GROUPS = listOf(
        PresetGroup(
            title = "⭐ iPhone Duo 256GB Star White",
            subtitle = "Target: MK244VC/A (Primary Flagship)",
            skus = listOf("MK244VC/A")
        ),
        PresetGroup(
            title = "📱 Any iPhone Duo (All Storages & Colors)",
            subtitle = "Monitors 256GB, 512GB & 1TB in Star White & Night Sky",
            skus = listOf("MK244VC/A", "MK254VC/A", "MK264VC/A", "MK274VC/A", "MK284VC/A", "MK294VC/A")
        ),
        PresetGroup(
            title = "🤍 Any White / Silver Flagship",
            subtitle = "Duo Star White, 18 Pro Silver, 18 Pro Max Silver, Air Cloud White",
            skus = listOf("MK244VC/A", "MJR64VC/A", "MJX64VC/A", "MG194VC/A")
        ),
        PresetGroup(
            title = "🔥 iPhone 18 Pro 256GB (All 4 Colors)",
            subtitle = "Monitors Silver, Black, Burgundy & Glacier",
            skus = listOf("MJR64VC/A", "MJR54VC/A", "MJR74VC/A", "MJR84VC/A")
        ),
        PresetGroup(
            title = "👑 iPhone 18 Pro Max 256GB (All 4 Colors)",
            subtitle = "Monitors Silver, Black, Burgundy & Glacier",
            skus = listOf("MJX64VC/A", "MJX54VC/A", "MJX74VC/A", "MJX84VC/A")
        ),
        PresetGroup(
            title = "💎 iPhone 18 Series Glacier Blue Tier",
            subtitle = "Monitors Glacier color in 18 Pro & 18 Pro Max",
            skus = listOf("MJR84VC/A", "MJX84VC/A", "MJRE4VC/A", "MJXD4VC/A")
        ),
        PresetGroup(
            title = "🕊️ iPhone Air 256GB (All Colors)",
            subtitle = "Monitors Cloud White, Space Black, Light Gold & Sky Blue",
            skus = listOf("MG194VC/A", "MG184VC/A", "MG1A4VC/A", "MG1D4VC/A")
        )
    )
}
