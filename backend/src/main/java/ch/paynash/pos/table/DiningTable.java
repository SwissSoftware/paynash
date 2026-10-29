package ch.paynash.pos.table;

/** A table of the restaurant. {@code number} is upper-case and is what orders store as their table label. */
public record DiningTable(long id, String number, String name, boolean active) {
}
