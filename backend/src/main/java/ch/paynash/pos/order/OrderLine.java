package ch.paynash.pos.order;

import java.util.List;

import ch.paynash.pos.menu.ItemType;

/** An order line with the item's name, type, category, and price as they were when the order was sent. */
public record OrderLine(long id, long orderId, long menuItemId, String name, ItemType type, String category,
		int unitPriceRappen, int qty, String note, LineStatus status, String cancelReason) {

	/** Cancelled lines count as 0 (spec order-taking, "Order total"). */
	public long totalRappen() {
		return status == LineStatus.CANCELLED ? 0 : (long) qty * unitPriceRappen;
	}

	public static long totalRappen(List<OrderLine> lines) {
		return lines.stream().mapToLong(OrderLine::totalRappen).sum();
	}

}
