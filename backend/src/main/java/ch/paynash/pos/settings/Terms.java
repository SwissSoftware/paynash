package ch.paynash.pos.settings;

/**
 * The names the apps use for the {@code waiter} role, for menu items, and for sending an order (spec
 * business-features, "Configurable terms"). Stored roles and states never change with them.
 */
public record Terms(String waiter, String items, String send) {

	public static final int MAX_LENGTH = 30;

	public static final Terms DEFAULT = new Terms("Waiter", "Menu", "Send");

}
