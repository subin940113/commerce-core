package com.example.commerce

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class CommerceApplicationTests {

    @Test
    fun `애플리케이션 컨텍스트가 로드된다`() {
    }

}
