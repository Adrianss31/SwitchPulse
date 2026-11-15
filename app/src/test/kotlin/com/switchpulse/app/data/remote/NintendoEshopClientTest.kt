package com.switchpulse.app.data.remote

import com.switchpulse.app.data.model.Platform
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

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
    fun `fetchPopularDeals discovers recent hits like BALL x PIT and ranks Steam-verified deals above shovelware`() = runTest {
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
                    "dates_released_dts": ["2026-06-03T00:00:00Z"],
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
                    "dates_released_dts": ["2024-07-26T00:00:00Z"],
                    "nsuid_txt": ["70010000060000"],
                    "price_regular_f": 59.99,
                    "price_discounted_f": 11.99,
                    "price_discount_percentage_f": 80
                  },
                  {
                    "fs_id": "9999001",
                    "title": "POLICE OFFICERS SIMULATOR",
                    "publisher": "Shovelware Studio",
                    "system_type": ["nintendoswitch_downloadsoftware"],
                    "url": "/it-it/Giochi/POLICE-9999001.html",
                    "dates_released_dts": ["2025-11-25T00:00:00Z"],
                    "nsuid_txt": ["70010000999001"],
                    "price_regular_f": 12.99,
                    "price_discounted_f": 2.99,
                    "price_discount_percentage_f": 77
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
                    "dates_released_dts": ["2026-06-03T00:00:00Z"],
                    "nsuid_txt": ["70010000112478"],
                    "price_regular_f": 79.99,
                    "price_discounted_f": 11.99,
                    "price_discount_percentage_f": 85
                  },
                  {
                    "fs_id": "2949433",
                    "title": "BALL x PIT – Nintendo Switch™ 2 Edition",
                    "publisher": "Devolver Digital",
                    "system_type": ["nintendoswitch2,nintendoswitch2"],
                    "url": "/it-it/Giochi/BALL-x-PIT-Switch-2-2949433.html",
                    "dates_released_dts": ["2025-10-28T00:00:00Z"],
                    "nsuid_txt": ["70010000109095"],
                    "price_regular_f": 0.0,
                    "price_discounted_f": 7.49,
                    "price_discount_percentage_f": 50
                  }
                ]
              }
            }
        """.trimIndent()

        val chartsJson = """
            [
              { "fs_id": "2618635", "price_has_discount_b": true }
            ]
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
                    "start_datetime": "2026-11-02T23:00:00Z",
                    "end_datetime": "2026-11-16T22:59:59Z"
                  }
                },
                {
                  "title_id": 70010000109095,
                  "regular_price": { "raw_value": "14.99" },
                  "discount_price": {
                    "raw_value": "7.49",
                    "start_datetime": "2026-11-12T23:00:00Z",
                    "end_datetime": "2026-12-02T22:59:59Z"
                  }
                },
                {
                  "title_id": 70010000999001,
                  "regular_price": { "raw_value": "12.99" },
                  "discount_price": {
                    "raw_value": "2.99",
                    "start_datetime": "2026-11-14T23:00:00Z",
                    "end_datetime": "2026-11-28T22:59:59Z"
                  }
                }
              ]
            }
        """.trimIndent()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(404)
                val path = url.encodedPath
                val fq = url.queryParameter("fq").orEmpty()
                return when {
                    path.endsWith("/it/select") && fq.contains("dates_released_dts") ->
                        MockResponse().setResponseCode(200).setBody(sw2SearchJson)
                    path.endsWith("/it/select") && fq == "type:GAME AND price_has_discount_b:true AND system_type:*nintendoswitch2*" ->
                        MockResponse().setResponseCode(200).setBody(sw2SearchJson)
                    path.endsWith("/it/select") ->
                        MockResponse().setResponseCode(200).setBody(generalSearchJson)
                    path.endsWith("/charts") ->
                        MockResponse().setResponseCode(200).setBody(chartsJson)
                    path.endsWith("/v1/price") ->
                        MockResponse().setResponseCode(200).setBody(priceApiJson)
                    path.endsWith("/api/storesearch/") -> {
                        val term = url.queryParameter("term").orEmpty()
                        val body = when {
                            term.contains("ball x pit") ->
                                """{"items":[{"id":2062430,"name":"BALL x PIT"}]}"""
                            term.contains("final fantasy vii rebirth") ->
                                """{"items":[{"id":2909400,"name":"FINAL FANTASY VII REBIRTH"}]}"""
                            term.contains("police officers simulator") ->
                                """{"items":[{"id":997010,"name":"Police Simulator: Patrol Officers"}]}"""
                            else -> """{"items":[]}"""
                        }
                        MockResponse().setResponseCode(200).setBody(body)
                    }
                    path.endsWith("/IStoreBrowseService/GetItems/v1/") -> {
                        val body = """
                            {
                              "response": {
                                "store_items": [
                                  {
                                    "appid": 2062430,
                                    "basic_info": {
                                      "short_description": "Brick-breaking roguelite survival arena",
                                      "developers": [{ "name": "Kenny Sun" }]
                                    },
                                    "tagids": [3959, 1716, 4885],
                                    "reviews": {
                                      "summary_filtered": {
                                        "review_count": 25548,
                                        "percent_positive": 95
                                      }
                                    }
                                  },
                                  {
                                    "appid": 2909400,
                                    "basic_info": {
                                      "short_description": "Action JRPG epic sequel",
                                      "developers": [{ "name": "Square Enix" }]
                                    },
                                    "tagids": [122, 4434],
                                    "reviews": {
                                      "summary_filtered": {
                                        "review_count": 27056,
                                        "percent_positive": 91
                                      }
                                    }
                                  }
                                ]
                              }
                            }
                        """.trimIndent()
                        MockResponse().setResponseCode(200).setBody(body)
                    }
                    path.endsWith("/IStoreService/GetTagList/v1/") -> {
                        val body = """
                            {
                              "response": {
                                "tags": [
                                  { "tagid": 3959, "name": "Roguelite" },
                                  { "tagid": 1716, "name": "Roguelike" },
                                  { "tagid": 4885, "name": "Inferno di proiettili" },
                                  { "tagid": 122, "name": "GDR" },
                                  { "tagid": 4434, "name": "JRPG" }
                                ]
                              }
                            }
                        """.trimIndent()
                        MockResponse().setResponseCode(200).setBody(body)
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val fixedClock = Clock.fixed(Instant.parse("2026-11-15T18:00:00Z"), ZoneOffset.UTC)
        val client = NintendoEshopClient(
            httpClient = OkHttpClient(),
            searchBaseUrl = server.url("/it/select").toString(),
            priceBaseUrl = server.url("/v1/price").toString(),
            chartsUrl = server.url("/charts").toString(),
            steamSearchUrl = server.url("/api/storesearch/").toString(),
            steamBrowseItemsUrl = server.url("/IStoreBrowseService/GetItems/v1/").toString(),
            steamTagListUrl = server.url("/IStoreService/GetTagList/v1/").toString(),
            clock = fixedClock
        )

        val deals = client.fetchPopularDeals()
        assertEquals(4, deals.size)

        val byId = deals.associateBy { it.id }

        val ff7 = byId.getValue("3016562")
        assertEquals(Platform.SWITCH_2, ff7.platform)
        assertEquals(59.99, ff7.regularPriceEur, 0.001)
        assertEquals(8.99, ff7.discountedPriceEur, 0.001)
        assertEquals(85, ff7.discountPercent)
        assertEquals(27056, ff7.steamReviewCount)
        assertEquals(91, ff7.steamPositivePercent)
        assertEquals("Square Enix", ff7.developer)
        assertEquals(listOf("GDR", "JRPG"), ff7.steamTagNames)
        assertEquals("2026-11-16T22:59:59Z", ff7.endsAtIso)
        assertEquals("https://www.nintendo.com/it-it/Giochi/FINAL-FANTASY-VII-REBIRTH-3016562.html", ff7.eshopUrl)

        val ballXPit = byId.getValue("2949433")
        assertEquals(14.99, ballXPit.regularPriceEur, 0.001)
        assertEquals(7.49, ballXPit.discountedPriceEur, 0.001)
        assertEquals(50, ballXPit.discountPercent)
        assertEquals(25548, ballXPit.steamReviewCount)
        assertEquals(95, ballXPit.steamPositivePercent)
        assertEquals("Kenny Sun", ballXPit.developer)
        assertEquals(listOf("Roguelite", "Roguelike", "Inferno di proiettili"), ballXPit.steamTagNames)
        assertEquals("2026-11-12T23:00:00Z", ballXPit.startsAtIso)

        val onePiece = byId.getValue("2618635")
        assertEquals(Platform.SWITCH_1, onePiece.platform)
        assertEquals(1, onePiece.chartRank)
        assertEquals(11.99, onePiece.discountedPriceEur, 0.001)

        val police = byId.getValue("9999001")
        // Non-matching Steam title ("Police Simulator: Patrol Officers") must NOT pollute review count
        assertEquals(0, police.steamReviewCount)
        assertTrue(
            "Expected BALL x PIT (${ballXPit.hotScore}) to outrank shovelware (${police.hotScore})",
            ballXPit.hotScore > police.hotScore
        )
        assertTrue(
            "Expected FF7 Rebirth (${ff7.hotScore}) to outrank shovelware (${police.hotScore})",
            ff7.hotScore > police.hotScore
        )
    }
}
