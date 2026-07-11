package com.careercoach.coach.web.model;

/** One prior turn of the current strategic conversation ({@code role} = user/coach). */
public record ChatTurn(String role, String content) {
}
