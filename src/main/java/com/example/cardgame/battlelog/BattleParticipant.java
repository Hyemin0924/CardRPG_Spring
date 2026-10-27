package com.example.cardgame.battlelog;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 보스전 한 판에 참여한 플레이어 1명의 스냅샷.
 * "보스전 진입 시점(성장 결과)" 스탯과 "전투 종료 시점" 상태를 함께 담는다.
 */
@Entity
@Table(name = "battle_participant")
public class BattleParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 방 안에서의 플레이어 번호 (1-based) */
    public int playerId;

    public String name;

    /** 보스전 포지션 = 직업 (탱커/딜러/서포터/힐러) */
    public String job;

    public int level;

    public double experience;

    /** 성장 단계에서 레벨 미달로 탈락했는지 (탈락자는 보통 보스파티에 없음, 참고용) */
    public boolean eliminated;

    // ---- 보스전 진입 시점 스탯 (성장 결과) ----
    public double statHp;
    public double statAttack;
    public double statIntelligence;
    public double statMental;

    // ---- 장비 (슬롯당 최고 등급 1개) ----
    /** 장착 장비 이름 전체, 쉼표로 연결 (없으면 빈 문자열) */
    public String equipment;
    public String weapon;
    public String armor;
    public String ring;
    /** 배율 (해당 슬롯 장비 없으면 1.0) */
    public double weaponMult = 1.0;
    public double armorMult = 1.0;
    public double ringMult = 1.0;

    // ---- 전투 종료 시점 상태 ----
    public double endHp;
    public double endMaxHp;
    public double endMp;
    public double endAtk;
    public double endIntel;
    /** 표시용 초기 최대 HP / MP (BossPartyMember.initialMaxHp / initialMaxMp) */
    public double initialMaxHp;
    public double initialMaxMp;
    public boolean stunned;

    /** 전투 종료 시점 생존 여부 (endHp > 0 && endMp > 0) */
    public boolean alive;
}
