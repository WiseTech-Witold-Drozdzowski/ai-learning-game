package com.careercoach.tasks.web.model;

import java.util.List;

/**
 * Body of {@code POST /tasks/{id}/submit}. {@code artifact} feeds the artifact-review
 * path; {@code answers} feed the AUTO_QUIZ path (issue-5). Both are optional — the
 * task's verification method decides which is used.
 */
public record SubmitRequest(String artifact, List<String> answers) {
}
