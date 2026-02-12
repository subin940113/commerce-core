package com.example.commerce.order

import com.example.commerce.TestcontainersConfiguration
import com.example.commerce.catalog.InventoryRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class OrderApiTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @BeforeEach
    fun setUp() {
        inventoryRepository.findById(1L).ifPresent { inv ->
            inv.availableQty = 100
            inv.reservedQty = 0
            inventoryRepository.saveAndFlush(inv)
        }
    }

    @Test
    fun `재고가 충분하면 201을 반환한다`() {
        mockMvc.perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":1,"items":[{"productId":1,"qty":2},{"productId":2,"qty":1}]}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.orderId").isNumber)
            .andExpect(jsonPath("$.status").value("CREATED"))
            .andExpect(jsonPath("$.payableAmount").value(35000))
    }

    @Test
    fun `재고가 부족하면 409를 반환한다`() {
        mockMvc.perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":1,"items":[{"productId":1,"qty":99999}]}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"))
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `검증에 실패하면 400을 반환한다`() {
        mockMvc.perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":1,"items":[{"productId":1,"qty":0}]}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
    }

    @Test
    fun `상품이 없으면 404를 반환한다`() {
        mockMvc.perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":1,"items":[{"productId":99999,"qty":1}]}"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
    }

    @Test
    fun `동일 상품이 여러 번 들어오면 수량을 합산하여 주문한다`() {
        mockMvc.perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":1,"items":[{"productId":1,"qty":1},{"productId":1,"qty":2}]}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.orderId").isNumber)
            .andExpect(jsonPath("$.status").value("CREATED"))
            .andExpect(jsonPath("$.payableAmount").value(30000))

        val inventory = inventoryRepository.findById(1L).orElseThrow()
        assertThat(inventory.reservedQty).isEqualTo(3)
    }

    @Test
    fun `items가 비어있으면 400을 반환한다`() {
        mockMvc.perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":1,"items":[]}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
    }

    @Test
    fun `productId가 0 이하면 400을 반환한다`() {
        mockMvc.perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":1,"items":[{"productId":0,"qty":1}]}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
    }
}
