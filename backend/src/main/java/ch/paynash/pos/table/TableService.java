package ch.paynash.pos.table;

import java.util.Comparator;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.paynash.pos.common.ApiException;
import ch.paynash.pos.common.TableLabels;

/**
 * The restaurant's tables (spec table-management). Orders keep their table label as text, so changing or deleting a
 * table never looks at orders (design D3).
 */
@Service
public class TableService {

	/** Purely numeric numbers first, in numeric order, then the rest alphabetically (design D5). */
	static final Comparator<DiningTable> BY_NUMBER = Comparator
		.comparing((DiningTable t) -> !isNumeric(t.number()))
		.thenComparing(t -> isNumeric(t.number()) ? Long.parseLong(t.number()) : 0L)
		.thenComparing(DiningTable::number);

	private final TableRepository tables;

	public TableService(TableRepository tables) {
		this.tables = tables;
	}

	public List<DiningTable> all() {
		return tables.findAll().stream().sorted(BY_NUMBER).toList();
	}

	public List<DiningTable> active() {
		return tables.findAll().stream().filter(DiningTable::active).sorted(BY_NUMBER).toList();
	}

	@Transactional
	public DiningTable create(String number, String name, boolean active) {
		String n = normalize(number, -1);
		try {
			return get(tables.insert(n, blankToNull(name), active));
		}
		catch (DuplicateKeyException ex) {
			throw numberTaken();
		}
	}

	@Transactional
	public DiningTable update(long id, String number, String name, boolean active) {
		get(id);
		String n = normalize(number, id);
		try {
			tables.update(id, n, blankToNull(name), active);
		}
		catch (DuplicateKeyException ex) {
			throw numberTaken();
		}
		return get(id);
	}

	@Transactional
	public void delete(long id) {
		get(id);
		tables.delete(id);
	}

	private String normalize(String number, long id) {
		String n = TableLabels.normalize(number, "number", "Number");
		if (tables.existsByNumber(n, id)) {
			throw numberTaken();
		}
		return n;
	}

	private DiningTable get(long id) {
		return tables.findById(id).orElseThrow(() -> ApiException.notFound("Table"));
	}

	private static boolean isNumeric(String number) {
		return number.chars().allMatch(Character::isDigit);
	}

	private static ApiException numberTaken() {
		return ApiException.validation("number", "This number is already in use");
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

}
