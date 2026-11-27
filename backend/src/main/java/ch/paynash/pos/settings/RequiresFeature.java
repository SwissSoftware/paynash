package ch.paynash.pos.settings;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller or handler as available only while a feature is on (spec business-features, "Functions follow
 * the features"; design D2). Other requests are answered with 409 and the code {@code feature_disabled}.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresFeature {

	Feature value();

}
