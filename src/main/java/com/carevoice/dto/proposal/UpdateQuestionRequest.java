package com.carevoice.dto.proposal;

public record UpdateQuestionRequest(String questionText, Boolean required, Boolean enabled) {}
