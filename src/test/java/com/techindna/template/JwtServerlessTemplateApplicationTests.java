package com.techindna.template;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class JwtServerlessTemplateApplicationTests extends TestcontainersSupport {

    @Test
    void contextLoads() {
    }

}
