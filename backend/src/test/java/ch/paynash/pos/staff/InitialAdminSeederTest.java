package ch.paynash.pos.staff;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import ch.paynash.pos.support.IntegrationTest;

@ExtendWith(OutputCaptureExtension.class)
class InitialAdminSeederTest extends IntegrationTest {

	@Autowired
	InitialAdminSeeder seeder;

	@Test
	void createsFirstAdminWithLoggedOneTimePin(CapturedOutput output) throws Exception {
		seeder.run(new DefaultApplicationArguments());

		var admin = staffRepository.findByUsername("admin").orElseThrow();
		assertThat(admin.role()).isEqualTo(Role.ADMIN);
		var matcher = java.util.regex.Pattern.compile("One-time PIN: (\\d{6})").matcher(output.getOut());
		assertThat(matcher.find()).isTrue();
		login("admin", matcher.group(1));
	}

	@Test
	void doesNothingWhenStaffExists() {
		createStaff("boss", Role.ADMIN, "1111");
		seeder.run(new DefaultApplicationArguments());
		assertThat(staffRepository.findAll()).hasSize(1);
	}

}
