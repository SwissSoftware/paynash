package ch.paynash.pos.table;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import ch.paynash.pos.support.IntegrationTest;

class TableSchemaTest extends IntegrationTest {

	@Test
	void numberIsUniqueIgnoringCase() {
		jdbc.sql("INSERT INTO dining_table (number) VALUES ('T5')").update();
		assertThatThrownBy(() -> jdbc.sql("INSERT INTO dining_table (number, active) VALUES ('t5', false)").update())
			.isInstanceOf(DuplicateKeyException.class);
	}

}
