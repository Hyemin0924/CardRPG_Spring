package com.example.cardgame.battlelog;

import com.example.cardgame.config.CardCatalog;
import com.example.cardgame.exception.GameApiException;
import com.example.cardgame.model.BossPartyMember;
import com.example.cardgame.model.Card;
import com.example.cardgame.model.EquipmentInfo;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BattleRecordService {

    // 장비 이름 -> 배율 (GAME_SPEC §3.2). 현재 전투 로직엔 미반영이지만 "어떤 장비였는지" 기록엔 남긴다.
    private static final Map<String, Double> EQUIPMENT_MULT = Map.of(
            "검", 1.10,
            "가죽 갑옷", 1.05,
            "철 갑옷", 1.10,
            "금 갑옷", 1.15,
            "다이아 갑옷", 1.20,
            "은반지", 1.05,
            "금반지", 1.10
    );

    private final BattleRecordRepository repository;

    public BattleRecordService(BattleRecordRepository repository) {
        this.repository = repository;
    }

    /** 보스전 승패가 확정된 GameState 로부터 기록 1건을 만들어 저장한다. */
    @Transactional
    public BattleRecord record(String roomId, GameState state) {
        BattleRecord r = new BattleRecord();
        r.roomId = roomId;
        r.createdAt = Instant.now();
        r.result = state.bossResult;
        r.message = truncate(state.message, 500);
        r.roomMaxPlayers = state.maxPlayers;

        boolean emptyParty = state.bossParty == null || state.bossParty.isEmpty();
        r.playerCount = emptyParty ? 0 : state.bossParty.size();
        r.bossTurns = emptyParty ? 0 : (state.bossTurn == null ? 0 : state.bossTurn);
        r.bossInitialHp = emptyParty ? 0 : 52;
        r.bossInitialAtk = emptyParty ? 0 : 10;
        r.bossFinalHp = state.boss != null ? state.boss.hp : 0;
        r.bossSummons = state.boss != null ? state.boss.summons : 0;

        int survivors = 0;
        if (!emptyParty) {
            for (BossPartyMember member : state.bossParty) {
                Player player = findPlayer(state, member.playerId);
                BattleParticipant p = toParticipant(member, player);
                if (p.alive) {
                    survivors++;
                }
                r.participants.add(p);
            }
        }
        r.survivorCount = survivors;

        return repository.save(r);
    }

    private BattleParticipant toParticipant(BossPartyMember member, Player player) {
        BattleParticipant p = new BattleParticipant();
        p.playerId = member.playerId;
        p.name = member.name;
        p.job = member.pos;

        if (player != null) {
            p.level = player.level;
            p.experience = player.experience;
            p.eliminated = player.eliminated;
            p.statHp = player.stats.getOrDefault("hp", 0.0);
            p.statAttack = player.stats.getOrDefault("attack", 0.0);
            p.statIntelligence = player.stats.getOrDefault("intelligence", 0.0);
            p.statMental = player.stats.getOrDefault("mental", 0.0);
            applyEquipment(p, player.equipment);
        }

        p.endHp = member.hp;
        p.endMaxHp = member.maxHp;
        p.endMp = member.mp;
        p.endAtk = member.atk;
        p.endIntel = member.intel;
        p.initialMaxHp = member.initialMaxHp;
        p.initialMaxMp = member.initialMaxMp;
        p.stunned = member.stunned;
        p.alive = member.hp > 0 && member.mp > 0;
        return p;
    }

    private void applyEquipment(BattleParticipant p, List<Card> equipment) {
        if (equipment == null || equipment.isEmpty()) {
            p.equipment = "";
            return;
        }
        p.equipment = equipment.stream()
                .map(c -> c.name)
                .collect(Collectors.joining(", "));
        for (Card eq : equipment) {
            EquipmentInfo info = CardCatalog.EQUIPMENT_INFO.get(eq.name);
            if (info == null) {
                continue;
            }
            double mult = EQUIPMENT_MULT.getOrDefault(eq.name, 1.0);
            switch (info.slot) {
                case "무기" -> {
                    p.weapon = eq.name;
                    p.weaponMult = mult;
                }
                case "갑옷" -> {
                    p.armor = eq.name;
                    p.armorMult = mult;
                }
                case "반지" -> {
                    p.ring = eq.name;
                    p.ringMult = mult;
                }
                default -> {
                }
            }
        }
    }

    private Player findPlayer(GameState state, int playerId) {
        for (Player player : state.players) {
            if (player.id == playerId) {
                return player;
            }
        }
        return null;
    }

    // ===================== 조회 / 삭제 / 통계 =====================

    @Transactional(readOnly = true)
    public List<BattleRecord> list(String roomId, String result) {
        if (roomId != null && !roomId.isBlank()) {
            return repository.findByRoomIdOrderByCreatedAtDesc(roomId);
        }
        if (result != null && !result.isBlank()) {
            return repository.findByResultOrderByCreatedAtDesc(result);
        }
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public BattleRecord get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new GameApiException(404, "존재하지 않는 전투 기록입니다."));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new GameApiException(404, "존재하지 않는 전투 기록입니다.");
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        long total = repository.count();
        long wins = repository.countByResult("win");
        long losses = repository.countByResult("lose");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_battles", total);
        m.put("wins", wins);
        m.put("losses", losses);
        m.put("win_rate", total == 0 ? 0.0 : round2((double) wins / total));
        m.put("average_boss_turns", round2(repository.averageBossTurns()));

        List<Map<String, Object>> byJob = new ArrayList<>();
        for (Object[] row : repository.jobBreakdown()) {
            Map<String, Object> j = new LinkedHashMap<>();
            j.put("job", row[0]);
            j.put("appearances", ((Number) row[1]).longValue());
            j.put("survived", ((Number) row[2]).longValue());
            byJob.add(j);
        }
        m.put("by_job", byJob);
        return m;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
