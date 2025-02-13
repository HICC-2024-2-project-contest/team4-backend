package com.team4.giftidea.configuration;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

	@Bean
	public OpenAPI giftIdeaOpenAPI() {
		return new OpenAPI()
			.info(new Info()
				.title("🎁 GiftIdea API 문서")
				.description("GPT 기반 선물 추천 API")
				.version("1.0.0")
				.contact(new Contact()
					.name("팀4")
					.email("team4@giftidea.com")
					.url("https://presentalk.store"))
				.license(new License()
					.name("Apache 2.0")
					.url("https://www.apache.org/licenses/LICENSE-2.0")))
			.externalDocs(new ExternalDocumentation()
				.description("GitHub Repository")
				.url("https://github.com/team4/giftidea"))
			.servers(List.of(
				new Server().url("https://app.presentalk.store").description("🚀 배포 환경"),
				new Server().url("http://localhost:8080").description("🛠️ 로컬 개발 환경")
			));
	}
}