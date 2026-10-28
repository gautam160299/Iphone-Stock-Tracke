package com.example.applestockpulse

import androidx.compose.ui.graphics.Color

data class ProductInfo(
    val sku: String,
    val name: String,
    val model: String,
    val storage: String,
    val color: String,
    val colorHex: Color = Color.Gray
)

data class DeviceModel(
    val name: String,
    val subtitle: String,
    val storages: List<String>,
    val colors: List<String>
)

object ProductCatalog {

    private val MODEL_SUBTITLES: List<Pair<String, String>> = listOf(
        "iPhone Duo" to "Foldable Titanium Architecture",
        "iPhone 18 Pro" to "Variable Aperture Pro Camera (A20 Pro)",
        "iPhone 18 Pro Max" to "6.9\" Super Retina Pro Display",
        "iPhone Air" to "Ultrathin Titanium Profile",
        "iPhone 16" to "Standard Lineup"
    )

    val CATALOG: List<ProductInfo> = listOf(
        // ==================== iPhone Duo ====================
        ProductInfo("MK244VC/A", "iPhone Duo 256GB Star White", "iPhone Duo", "256GB", "Star White", Color(0xFFF8FAFC)),
        ProductInfo("MK254VC/A", "iPhone Duo 256GB Night Sky", "iPhone Duo", "256GB", "Night Sky", Color(0xFF0F172A)),
        ProductInfo("MK264VC/A", "iPhone Duo 512GB Star White", "iPhone Duo", "512GB", "Star White", Color(0xFFF8FAFC)),
        ProductInfo("MK274VC/A", "iPhone Duo 512GB Night Sky", "iPhone Duo", "512GB", "Night Sky", Color(0xFF0F172A)),
        ProductInfo("MK284VC/A", "iPhone Duo 1TB Star White", "iPhone Duo", "1TB", "Star White", Color(0xFFF8FAFC)),
        ProductInfo("MK294VC/A", "iPhone Duo 1TB Night Sky", "iPhone Duo", "1TB", "Night Sky", Color(0xFF0F172A)),
        ProductInfo("MK2A4VC/A", "iPhone Duo 2TB Star White", "iPhone Duo", "2TB", "Star White", Color(0xFFF8FAFC)),
        ProductInfo("MK2C4VC/A", "iPhone Duo 2TB Night Sky", "iPhone Duo", "2TB", "Night Sky", Color(0xFF0F172A)),

        // ==================== iPhone 18 Pro ====================
        // 256GB
        ProductInfo("MJR64VC/A", "iPhone 18 Pro 256GB Silver", "iPhone 18 Pro", "256GB", "Silver", Color(0xFFE2E8F0)),
        ProductInfo("MJR54VC/A", "iPhone 18 Pro 256GB Black", "iPhone 18 Pro", "256GB", "Black", Color(0xFF1E293B)),
        ProductInfo("MJR74VC/A", "iPhone 18 Pro 256GB Burgundy", "iPhone 18 Pro", "256GB", "Burgundy", Color(0xFF881337)),
        ProductInfo("MJR84VC/A", "iPhone 18 Pro 256GB Glacier", "iPhone 18 Pro", "256GB", "Glacier", Color(0xFF38BDF8)),
        // 512GB
        ProductInfo("MJRC4VC/A", "iPhone 18 Pro 512GB Silver", "iPhone 18 Pro", "512GB", "Silver", Color(0xFFE2E8F0)),
        ProductInfo("MJR94VC/A", "iPhone 18 Pro 512GB Black", "iPhone 18 Pro", "512GB", "Black", Color(0xFF1E293B)),
        ProductInfo("MJRD4VC/A", "iPhone 18 Pro 512GB Burgundy", "iPhone 18 Pro", "512GB", "Burgundy", Color(0xFF881337)),
        ProductInfo("MJRE4VC/A", "iPhone 18 Pro 512GB Glacier", "iPhone 18 Pro", "512GB", "Glacier", Color(0xFF38BDF8)),
        // 1TB
        ProductInfo("MJRG4VC/A", "iPhone 18 Pro 1TB Silver", "iPhone 18 Pro", "1TB", "Silver", Color(0xFFE2E8F0)),
        ProductInfo("MJRF4VC/A", "iPhone 18 Pro 1TB Black", "iPhone 18 Pro", "1TB", "Black", Color(0xFF1E293B)),
        ProductInfo("MJRH4VC/A", "iPhone 18 Pro 1TB Burgundy", "iPhone 18 Pro", "1TB", "Burgundy", Color(0xFF881337)),
        ProductInfo("MJRJ4VC/A", "iPhone 18 Pro 1TB Glacier", "iPhone 18 Pro", "1TB", "Glacier", Color(0xFF38BDF8)),
        // 2TB
        ProductInfo("MJRL4VC/A", "iPhone 18 Pro 2TB Silver", "iPhone 18 Pro", "2TB", "Silver", Color(0xFFE2E8F0)),
        ProductInfo("MJRK4VC/A", "iPhone 18 Pro 2TB Black", "iPhone 18 Pro", "2TB", "Black", Color(0xFF1E293B)),
        ProductInfo("MJRM4VC/A", "iPhone 18 Pro 2TB Burgundy", "iPhone 18 Pro", "2TB", "Burgundy", Color(0xFF881337)),
        ProductInfo("MJRN4VC/A", "iPhone 18 Pro 2TB Glacier", "iPhone 18 Pro", "2TB", "Glacier", Color(0xFF38BDF8)),

        // ==================== iPhone 18 Pro Max ====================
        // 256GB
        ProductInfo("MJX64VC/A", "iPhone 18 Pro Max 256GB Silver", "iPhone 18 Pro Max", "256GB", "Silver", Color(0xFFE2E8F0)),
        ProductInfo("MJX54VC/A", "iPhone 18 Pro Max 256GB Black", "iPhone 18 Pro Max", "256GB", "Black", Color(0xFF1E293B)),
        ProductInfo("MJX74VC/A", "iPhone 18 Pro Max 256GB Burgundy", "iPhone 18 Pro Max", "256GB", "Burgundy", Color(0xFF881337)),
        ProductInfo("MJX84VC/A", "iPhone 18 Pro Max 256GB Glacier", "iPhone 18 Pro Max", "256GB", "Glacier", Color(0xFF38BDF8)),
        // 512GB
        ProductInfo("MJXA4VC/A", "iPhone 18 Pro Max 512GB Silver", "iPhone 18 Pro Max", "512GB", "Silver", Color(0xFFE2E8F0)),
        ProductInfo("MJX94VC/A", "iPhone 18 Pro Max 512GB Black", "iPhone 18 Pro Max", "512GB", "Black", Color(0xFF1E293B)),
        ProductInfo("MJXC4VC/A", "iPhone 18 Pro Max 512GB Burgundy", "iPhone 18 Pro Max", "512GB", "Burgundy", Color(0xFF881337)),
        ProductInfo("MJXD4VC/A", "iPhone 18 Pro Max 512GB Glacier", "iPhone 18 Pro Max", "512GB", "Glacier", Color(0xFF38BDF8)),
        // 1TB
        ProductInfo("MJXF4VC/A", "iPhone 18 Pro Max 1TB Silver", "iPhone 18 Pro Max", "1TB", "Silver", Color(0xFFE2E8F0)),
        ProductInfo("MJXE4VC/A", "iPhone 18 Pro Max 1TB Black", "iPhone 18 Pro Max", "1TB", "Black", Color(0xFF1E293B)),
        ProductInfo("MJXG4VC/A", "iPhone 18 Pro Max 1TB Burgundy", "iPhone 18 Pro Max", "1TB", "Burgundy", Color(0xFF881337)),
        ProductInfo("MJXH4VC/A", "iPhone 18 Pro Max 1TB Glacier", "iPhone 18 Pro Max", "1TB", "Glacier", Color(0xFF38BDF8)),
        // 2TB
        ProductInfo("MJXK4VC/A", "iPhone 18 Pro Max 2TB Silver", "iPhone 18 Pro Max", "2TB", "Silver", Color(0xFFE2E8F0)),
        ProductInfo("MJXJ4VC/A", "iPhone 18 Pro Max 2TB Black", "iPhone 18 Pro Max", "2TB", "Black", Color(0xFF1E293B)),
        ProductInfo("MJXL4VC/A", "iPhone 18 Pro Max 2TB Burgundy", "iPhone 18 Pro Max", "2TB", "Burgundy", Color(0xFF881337)),
        ProductInfo("MJXM4VC/A", "iPhone 18 Pro Max 2TB Glacier", "iPhone 18 Pro Max", "2TB", "Glacier", Color(0xFF38BDF8)),

        // ==================== iPhone Air ====================
        ProductInfo("MG194VC/A", "iPhone Air 256GB Cloud White", "iPhone Air", "256GB", "Cloud White", Color(0xFFF8FAFC)),
        ProductInfo("MG184VC/A", "iPhone Air 256GB Space Black", "iPhone Air", "256GB", "Space Black", Color(0xFF1E293B)),
        ProductInfo("MG1A4VC/A", "iPhone Air 256GB Light Gold", "iPhone Air", "256GB", "Light Gold", Color(0xFFFCD34D)),
        ProductInfo("MG1D4VC/A", "iPhone Air 256GB Sky Blue", "iPhone Air", "256GB", "Sky Blue", Color(0xFF7DD3FC)),
        ProductInfo("MG1G4VC/A", "iPhone Air 512GB Cloud White", "iPhone Air", "512GB", "Cloud White", Color(0xFFF8FAFC)),
        ProductInfo("MG1E4VC/A", "iPhone Air 512GB Space Black", "iPhone Air", "512GB", "Space Black", Color(0xFF1E293B)),
        ProductInfo("MG1H4VC/A", "iPhone Air 512GB Light Gold", "iPhone Air", "512GB", "Light Gold", Color(0xFFFCD34D)),
        ProductInfo("MG1J4VC/A", "iPhone Air 512GB Sky Blue", "iPhone Air", "512GB", "Sky Blue", Color(0xFF7DD3FC)),
        ProductInfo("MG1N4VC/A", "iPhone Air 1TB Cloud White", "iPhone Air", "1TB", "Cloud White", Color(0xFFF8FAFC)),
        ProductInfo("MG1L4VC/A", "iPhone Air 1TB Space Black", "iPhone Air", "1TB", "Space Black", Color(0xFF1E293B)),

        // ==================== iPhone 16 ====================
        ProductInfo("MYDR3VC/A", "iPhone 16 128GB White", "iPhone 16", "128GB", "White", Color(0xFFF8FAFC)),
        ProductInfo("MYDQ3VC/A", "iPhone 16 128GB Black", "iPhone 16", "128GB", "Black", Color(0xFF1E293B)),
        ProductInfo("MYDT3VC/A", "iPhone 16 128GB Pink", "iPhone 16", "128GB", "Pink", Color(0xFFF472B6)),
        ProductInfo("MYDU3VC/A", "iPhone 16 128GB Ultramarine", "iPhone 16", "128GB", "Ultramarine", Color(0xFF38BDF8)),
        ProductInfo("MYDV3VC/A", "iPhone 16 128GB Teal", "iPhone 16", "128GB", "Teal", Color(0xFF2DD4BF)),
        ProductInfo("MYDX3VC/A", "iPhone 16 256GB White", "iPhone 16", "256GB", "White", Color(0xFFF8FAFC)),
        ProductInfo("MYDW3VC/A", "iPhone 16 256GB Black", "iPhone 16", "256GB", "Black", Color(0xFF1E293B)),
        ProductInfo("MYDY3VC/A", "iPhone 16 256GB Pink", "iPhone 16", "256GB", "Pink", Color(0xFFF472B6)),
        ProductInfo("MYE03VC/A", "iPhone 16 256GB Ultramarine", "iPhone 16", "256GB", "Ultramarine", Color(0xFF38BDF8)),
        ProductInfo("MYE13VC/A", "iPhone 16 256GB Teal", "iPhone 16", "256GB", "Teal", Color(0xFF2DD4BF))
    )

    private val skuMap = CATALOG.associateBy { it.sku.uppercase() }

    // Storages and colors come from CATALOG so the UI never offers a combination without a SKU
    val DEVICE_MODELS: List<DeviceModel> = MODEL_SUBTITLES.map { (name, subtitle) ->
        val items = CATALOG.filter { it.model == name }
        DeviceModel(
            name = name,
            subtitle = subtitle,
            storages = items.map { it.storage }.distinct().sortedBy { storageInGigabytes(it) },
            colors = items.map { it.color }.distinct()
        )
    }

    fun storageInGigabytes(storage: String): Int {
        val number = storage.filter { it.isDigit() }.toIntOrNull() ?: 0
        return if (storage.endsWith("TB", ignoreCase = true)) number * 1024 else number
    }

    /**
     * Apple product page for a part number, e.g. https://www.apple.com/ca/shop/product/MYDR3VC/A
     */
    fun productPageUrl(sku: String): String {
        return "https://www.apple.com/ca/shop/product/${sku.trim().uppercase()}"
    }

    fun getProduct(sku: String): ProductInfo? {
        return skuMap[sku.trim().uppercase()]
    }

    fun getProductName(sku: String, fallback: String = ""): String {
        return skuMap[sku.trim().uppercase()]?.name ?: fallback.ifBlank { sku.trim().uppercase() }
    }

    fun getDeviceModel(name: String): DeviceModel {
        return DEVICE_MODELS.find { it.name.equals(name, ignoreCase = true) } ?: DEVICE_MODELS[0]
    }

    fun getSku(model: String, storage: String, color: String): String? {
        return CATALOG.find {
            it.model.equals(model, ignoreCase = true) &&
            it.storage.equals(storage, ignoreCase = true) &&
            it.color.equals(color, ignoreCase = true)
        }?.sku
    }

    fun getSkusForSelection(model: String, storageFilter: String, colorFilter: String): List<String> {
        // No fallback: a filter combination without a SKU must not silently track other variants
        return CATALOG.filter { item ->
            item.model.equals(model, ignoreCase = true) &&
            (storageFilter == "All" || item.storage.equals(storageFilter, ignoreCase = true)) &&
            (colorFilter == "All" || item.color.equals(colorFilter, ignoreCase = true))
        }.map { it.sku }
    }
}
