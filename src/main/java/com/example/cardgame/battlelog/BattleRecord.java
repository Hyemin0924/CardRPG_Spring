package com.example.cardgame.battlelog;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 보스전 한 판의 결과 스냅샷. 보스전 승패가 확정되는 순간(GameService) 자동으로 1건 저장된다.
 * 필드 명은 다른 API 와 동일하게 응답에서 snake_case 로 직렬화된다(spring.jackson.property-naming-strategy).
 */
@Entity
@Table(name = "battle_record")
public class BattleRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 보스전이 벌어진 방 코드 */
    public String roomId;

    /** 기록이 저장된 시각 (ISO-8601) */
    public Instant createdAt;

    /** "win" = 보스 처치, "lose" = 파티 전멸/전원 탈락 */
    public String result;

    /** 방에 배정된 전체 슬롯 수 (max_players) */
    public int roomMaxPlayers;

    /** 실제 보스전에 참여한 인원 (connected && !eliminated) */
    public int playerCount;

    /** 전투 종료 시점에 생존한 인원 (hp > 0 && mp > 0) */
    public int survivorCount;

    /** 진행된 보스 턴 수 (전원 탈락으로 즉시 패배한 경우 0) */
    public int bossTurns;

    /** 보스 시작 HP (정상 시작이면 52, 전원 탈락이면 0) */
    public double bossInitialHp;

    /** 보스 시작 ATK (정상 시작이면 10, 전원 탈락이면 0) */
    public double bossInitialAtk;

    /** 전투 종료 시점 보스 HP */
    public double bossFinalHp;

    /** 전투 종료 시점 보스가 소환한 친구 수 */
    public int bossSummons;

    /** 표시용 마지막 메시지 (state.message) */
    @Column(length = 500)
    public String message;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "battle_record_id")
    public List<BattleParticipant> participants = new ArrayList<>();
}
