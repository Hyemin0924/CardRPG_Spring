package com.example.cardgame.exception;

// 게임 로직에서 발생하는 "정상적인 실패"를 HTTP 응답으로 변환하기 위한 예외
public class GameApiException extends RuntimeException {
    private final int status;

    public GameApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
