package ch.paynash.pos.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import ch.paynash.pos.support.IntegrationTest;

class CustomerSchemaTest extends IntegrationTest {

	@Test
	void numbersAreUniqueAndNeverGivenAgain() {
		long first = insert("Meier");
		int firstNumber = number(first);
		long second = insert("Keller");
		assertThat(number(second)).isGreaterThan(firstNumber);

		jdbc.sql("DELETE FROM customer WHERE id = ?").param(second).update();
		long third = insert("Huber");
		assertThat(number(third)).isGreaterThan(firstNumber + 1);

		assertThatThrownBy(() -> jdbc.sql("UPDATE customer SET number = ? WHERE id = ?")
			.params(firstNumber, third)
			.update()).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void aCustomerNeedsANameUnlessAnonymized() {
		assertThatThrownBy(() -> jdbc.sql("INSERT INTO customer (phone) VALUES ('079')").update())
			.isInstanceOf(DataIntegrityViolationException.class);
		jdbc.sql("INSERT INTO customer (anonymized_at, active) VALUES (now(), false)").update();
	}

	private long insert(String lastName) {
		return jdbc.sql("INSERT INTO customer (last_name) VALUES (?) RETURNING id")
			.param(lastName)
			.query(Long.class)
			.single();
	}

	private int number(long id) {
		return jdbc.sql("SELECT number FROM customer WHERE id = ?").param(id).query(Integer.class).single();
	}

}
