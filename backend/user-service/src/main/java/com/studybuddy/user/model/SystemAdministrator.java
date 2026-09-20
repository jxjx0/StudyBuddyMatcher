package com.studybuddy.user.model;

/**
 * Administrator account.
 *
 * The UML gives this class createUserAccount, deleteUserAccount and similar
 * methods. Those live in UserAccountService instead: a model object has no
 * repository, so it cannot read or write other accounts. This class identifies
 * who the administrator is; the service performs the work.
 */
public class SystemAdministrator extends User {

	private String name;

	public SystemAdministrator() {
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
