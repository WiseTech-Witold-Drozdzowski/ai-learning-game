package com.careercoach.coach.web;

/** One prior turn of the current strategic conversation ({@code role} = user/coach). */
public record ChatTurn(String role, String content) {
}
