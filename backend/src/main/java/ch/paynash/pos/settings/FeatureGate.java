package ch.paynash.pos.settings;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import ch.paynash.pos.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Enforces {@link RequiresFeature} on the server, not only in the apps (change configurable-business-features, design
 * D2).
 */
@Component
public class FeatureGate implements HandlerInterceptor {

	private final SettingsService settings;

	public FeatureGate(SettingsService settings) {
		this.settings = settings;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (handler instanceof HandlerMethod method) {
			RequiresFeature required = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(),
					RequiresFeature.class);
			if (required == null) {
				required = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequiresFeature.class);
			}
			if (required != null) {
				settings.require(required.value());
			}
		}
		return true;
	}

	/** 409 with {@code code: feature_disabled} and the feature's key (spec business-features). */
	public static ApiException disabled(Feature feature) {
		return new ApiException(HttpStatus.CONFLICT, feature.offMessage()).with("code", "feature_disabled")
			.with("feature", feature.key());
	}

}
