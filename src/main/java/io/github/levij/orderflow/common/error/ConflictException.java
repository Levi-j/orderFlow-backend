package io.github.levij.orderflow.common.error;

public class ConflictException extends RuntimeException {
	private final ErrorCode code;

	public ConflictException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public ErrorCode getCode() {
		return code;
	}
}
