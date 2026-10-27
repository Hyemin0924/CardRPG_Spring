package com.example.cardgame.model;

import java.util.ArrayList;
import java.util.List;

public class GameState {
    public boolean started = false;
    public int maxPlayers = 0;
    public int round = 0;
    public int maxRounds;
    public List<Card> deck = new ArrayList<>();
    public List<Player> players = new ArrayList<>();
    public String message = "";
    public boolean gameOver = false;

    // 전체 협력카드 사용 금지 (다음 턴부터 적용)
    public boolean split = false;
    // 분열 카드를 사용해서 다음 턴부터 적용될 예정인 상태
    public boolean splitPending = false;

    // 보스전 관련 상태
    public boolean bossActive = false;
    public Boss boss;
    public Integer bossTurn;
    public List<String> bossLog = new ArrayList<>();
    public List<BossPartyMember> bossParty = new ArrayList<>();
    public String bossResult;
    public BossBuffs bossBuffs = new BossBuffs();

    // 보스전 결과가 전투 기록 DB에 이미 저장됐는지 (중복 저장 방지용, 응답에는 노출 안 함)
    public boolean bossRecorded = false;
}
