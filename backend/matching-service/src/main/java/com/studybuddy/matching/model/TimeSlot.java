package com.studybuddy.matching.model;

import java.time.LocalTime;

/**
 * A recurring weekly availability window, for example "WEDNESDAY 19:00-21:00".
 *
 * Every service that handles availability keeps its own copy of this class.
 * The overlap methods must behave identically in each copy: if matching-service
 * and group-service disagree about whether two students can meet, users see
 * contradictory results. Change all copies together.
 */
public class TimeSlot {

	private String day;
	private LocalTime startTime;
	private LocalTime endTime;

	public TimeSlot() {
	}

	public TimeSlot(String day, LocalTime startTime, LocalTime endTime) {
		this.day = day;
		this.startTime = startTime;
		this.endTime = endTime;
	}

	public String getDay() {
		return day;
	}

	public void setDay(String day) {
		this.day = day;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public void setStartTime(LocalTime startTime) {
		this.startTime = startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

	public void setEndTime(LocalTime endTime) {
		this.endTime = endTime;
	}

	/**
	 * Whether this slot shares any part of the same day and time with another.
	 */
	public boolean overlaps(TimeSlot other) {
		// TODO: implement
		return false;
	}

	/**
	 * Length of the shared window in minutes, or 0 when the slots do not overlap.
	 */
	public int overlapMinutes(TimeSlot other) {
		// TODO: implement
		return 0;
	}
}
