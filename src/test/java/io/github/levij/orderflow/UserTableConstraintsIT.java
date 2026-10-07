package io.github.levij.orderflow;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.levij.orderflow.support.TestcontainersConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class UserTableConstraintsIT {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanUsers() {
		jdbcTemplate.update("DELETE FROM users");
	}

	@Test
	void duplicateEmailIsRejected() {
		insertUser("a@example.com", "CUSTOMER");

		assertThatThrownBy(() -> insertUser("a@example.com", "CUSTOMER"))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void emailWithUppercaseLettersIsRejected() {
		assertThatThrownBy(() -> insertUser("A@example.com", "CUSTOMER"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void unknownRoleIsRejected() {
		assertThatThrownBy(() -> insertUser("b@example.com", "SUPERUSER"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private void insertUser(String email, String role) {
		jdbcTemplate.update(
				"INSERT INTO users (email, password_hash, role, created_at, updated_at) VALUES (?, ?, ?, now(), now())",
				email, "not-a-real-hash", role);
	}
}
