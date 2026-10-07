package io.github.levij.orderflow.auth.dto;

public record LoginResponse(
		String accessToken,
		String tokenType,
		long expiresIn) {

	@Override
	public String toString() {
		return "LoginResponse[accessToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
	}
}
