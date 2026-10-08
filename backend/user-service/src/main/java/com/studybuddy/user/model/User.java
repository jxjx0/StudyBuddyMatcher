package com.studybuddy.user.model;

import java.util.Locale;
import java.util.Objects;

/** Base account shared by students and system administrators. */
public class User {

	private String userId;
	private String email;
	private String passwordHash;
	private AccountStatus accountStatus = AccountStatus.ACTIVE;

	public User() { }

	public User(String userId, String email, String passwordHash, AccountStatus accountStatus) {
		this.userId = userId;
		setEmail(email);
		this.passwordHash = passwordHash;
		this.accountStatus = Objects.requireNonNullElse(accountStatus, AccountStatus.ACTIVE);
	}

	public String getUserId() { return userId; }
	public void setUserId(String userId) { this.userId = userId; }
	public String getEmail() { return email; }
	public void setEmail(String email) {
		this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}
	/** Stored password hash only. API responses use account DTOs that omit it. */
	public String getPasswordHash() { return passwordHash; }
	public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
	public AccountStatus getAccountStatus() { return accountStatus; }
	public void setAccountStatus(AccountStatus accountStatus) {
		this.accountStatus = Objects.requireNonNull(accountStatus, "accountStatus is required");
	}
}
