package com.example.applestockpulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductCatalogTest {

    @Test
    fun `every SKU is unique`() {
        val skus = ProductCatalog.CATALOG.map { it.sku }
        assertEquals(skus.size, skus.toSet().size)
    }

    @Test
    fun `every offered storage and color has at least one SKU`() {
        for (model in ProductCatalog.DEVICE_MODELS) {
            for (storage in model.storages) {
                assertTrue("${model.name} $storage", ProductCatalog.getSkusForSelection(model.name, storage, "All").isNotEmpty())
            }
            for (color in model.colors) {
                assertTrue("${model.name} $color", ProductCatalog.getSkusForSelection(model.name, "All", color).isNotEmpty())
            }
        }
    }

    @Test
    fun `combination without a SKU does not fall back to other variants`() {
        // The catalog has no 1TB Light Gold iPhone Air
        assertTrue(ProductCatalog.getSkusForSelection("iPhone Air", "1TB", "Light Gold").isEmpty())
    }

    @Test
    fun `storages are sorted by size`() {
        val duo = ProductCatalog.getDeviceModel("iPhone Duo")
        assertEquals(listOf("256GB", "512GB", "1TB", "2TB"), duo.storages)
    }

    @Test
    fun `exact selection gives exactly one SKU`() {
        assertEquals(listOf("MK244VC/A"), ProductCatalog.getSkusForSelection("iPhone Duo", "256GB", "Star White"))
        assertNotNull(ProductCatalog.getSku("iPhone 16", "128GB", "Teal"))
    }

    @Test
    fun `product page url uses the part number`() {
        assertEquals("https://www.apple.com/ca/shop/product/MYDR3VC/A", ProductCatalog.productPageUrl(" mydr3vc/a "))
    }
}
