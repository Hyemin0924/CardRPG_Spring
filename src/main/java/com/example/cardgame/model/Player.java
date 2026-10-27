package com.example.cardgame.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Player {
    public int id;
    public String name;
    public boolean connected = false;
    public int level = 1;
    public double experience = 0;
    public boolean eliminated = false;
    public String job;

    public Map<String, Double> stats = new LinkedHashMap<>();

    public List<Card> hand = new ArrayList<>();
    public List<Card> equipment = new ArrayList<>();

    public int usedCardsThisTurn = 0;
    public boolean turnEnded = false;

    public boolean jealousy = false;
    public boolean jealousyPending = false;

    public boolean greedNextTurn = false;
    public boolean greedPending = false;

    public boolean sick = false;
    public boolean sickPending = false;

    public Player() {
    }

    public Player(int id, String job) {
        this.id = id;
        this.name = "플레이어 " + id;
        this.job = job;
        stats.put("hp", 1.0);
        stats.put("attack", 1.0);
        stats.put("intelligence", 1.0);
        stats.put("mental", 1.0);
    }
}
