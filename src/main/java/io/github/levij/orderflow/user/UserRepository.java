package io.github.levij.orderflow.user;

import org.springframework.data.jpa.repository.JpaRepository;

interface UserRepository extends JpaRepository<User, Long> {

	boolean existsByEmail(String email);
}
