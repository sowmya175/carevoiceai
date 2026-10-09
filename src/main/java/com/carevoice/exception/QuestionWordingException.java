package com.carevoice.exception;

public class QuestionWordingException extends RuntimeException {
    public QuestionWordingException() {
        super("Question wording failed");
    }
}
