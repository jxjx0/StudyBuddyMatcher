package com.studybuddy.connection.dto;

/**
 * Contact details for a student, kept separate from {@link StudentDto} so that
 * they can only be released deliberately - after a match request has been
 * accepted, per the project brief.
 */
public class StudentContactDto {

	private String userId;
	private String name;
	private String contactNumber;

	public StudentContactDto() {
	}

	public StudentContactDto(String userId, String name, String contactNumber) {
		this.userId = userId;
		this.name = name;
		this.contactNumber = contactNumber;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getContactNumber() {
		return contactNumber;
	}

	public void setContactNumber(String contactNumber) {
		this.contactNumber = contactNumber;
	}
}
