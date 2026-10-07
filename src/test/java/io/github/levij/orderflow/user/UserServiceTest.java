package io.github.levij.orderflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.levij.orderflow.common.error.ConflictException;
import io.github.levij.orderflow.common.error.ErrorCode;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	private UserService userService() {
		return new UserService(userRepository, passwordEncoder);
	}

	@Test
	void registerCustomerNormalizesEmailHashesPasswordAndCreatesCustomer() {
		String rawPassword = "  Mixed Case Password with spaces  ";
		when(userRepository.existsByEmail("jane.doe@example.com")).thenReturn(false);
		when(passwordEncoder.encode(rawPassword)).thenReturn("$2a$10$encodedHashForTest");
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		User result = userService().registerCustomer("Jane.Doe@Example.COM", rawPassword);

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(saved.capture());
		User savedUser = saved.getValue();
		assertThat(savedUser.getEmail()).isEqualTo("jane.doe@example.com");
		assertThat(savedUser.getRole()).isEqualTo(Role.CUSTOMER);
		assertThat(savedUser.getPasswordHash()).isEqualTo("$2a$10$encodedHashForTest");
		assertThat(savedUser.getPasswordHash()).isNotEqualTo(rawPassword);
		assertThat(result).isSameAs(savedUser);
		verify(passwordEncoder).encode(rawPassword);
	}

	@Test
	void registerCustomerRejectsEmailThatAlreadyExists() {
		when(userRepository.existsByEmail("jane.doe@example.com")).thenReturn(true);

		assertThatThrownBy(() -> userService().registerCustomer("Jane.Doe@example.com", "some long password here"))
				.isInstanceOfSatisfying(ConflictException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED));

		verifyNoInteractions(passwordEncoder);
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void bootstrapAdminIsCreatedWhenEmailIsNew() {
		String rawPassword = " Bootstrap admin password ";
		when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
		when(passwordEncoder.encode(rawPassword)).thenReturn("$2a$10$adminHashForTest");
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		boolean created = userService().ensureBootstrapAdmin("Admin@Example.COM", rawPassword);

		assertThat(created).isTrue();
		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.com");
		assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
		assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$10$adminHashForTest");
		verify(passwordEncoder).encode(rawPassword);
	}

	@Test
	void existingAdminIsLeftUntouched() {
		User existingAdmin = User.bootstrapAdmin("admin@example.com", "$2a$10$existingHash");
		when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(existingAdmin));

		boolean created = userService().ensureBootstrapAdmin("admin@example.com", "a different new password");

		assertThat(created).isFalse();
		assertThat(existingAdmin.getPasswordHash()).isEqualTo("$2a$10$existingHash");
		verifyNoInteractions(passwordEncoder);
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void existingCustomerIsNeverPromoted() {
		User customer = User.registerCustomer("admin@example.com", "$2a$10$customerHash");
		when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(customer));

		assertThatThrownBy(() -> userService().ensureBootstrapAdmin("admin@example.com", "bootstrap admin password"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageNotContaining("admin@example.com");

		assertThat(customer.getRole()).isEqualTo(Role.CUSTOMER);
		assertThat(customer.getPasswordHash()).isEqualTo("$2a$10$customerHash");
		verifyNoInteractions(passwordEncoder);
		verify(userRepository, never()).saveAndFlush(any());
	}
}
