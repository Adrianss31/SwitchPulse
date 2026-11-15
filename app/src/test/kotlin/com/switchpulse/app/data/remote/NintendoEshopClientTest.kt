package com.switchpulse.app.data.remote

import com.switchpulse.app.data.model.Platform
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NintendoEshopClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchPopularDeals merges search results with live EUR pricing and Switch 2 platform`() = runTest {
        val generalSearchJson = """
            {
              "response": {
                "docs": [
                  {
                    "fs_id": "3016562",
                    "title": "FINAL FANTASY VII REBIRTH",
                    "publisher": "SQUARE ENIX",
                    "system_type": ["nintendoswitch2,nintendoswitch2"],
                    "url": "/it-it/Giochi/FINAL-FANTASY-VII-REBIRTH-3016562.html",
                    "image_url_h16x9_s": "https://www.nintendo.com/eu/media/ff7.jpg",
                    "nsuid_txt": ["70010000112478", "70070000032076"],
                    "price_regular_f": 79.99,
                    "price_discounted_f": 11.99,
                    "price_discount_percentage_f": 85
                  },
                  {
                    "fs_id": "2618635",
                    "title": "ONE PIECE ODYSSEY",
                    "publisher": "BANDAI NAMCO",
                    "system_type": ["nintendoswitch_digitaldistribution"],
                    "url": "/it-it/Giochi/ONE-PIECE-2618635.html",
                    "nsuid_txt": ["70010000060000"],
                    "price_regular_f": 59.99,
                    "price_discounted_f": 11.99,
                    "price_discount_percentage_f": 80
                  }
                ]
              }
            }
        """.trimIndent()

        val sw2SearchJson = """
            {
              "response": {
                "docs": [
                  {
                    "fs_id": "3016562",
                    "title": "FINAL FANTASY VII REBIRTH",
                    "publisher": "SQUARE ENIX",
                    "system_type": ["nintendoswitch2,nintendoswitch2"],
                    "url": "/it-it/Giochi/FINAL-FANTASY-VII-REBIRTH-3016562.html",
                    "nsuid_txt": ["70010000112478"],
                    "price_regular_f": 79.99,
                    "price_discounted_f": 11.99,
                    "price_discount_percentage_f": 85
                  }
                ]
              }
            }
        """.trimIndent()

        val priceApiJson = """
            {
              "personalized": false,
              "country": "IT",
              "prices": [
                {
                  "title_id": 70010000112478,
                  "regular_price": { "raw_value": "59.99" },
                  "discount_price": {
                    "raw_value": "8.99",
                    "end_datetime": "2026-11-16T22:59:59Z"
                  }
                }
              ]
            }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(generalSearchJson))
        server.enqueue(MockResponse().setResponseCode(200).setBody(sw2SearchJson))
        server.enqueue(MockResponse().setResponseCode(200).setBody(priceApiJson))

        val client = NintendoEshopClient(
            httpClient = OkHttpClient(),
            searchBaseUrl = server.url("/it/select").toString(),
            priceBaseUrl = server.url("/v1/price").toString()
        )

        val deals = client.fetchPopularDeals()
        assertEquals(2, deals.size)

        val ff7 = deals[0]
        assertEquals("3016562", ff7.id)
        assertEquals(Platform.SWITCH_2, ff7.platform)
        // Live price API overrides stale Solr pricing
        assertEquals(59.99, ff7.regularPriceEur, 0.001)
        assertEquals(8.99, ff7.discountedPriceEur, 0.001)
        assertEquals(85, ff7.discountPercent)
        assertEquals("2026-11-16T22:59:59Z", ff7.endsAtIso)
        assertEquals("https://www.nintendo.com/it-it/Giochi/FINAL-FANTASY-VII-REBIRTH-3016562.html", ff7.eshopUrl)

        val op = deals[1]
        assertEquals(Platform.SWITCH_1, op.platform)
        assertEquals(11.99, op.discountedPriceEur, 0.001)
    }
}
