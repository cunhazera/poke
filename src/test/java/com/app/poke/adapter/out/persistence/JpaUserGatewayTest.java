package com.app.poke.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.app.poke.application.port.out.NewUser;
import com.app.poke.domain.exception.EmailTakenException;
import com.app.poke.domain.exception.UsernameTakenException;
import com.app.poke.domain.model.EmailAddress;
import com.app.poke.domain.model.Role;
import com.app.poke.domain.model.User;
import com.app.poke.domain.model.Username;
import com.app.poke.support.TestcontainersConfiguration;

/**
 * The real gateway, Hibernate and Flyway schema on PostgreSQL. Not wrapped in a test transaction: each call commits
 * like in production, so unique violations and concurrent inserts behave for real.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JpaUserGateway.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaUserGatewayTest {

	@Autowired
	private JpaUserGateway gateway;

	@Autowired
	private JdbcTemplate db;

	@BeforeEach
	void empty() {
		db.execute("TRUNCATE app_user RESTART IDENTITY");
	}

	private static NewUser newUser(String username, String email) {
		return new NewUser(new Username(username), new EmailAddress(email), "$2a$10$hash-of-" + username, Role.USER);
	}

	@Test
	void createsAndReadsBackAUserKeepingItsCasing() {
		var created = gateway.create(newUser("AshKetchum", "Ash@Pallet.town"));

		assertThat(created.id()).isPositive();
		assertThat(gateway.findById(created.id())).contains(new User(created.id(), new Username("AshKetchum"),
				new EmailAddress("Ash@Pallet.town"), "$2a$10$hash-of-AshKetchum", Role.USER));
	}

	@Test
	void theRoleIsStoredByName() {
		var admin = gateway.create(new NewUser(new Username("oak"), new EmailAddress("oak@pallet.town"), "h", Role.ADMIN));

		assertThat(db.queryForObject("SELECT role FROM app_user WHERE id = ?", String.class, admin.id())).isEqualTo("ADMIN");
	}

	@ParameterizedTest(name = "login = {0}")
	@ValueSource(strings = { "AshKetchum", "ashketchum", "ASHKETCHUM", "Ash@Pallet.town", "ash@PALLET.TOWN" })
	void findsByUsernameOrEmailIgnoringCase(String login) {
		gateway.create(newUser("AshKetchum", "Ash@Pallet.town"));

		assertThat(gateway.findByLogin(login)).map(u -> u.username().value()).contains("AshKetchum");
	}

	@Test
	void findsNothingForUnknownLoginsOrIds() {
		gateway.create(newUser("ash", "ash@pallet.town"));

		assertThat(gateway.findByLogin("gary")).isEmpty();
		assertThat(gateway.findByLogin("ash@")).isEmpty();
		assertThat(gateway.findById(999)).isEmpty();
	}

	@Test
	void usernameTakenInAnotherCaseBecomesADomainConflictAndStoresNothing() {
		gateway.create(newUser("AshKetchum", "ash@pallet.town"));

		assertThatThrownBy(() -> gateway.create(newUser("ashketchum", "other@pallet.town")))
				.isInstanceOf(UsernameTakenException.class);
		assertThat(db.queryForObject("SELECT count(*) FROM app_user", Integer.class)).isEqualTo(1);
	}

	@Test
	void emailTakenInAnotherCaseBecomesADomainConflict() {
		gateway.create(newUser("ash", "Ash@Pallet.town"));

		assertThatThrownBy(() -> gateway.create(newUser("misty", "ash@PALLET.TOWN"))).isInstanceOf(EmailTakenException.class);
	}

	@Test
	void concurrentRegistrationsOfTheSameUsernameLetExactlyOneWin() throws Exception {
		int racers = 8;
		var start = new CyclicBarrier(racers);
		List<Future<Object>> results = new ArrayList<>();
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			for (int i = 0; i < racers; i++) {
				var email = "ash" + i + "@pallet.town";
				var casing = i % 2 == 0 ? "ash" : "ASH";
				Callable<Object> register = () -> {
					start.await();
					try {
						return gateway.create(newUser(casing, email));
					} catch (UsernameTakenException e) {
						return e;
					}
				};
				results.add(executor.submit(register));
			}
		}

		var outcomes = results.stream().map(JpaUserGatewayTest::get).toList();
		assertThat(outcomes).filteredOn(User.class::isInstance).hasSize(1);
		assertThat(outcomes).filteredOn(UsernameTakenException.class::isInstance).hasSize(racers - 1);
	}

	@Test
	void theLoginLookupUsesTheCaseInsensitiveIndexes() {
		db.execute("SET enable_seqscan = off"); // a tiny table would be seq-scanned anyway; ask what the plan *can* use
		var plan = db.queryForList("EXPLAIN " + UserJpaRepository.FIND_BY_LOGIN.replace(":login", "'ash'"), String.class);

		assertThat(String.join("\n", plan))
				.contains(JpaUserGateway.USERNAME_INDEX)
				.contains(JpaUserGateway.EMAIL_INDEX)
				.doesNotContain("Seq Scan");
	}

	private static Object get(Future<Object> future) {
		try {
			return future.get();
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}
}
