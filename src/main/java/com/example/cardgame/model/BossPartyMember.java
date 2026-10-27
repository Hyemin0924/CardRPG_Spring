package com.example.cardgame.model;

// 보스전에서 사용하는 플레이어 파티원 스냅샷
public class BossPartyMember {
    public int playerId;
    public String name;
    public String pos;
    public double maxHp;
    public double hp;
    public double atk;
    public double intel;
    public double mp;
    public boolean stunned = false;
    public double initialMaxHp;
    public double initialMaxMp;
}
