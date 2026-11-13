package ch.paynash.pos.settings;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller or handler as available only in one installation mode (spec venue-mode, "Functions per mode";
 * design D2). A request in the other mode is answered with 409 and the code {@code wrong_mode}.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresMode {

	Mode value();

}
