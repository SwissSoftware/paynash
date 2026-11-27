package ch.paynash.pos.settings;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class FeatureGateConfig implements WebMvcConfigurer {

	private final FeatureGate gate;

	FeatureGateConfig(FeatureGate gate) {
		this.gate = gate;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(gate);
	}

}
