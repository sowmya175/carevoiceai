package com.carevoice.dto.proposal;

public record AddQuestionRequest(String fieldCode, String questionText, Boolean required) {}
