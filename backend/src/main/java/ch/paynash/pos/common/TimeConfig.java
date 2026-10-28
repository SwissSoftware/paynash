package ch.paynash.pos.common;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

	/** Injected wherever "now" matters, so tests can move time forward. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
