package io.github.levij.orderflow.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import io.github.levij.orderflow.common.error.ConflictException;
import io.github.levij.orderflow.common.error.ErrorCode;
import io.github.levij.orderflow.product.dto.CreateProductRequest;
import io.github.levij.orderflow.support.TestcontainersConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ProductServiceIT {

	@Autowired
	private ProductService productService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoSpyBean
	private ProductRepository productRepository;

	@BeforeEach
	void cleanProducts() {
		jdbcTemplate.update("DELETE FROM products");
	}

	@Test
	void databaseConstraintStillCatchesDuplicateWhenPreCheckIsBypassed() {
		CreateProductRequest request = new CreateProductRequest("RACE-1", "Thing", null, new BigDecimal("1.00"));
		productService.create(request);

		doReturn(false).when(productRepository).existsBySku(anyString());

		assertThatThrownBy(() -> productService.create(request))
				.isInstanceOfSatisfying(ConflictException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.DUPLICATE_SKU));
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class)).isEqualTo(1);
	}

}
