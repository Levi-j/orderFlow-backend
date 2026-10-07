package io.github.levij.orderflow.auth;

public record AccessToken(String value, long expiresInSeconds) {

	@Override
	public String toString() {
		return "AccessToken[value=***, expiresInSeconds=" + expiresInSeconds + "]";
	}
}
