package com.example.cardgame.model;

// 실제 손패/장비로 존재하는 카드 인스턴스
public class Card {
    public String name;
    public String type;
    public String text;

    public Card() {
    }

    public Card(String name, String type, String text) {
        this.name = name;
        this.type = type;
        this.text = text;
    }

    public Card copy() {
        return new Card(name, type, text);
    }
}
