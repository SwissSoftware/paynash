package ch.paynash.pos.order;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Placeholder until print jobs and routing exist (tasks 5.1 and 5.2). Only logs what would be printed. */
@Component
class PendingTicketPrinting implements TicketPrinting {

	private static final Logger log = LoggerFactory.getLogger(PendingTicketPrinting.class);

	@Override
	public void roundSent(Order order, OrderRound round, List<OrderLine> lines) {
		log.info("Round {} of table {} sent with {} line(s); ticket printing is not set up yet", round.number(),
				order.tableLabel(), lines.size());
	}

	@Override
	public void lineCancelled(Order order, OrderLine line) {
		log.info("Line {} × {} of table {} cancelled; ticket printing is not set up yet", line.qty(), line.name(),
				order.tableLabel());
	}

}
