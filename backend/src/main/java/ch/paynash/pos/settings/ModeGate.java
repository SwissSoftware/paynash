package ch.paynash.pos.settings;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import ch.paynash.pos.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Enforces {@link RequiresMode} on the server, not only in the apps (design D2). */
@Component
public class ModeGate implements HandlerInterceptor {

	private final SettingsService settings;

	public ModeGate(SettingsService settings) {
		this.settings = settings;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (handler instanceof HandlerMethod method) {
			RequiresMode required = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequiresMode.class);
			if (required == null) {
				required = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequiresMode.class);
			}
			if (required != null && settings.mode() != required.value()) {
				throw wrongMode(required.value());
			}
		}
		return true;
	}

	public static ApiException wrongMode(Mode required) {
		String what = required == Mode.SALON ? "salon" : "event";
		return new ApiException(HttpStatus.CONFLICT, "This function is only available in " + what + " mode")
			.with("code", "wrong_mode");
	}

}
