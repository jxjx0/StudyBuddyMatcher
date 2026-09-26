package com.studybuddy.user.model;

/**
 * Base type for every account. Extended by Student and SystemAdministrator.
 */
public class User {

	private String userId;
	private AccountStatus accountStatus = AccountStatus.ACTIVE;

	public User() {
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public AccountStatus getAccountStatus() {
		return accountStatus;
	}

	public void changeAccountStatus(AccountStatus status) {
		// TODO: implement
	}
}
