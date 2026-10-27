package com.example.cardgame.model;

// CARD_DATA 카탈로그(덱 생성용) 항목
public class CardDefinition {
    public String name;
    public String type;
    public String text;
    public int count;

    public CardDefinition(String name, String type, String text, int count) {
        this.name = name;
        this.type = type;
        this.text = text;
        this.count = count;
    }

    public Card toCard() {
        return new Card(name, type, text);
    }
}
