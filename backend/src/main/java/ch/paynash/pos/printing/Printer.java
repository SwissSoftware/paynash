package ch.paynash.pos.printing;

import ch.paynash.pos.menu.ItemType;

/**
 * An Epson printer reached over ePOS-Print. {@code address} is a host or IP, optionally with port, or a full
 * {@code http(s)://} base URL.
 */
public record Printer(long id, String name, String address, String deviceId, ItemType itemType, boolean active) {
}
