package com.blikeng.chatapp

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ActiveProfiles

@EnableJpaRepositories
@SpringBootTest
@ActiveProfiles("test")
class ChatappApplicationTests {

	@Test
	fun contextLoads() {
	}

}
