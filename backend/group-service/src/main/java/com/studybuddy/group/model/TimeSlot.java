package com.studybuddy.group.model;

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
		if (other == null || this.day == null || other.getDay() == null) {
			return false;
		}
		if (!this.day.equalsIgnoreCase(other.getDay())) {
			return false;
		}
		if (this.startTime == null || this.endTime == null || other.getStartTime() == null || other.getEndTime() == null) {
			return false;
		}
		return this.startTime.isBefore(other.getEndTime()) && this.endTime.isAfter(other.getStartTime());
	}

	/**
	 * Length of the shared window in minutes, or 0 when the slots do not overlap.
	 */
	public int overlapMinutes(TimeSlot other) {
		if (!overlaps(other)) {
			return 0;
		}
		LocalTime latestStart = this.startTime.isAfter(other.getStartTime()) ? this.startTime : other.getStartTime();
		LocalTime earliestEnd = this.endTime.isBefore(other.getEndTime()) ? this.endTime : other.getEndTime();

		return (int) java.time.Duration.between(latestStart, earliestEnd).toMinutes();
	}
}
