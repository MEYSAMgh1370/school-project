package com.example.roshdeandishe.platform;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * فقط metadata نمایشی برای Swagger UI (عنوان، توضیح، نسخه) - هیچ دانشی از
 * ماژول‌های دامنه ندارد، به همین دلیل در platform قرار دارد، نه iam.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI roshdeandisheOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Roshdeandishe API")
                .description("سیستم مدیریت مدرسه - چندمستأجری، ماژولار")
                .version("v0.1")
        );
    }
}
