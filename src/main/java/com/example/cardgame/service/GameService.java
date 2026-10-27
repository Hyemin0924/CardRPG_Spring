package com.example.cardgame.service;

import com.example.cardgame.battlelog.BattleRecordService;
import com.example.cardgame.config.CardCatalog;
import com.example.cardgame.exception.GameApiException;
import com.example.cardgame.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {

    private static final Logger logger = LoggerFactory.getLogger("game_logger");
    private static final String ROOM_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    // room_id -> game state (서버 하나에서 여러 방을 동시에 관리)
    private final Map<String, GameState> games = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private final BattleRecordService battleRecordService;

    public GameService(BattleRecordService battleRecordService) {
        this.battleRecordService = battleRecordService;
    }

    // 보스전 결과가 확정되면 전투 기록 1건을 저장한다. 저장 실패가 게임 진행을 막지 않도록 예외는 삼킨다.
    private void recordBattleIfFinished(String roomId, GameState state) {
        if (state.bossResult == null || state.bossRecorded) {
            return;
        }
        try {
            battleRecordService.record(roomId, state);
            state.bossRecorded = true;
            logEvent("INFO", roomId, null, null, "BATTLE_RECORD_SAVED",
                    "result=" + state.bossResult, null);
        } catch (Exception e) {
            logEvent("ERROR", roomId, null, null, "BATTLE_RECORD_SAVED", null, e.toString());
        }
    }

    // ===================== 로깅 =====================

    private void logEvent(String level, String roomId, Integer playerId, Integer targetId,
                           String action, String details, String error) {
        List<String> parts = new ArrayList<>();
        if (roomId != null) parts.add("room=" + roomId);
        if (playerId != null) parts.add("player=" + playerId);
        if (targetId != null) parts.add("target=" + targetId);
        parts.add("action=" + action);
        if (details != null) parts.add("details=" + details);
        if (error != null) parts.add("error=" + error);
        String msg = String.join(" | ", parts);
        if ("INFO".equals(level)) logger.info(msg);
        else if ("WARNING".equals(level)) logger.warn(msg);
        else if ("ERROR".equals(level)) logger.error(msg);
    }

    // ===================== 기본 상태 관리 =====================

    private GameState createNewGameState() {
        GameState state = new GameState();
        state.maxRounds = CardCatalog.MAX_ROUNDS;
        return state;
    }

    private String generateRoomId() {
        while (true) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(ROOM_CHARS.charAt(random.nextInt(ROOM_CHARS.length())));
            }
            String roomId = sb.toString();
            if (!games.containsKey(roomId)) {
                return roomId;
            }
        }
    }

    private GameState getStateOrThrow(String roomId) {
        GameState state = games.get(roomId);
        if (state == null) {
            logEvent("WARNING", roomId, null, null, "GET_STATE", null, "ROOM_NOT_FOUND");
            throw new GameApiException(404, "존재하지 않는 방입니다.");
        }
        return state;
    }

    private void startNewTurnCycle(GameState state) {
        for (Player p : state.players) {
            p.turnEnded = false;
            p.usedCardsThisTurn = 0;

            p.jealousy = p.jealousyPending;
            p.jealousyPending = false;

            p.sick = p.sickPending;
            p.sickPending = false;

            p.greedNextTurn = p.greedPending;
            p.greedPending = false;
        }
        state.split = state.splitPending;
        state.splitPending = false;
    }

    private int getRequiredCooperationCount(GameState state) {
        int playerCount = state.maxPlayers;
        if (playerCount == 2) return 5;
        if (playerCount == 3) return 3;
        if (playerCount == 4) return 2;
        return 5;
    }

    private boolean addExperience(GameState state, Player player, double amount) {
        player.experience += amount;
        int requiredExp = getRequiredCooperationCount(state);

        boolean leveledUp = false;
        while (player.experience >= requiredExp) {
            player.experience -= requiredExp;
            player.level += 1;
            leveledUp = true;
        }
        return leveledUp;
    }

    private List<Card> createDeck() {
        List<Card> deck = new ArrayList<>();
        for (CardDefinition def : CardCatalog.CARD_DATA) {
            for (int i = 0; i < def.count; i++) {
                deck.add(def.toCard());
            }
        }
        Collections.shuffle(deck, random);
        return deck;
    }

    private Card drawCard(GameState state) {
        if (state.deck.isEmpty()) return null;
        return state.deck.remove(state.deck.size() - 1);
    }

    private List<Player> getActivePlayers(GameState state) {
        List<Player> result = new ArrayList<>();
        for (Player p : state.players) {
            if (p.connected && !p.eliminated) result.add(p);
        }
        return result;
    }

    private Player createPlayer(int playerId, String job) {
        return new Player(playerId, job);
    }

    private Player findPlayer(GameState state, Integer playerId) {
        if (playerId == null) return null;
        for (Player p : state.players) {
            if (p.id == playerId) return p;
        }
        return null;
    }

    // 숫자를 python의 동적 int/float 출력과 유사하게 표시 (정수면 소수점 없이)
    private String fmt(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    // ===================== 협력/딜레마 카드 효과 =====================

    private void applySelfCooperationEffect(Player player, String name) {
        switch (name) {
            case "전우의 격려" -> player.stats.merge("hp", 4.0, Double::sum);
            case "마법 조언" -> player.stats.merge("intelligence", 4.0, Double::sum);
            case "훈련 지원" -> player.stats.merge("attack", 4.0, Double::sum);
            case "칭찬" -> player.stats.merge("mental", 1.0, Double::sum);
            default -> {
            }
        }
    }

    private String applyTargetCardEffect(GameState state, Player sender, Player target, Card card) {
        String name = card.name;

        if (name.equals("게으름")) {
            List<Card> removableHandCards = new ArrayList<>();
            for (Card c : target.hand) {
                if (!c.type.equals("딜레마")) removableHandCards.add(c);
            }

            if (!removableHandCards.isEmpty()) {
                Card removedCard = removableHandCards.get(random.nextInt(removableHandCards.size()));
                target.hand.remove(removedCard);

                Card replacement = drawCard(state);
                String redrawMsg = "";
                if (replacement != null) {
                    target.hand.add(replacement);
                    redrawMsg = " " + target.name + "이(가) 새 카드를 뽑았습니다.";
                }
                return sender.name + "이(가) " + target.name + "의 손패 '" + removedCard.name + "' 카드를 제거했습니다." + redrawMsg;
            } else if (!target.equipment.isEmpty()) {
                Card removedEq = target.equipment.get(random.nextInt(target.equipment.size()));
                target.equipment.remove(removedEq);
                return sender.name + "이(가) " + target.name + "의 장착 장비 '" + removedEq.name + "'을(를) 제거했습니다.";
            } else {
                return target.name + "에게 제거 가능한 손패나 장비가 없어 게으름 카드가 효과 없이 소멸했습니다.";
            }
        }

        if (name.equals("민폐")) {
            List<Object[]> possibleItems = new ArrayList<>();
            for (Card c : target.hand) possibleItems.add(new Object[]{"hand", c});
            for (Card c : target.equipment) possibleItems.add(new Object[]{"equipment", c});

            if (possibleItems.isEmpty()) {
                return "모든 플레이어에게 제거 가능한 카드/장비가 없어 민폐 카드가 효과 없이 소멸했습니다.";
            }

            Object[] removed = possibleItems.get(random.nextInt(possibleItems.size()));
            String removedType = (String) removed[0];
            Card removedItem = (Card) removed[1];
            String redrawMsg = "";

            if (removedType.equals("hand")) {
                target.hand.remove(removedItem);
                Card replacement = drawCard(state);
                if (replacement != null) {
                    target.hand.add(replacement);
                    redrawMsg = " " + target.name + "이(가) 새 카드를 뽑았습니다.";
                }
            } else {
                target.equipment.remove(removedItem);
            }

            return sender.name + "이(가) " + target.name + "의 '" + removedItem.name + "'을(를) 제거했습니다." + redrawMsg;
        }

        if (name.equals("부상")) {
            List<String> statNames = List.of("hp", "attack", "intelligence");
            String selectedStat;
            double damage;

            if (sender.id == target.id) {
                selectedStat = statNames.get(random.nextInt(statNames.size()));
                damage = 4;
            } else {
                selectedStat = statNames.get(random.nextInt(statNames.size()));
                damage = 3;
            }

            target.stats.merge(selectedStat, -damage, Double::sum);
            return target.name + "의 " + selectedStat + " -" + fmt(damage);
        }

        if (name.equals("인내심 폭발")) {
            if (sender.id == target.id) {
                target.stats.merge("mental", -1.0, Double::sum);
                return target.name + "의 정신력 -1";
            }

            List<String> statNames = List.of("hp", "attack", "intelligence", "mental");
            String selectedStat = statNames.get(random.nextInt(statNames.size()));
            target.stats.merge(selectedStat, -1.0, Double::sum);
            return target.name + "의 " + selectedStat + " -1";
        }

        return "카드 효과가 적용되었습니다.";
    }

    private String applyCardEffect(GameState state, Player player, Card card) {
        String name = card.name;

        if (name.equals("덤벨")) {
            player.stats.merge("attack", 2.0, Double::sum);
            return "공격력 +2";
        } else if (name.equals("검술훈련")) {
            int dice = random.nextInt(6) + 1;
            if (dice <= 3) {
                player.stats.merge("attack", 1.0, Double::sum);
                return "주사위 " + dice + " → 공격력 +1";
            }
            player.stats.merge("attack", 2.0, Double::sum);
            return "주사위 " + dice + " → 공격력 +2";
        } else if (name.equals("책읽기")) {
            player.stats.merge("intelligence", 2.0, Double::sum);
            return "지능 +2";
        } else if (name.equals("마법연구")) {
            int dice = random.nextInt(6) + 1;
            if (dice <= 3) {
                player.stats.merge("intelligence", 1.0, Double::sum);
                return "주사위 " + dice + " → 지능 +1";
            }
            player.stats.merge("intelligence", 2.0, Double::sum);
            return "주사위 " + dice + " → 지능 +2";
        } else if (name.equals("수영")) {
            player.stats.merge("hp", 3.0, Double::sum);
            return "체력 +3";
        } else if (name.equals("등산")) {
            player.stats.merge("hp", 2.0, Double::sum);
            return "체력 +2";
        } else if (card.type.equals("장비")) {
            EquipmentInfo info = CardCatalog.EQUIPMENT_INFO.get(name);

            if (info == null) {
                player.equipment.add(card);
                return "장비를 장착했습니다.";
            }

            String slot = info.slot;
            int tier = info.tier;

            Card existing = null;
            for (Card eq : player.equipment) {
                EquipmentInfo eqInfo = CardCatalog.EQUIPMENT_INFO.get(eq.name);
                if (eqInfo != null && eqInfo.slot.equals(slot)) {
                    existing = eq;
                    break;
                }
            }

            if (existing == null) {
                player.equipment.add(card);
                return "장비를 장착했습니다.";
            }

            EquipmentInfo existingInfo = CardCatalog.EQUIPMENT_INFO.get(existing.name);

            if (existingInfo.tier >= tier) {
                return "이미 더 좋은 " + slot + " 장비(" + existing.name + ")를 장착 중이라 카드가 사라졌습니다.";
            }

            player.equipment.remove(existing);
            player.equipment.add(card);
            return existing.name + " 대신 " + name + "을(를) 장착했습니다.";
        } else if (name.equals("질투")) {
            player.jealousyPending = true;
            return "다음 턴 자신에게 협력카드를 사용할 수 없습니다.";
        } else if (name.equals("욕심")) {
            player.greedPending = true;
            return "다음 턴 동료에게 협력카드를 사용할 수 없습니다.";
        } else if (name.equals("분열")) {
            state.splitPending = true;
            return "다음 턴 모든 플레이어가 협력카드를 사용할 수 없습니다.";
        } else if (name.equals("질병")) {
            player.sickPending = true;
            return "다음 턴 경험치를 획득할 수 없습니다.";
        }

        return "카드 효과가 적용되었습니다.";
    }

    private void refillHands(GameState state, int handSize) {
        for (Player player : state.players) {
            if (!player.connected || player.eliminated) continue;
            while (player.hand.size() < handSize && !state.deck.isEmpty()) {
                player.hand.add(drawCard(state));
            }
        }
    }

    // ===================== 공개 상태(JSON) 변환 =====================

    public Map<String, Object> getPublicState(GameState state, Integer myPlayerId) {
        List<Object> players = new ArrayList<>();

        for (Player player : state.players) {
            Map<String, Object> publicPlayer = new LinkedHashMap<>();
            publicPlayer.put("id", player.id);
            publicPlayer.put("name", player.name);
            publicPlayer.put("connected", player.connected);
            publicPlayer.put("level", player.level);
            publicPlayer.put("eliminated", player.eliminated);
            publicPlayer.put("job", player.job);
            publicPlayer.put("stats", player.stats);
            publicPlayer.put("equipment", player.equipment);
            publicPlayer.put("experience", player.experience);
            publicPlayer.put("used_cards_this_turn", player.usedCardsThisTurn);
            publicPlayer.put("turn_ended", player.turnEnded);
            publicPlayer.put("jealousy", player.jealousy);
            publicPlayer.put("jealousy_pending", player.jealousyPending);
            publicPlayer.put("greed_next_turn", player.greedNextTurn);
            publicPlayer.put("greed_pending", player.greedPending);
            publicPlayer.put("sick", player.sick);
            publicPlayer.put("sick_pending", player.sickPending);

            if (myPlayerId != null && player.id == myPlayerId) {
                publicPlayer.put("hand", player.hand);
            } else {
                publicPlayer.put("hand_count", player.hand.size());
            }

            players.add(publicPlayer);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("started", state.started);
        result.put("max_players", state.maxPlayers);
        result.put("round", state.round);
        result.put("max_rounds", state.maxRounds);
        result.put("deck_count", state.deck.size());
        result.put("players", players);
        result.put("message", state.message);
        result.put("game_over", state.gameOver);
        result.put("split", state.split);
        result.put("split_pending", state.splitPending);

        result.put("boss_active", state.bossActive);
        result.put("boss", bossToMap(state.boss));
        result.put("boss_turn", state.bossTurn);
        result.put("boss_log", state.bossLog);
        result.put("boss_party", bossPartyToList(state.bossParty));
        result.put("boss_result", state.bossResult);

        result.put("game_finished", state.bossResult != null);

        return result;
    }

    private Map<String, Object> bossToMap(Boss boss) {
        if (boss == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("hp", boss.hp);
        map.put("atk", boss.atk);
        map.put("summons", boss.summons);
        return map;
    }

    private List<Object> bossPartyToList(List<BossPartyMember> party) {
        List<Object> result = new ArrayList<>();
        for (BossPartyMember p : party) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("player_id", p.playerId);
            map.put("name", p.name);
            map.put("pos", p.pos);
            map.put("maxHp", p.maxHp);
            map.put("hp", p.hp);
            map.put("atk", p.atk);
            map.put("intel", p.intel);
            map.put("mp", p.mp);
            map.put("stunned", p.stunned);
            map.put("initialMaxHp", p.initialMaxHp);
            map.put("initialMaxMp", p.initialMaxMp);
            result.add(map);
        }
        return result;
    }

    private Map<String, Object> okWithState(GameState state, Integer myPlayerId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("state", getPublicState(state, myPlayerId));
        return body;
    }

    // ===================== 방 생성 / 참가 / 조회 =====================

    public Map<String, Object> createGame(Integer playerCount, String job) {
        String creatorJob = CardCatalog.JOBS.contains(job) ? job : "탱커";

        if (playerCount == null || playerCount < 1 || playerCount > 4) {
            throw new GameApiException(400, "플레이어 수는 1~4명이어야 합니다.");
        }

        String roomId = generateRoomId();
        GameState state = createNewGameState();
        games.put(roomId, state);

        state.started = true;
        state.maxPlayers = playerCount;
        state.round = 1;
        state.deck = createDeck();
        state.players = new ArrayList<>();
        state.message = "방이 생성되었습니다.";
        state.gameOver = false;

        for (int i = 0; i < playerCount; i++) {
            String job2 = (i == 0) ? creatorJob : null;
            Player player = createPlayer(i + 1, job2);

            for (int c = 0; c < 5; c++) {
                Card card = drawCard(state);
                if (card != null) player.hand.add(card);
            }

            state.players.add(player);
        }

        state.players.get(0).connected = true;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("room_id", roomId);
        body.put("player_id", 1);
        body.put("state", getPublicState(state, 1));
        return body;
    }

    public Map<String, Object> joinGame(String roomId, String job) {
        if (!CardCatalog.JOBS.contains(job)) {
            logEvent("WARNING", roomId, null, null, "JOIN_ROOM", "job=" + job, "INVALID_JOB");
            throw new GameApiException(400, "올바른 직업(탱커, 딜러, 서포터, 힐러)을 선택해주세요.");
        }

        GameState state = getStateOrThrow(roomId);

        if (!state.started) {
            logEvent("WARNING", roomId, null, null, "JOIN_ROOM", null, "GAME_NOT_STARTED");
            throw new GameApiException(400, "아직 생성된 게임이 없습니다.");
        }

        List<String> takenJobs = new ArrayList<>();
        for (Player p : state.players) {
            if (p.connected && p.job != null) takenJobs.add(p.job);
        }

        if (takenJobs.contains(job)) {
            logEvent("WARNING", roomId, null, null, "JOIN_ROOM", "job=" + job, "JOB_ALREADY_TAKEN");
            throw new GameApiException(400, "'" + job + "' 직업은 이미 다른 플레이어가 선택했습니다.");
        }

        for (Player player : state.players) {
            if (!player.connected) {
                player.connected = true;
                player.job = job;
                logEvent("INFO", roomId, player.id, null, "JOIN_ROOM", "job=" + job, null);

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("player_id", player.id);
                body.put("state", getPublicState(state, player.id));
                return body;
            }
        }

        logEvent("WARNING", roomId, null, null, "JOIN_ROOM", null, "ROOM_FULL");
        throw new GameApiException(400, "모든 플레이어가 이미 참가했습니다.");
    }

    public Map<String, Object> getState(String roomId, Integer playerId) {
        GameState state = getStateOrThrow(roomId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("state", getPublicState(state, playerId));
        return body;
    }

    // ===================== 대상 지정 카드(협력/딜레마) 처리 =====================

    private Map<String, Object> resolveTargetCardUse(GameState state, String roomId, Integer senderId,
                                                       Integer targetId, Integer cardIndex, List<String> allowedTypes) {
        Player sender = findPlayer(state, senderId);
        Player target = findPlayer(state, targetId);

        if (sender == null || target == null) {
            logEvent("WARNING", roomId, null, null, "USE_TARGET_CARD", null, "INVALID_PLAYER_TARGET");
            throw new GameApiException(400, "플레이어 대상 정보가 올바르지 않습니다.");
        }

        if (sender.turnEnded) {
            logEvent("WARNING", roomId, null, null, "USE_TARGET_CARD", null, "TURN_ALREADY_ENDED");
            throw new GameApiException(400, "이미 턴을 종료했습니다.");
        }

        if (sender.usedCardsThisTurn >= CardCatalog.MAX_CARDS_PER_TURN) {
            logEvent("WARNING", roomId, null, null, "USE_TARGET_CARD", null, "MAX_CARDS_EXCEEDED");
            throw new GameApiException(400, "한 턴에 최대 " + CardCatalog.MAX_CARDS_PER_TURN + "장까지 사용할 수 있습니다.");
        }

        if (cardIndex == null || cardIndex < 0 || cardIndex >= sender.hand.size()) {
            logEvent("WARNING", roomId, senderId, null, "USE_TARGET_CARD", "card_index=" + cardIndex, "INVALID_CARD_INDEX");
            throw new GameApiException(400, "잘못된 카드 선택입니다.");
        }

        Card card = sender.hand.get(cardIndex);
        String cardName = card.name;
        boolean isCooperation = card.type.equals("협력");

        if (allowedTypes != null && !allowedTypes.contains(card.type)) {
            logEvent("WARNING", roomId, senderId, null, "USE_TARGET_CARD", "card=" + cardName, "CARD_TYPE_NOT_ALLOWED");
            throw new GameApiException(400, "이 카드는 이 방식으로 사용할 수 없습니다.");
        }

        if (isCooperation) {
            if (state.split) {
                logEvent("WARNING", roomId, senderId, null, "USE_TARGET_CARD", "card=" + cardName, "RESTRICTED_BY_SPLIT");
                throw new GameApiException(400, "분열의 효과로 이번 턴 협력카드를 사용할 수 없습니다.");
            }
            if (senderId.equals(targetId) && sender.jealousy) {
                logEvent("WARNING", roomId, senderId, null, "USE_TARGET_CARD", "card=" + cardName, "RESTRICTED_BY_JEALOUSY");
                throw new GameApiException(400, "질투의 효과로 이번 턴 자신에게 협력카드를 사용할 수 없습니다.");
            }
            if (!senderId.equals(targetId) && sender.greedNextTurn) {
                logEvent("WARNING", roomId, senderId, null, "USE_TARGET_CARD", "card=" + cardName, "RESTRICTED_BY_GREED");
                throw new GameApiException(400, "욕심의 효과로 이번 턴 동료에게 협력카드를 사용할 수 없습니다.");
            }
        }

        if (cardName.equals("민폐")) {
            if (senderId.equals(targetId)) {
                logEvent("WARNING", roomId, senderId, null, "USE_TARGET_CARD", "card=" + cardName, "SELF_TARGET_NOT_ALLOWED");
                throw new GameApiException(400, "민폐 카드는 자신에게 사용할 수 없습니다.");
            }

            boolean targetHasItems = !target.hand.isEmpty() || !target.equipment.isEmpty();
            if (!targetHasItems) {
                boolean otherPlayerHasItems = false;
                for (Player p : state.players) {
                    if (p.connected && p.id != senderId && (!p.hand.isEmpty() || !p.equipment.isEmpty())) {
                        otherPlayerHasItems = true;
                        break;
                    }
                }
                if (otherPlayerHasItems) {
                    logEvent("WARNING", roomId, senderId, targetId, "USE_TARGET_CARD", "card=" + cardName, "TARGET_HAS_NO_ITEMS_TRY_OTHER");
                    throw new GameApiException(400, "'" + target.name + "'에게 제거 가능한 카드/장비가 없습니다. 다른 플레이어를 선택해주세요.");
                }
            }
        }

        if (cardName.equals("게으름")) {
            if (!senderId.equals(targetId)) {
                logEvent("WARNING", roomId, senderId, null, "USE_TARGET_CARD", "card=" + cardName, "ONLY_SELF_TARGET_ALLOWED");
                throw new GameApiException(400, "게으름 카드는 자신에게만 사용할 수 있습니다.");
            }
        }

        Card usedCard = sender.hand.remove((int) cardIndex);

        String message;
        if (isCooperation) {
            if (senderId.equals(targetId)) {
                applySelfCooperationEffect(target, usedCard.name);
                message = target.name + "에게 " + usedCard.name + " 사용";
            } else if (target.sick) {
                message = sender.name + "이(가) " + target.name + "에게 " + usedCard.name + " 사용 → 질병으로 경험치 획득 불가";
            } else {
                boolean levelUpReady = addExperience(state, target, 1);
                message = sender.name + "이(가) " + target.name + "에게 " + usedCard.name + " 사용 → 경험치 +1";
                if (levelUpReady) message += " → 레벨업 조건 달성";
            }
        } else {
            message = applyTargetCardEffect(state, sender, target, usedCard);
        }

        sender.usedCardsThisTurn += 1;

        Card newCard = drawCard(state);
        if (newCard != null) {
            sender.hand.add(newCard);
            message += " 새 카드를 뽑았습니다.";
        } else {
            message += " 덱이 모두 소진되었습니다.";
        }

        logEvent("INFO", roomId, senderId, targetId, "USE_TARGET_CARD", "card=" + cardName + " | result=" + message, null);
        state.message = message;

        return okWithState(state, senderId);
    }

    public Map<String, Object> useCooperationCard(String roomId, Integer senderId, Integer targetId, Integer cardIndex) {
        GameState state = getStateOrThrow(roomId);
        return resolveTargetCardUse(state, roomId, senderId, targetId, cardIndex, List.of("협력", "딜레마"));
    }

    public Map<String, Object> useTargetCard(String roomId, Integer senderId, Integer targetId, Integer cardIndex) {
        GameState state = getStateOrThrow(roomId);

        Player sender = findPlayer(state, senderId);
        if (sender != null && sender.eliminated) {
            throw new GameApiException(400, "탈락한 플레이어는 행동할 수 없습니다.");
        }

        return resolveTargetCardUse(state, roomId, senderId, targetId, cardIndex, List.of("협력", "딜레마"));
    }

    // ===================== 일반(비대상) 카드 사용 =====================

    public Map<String, Object> useCard(String roomId, Integer playerId, Integer cardIndex) {
        GameState state = getStateOrThrow(roomId);

        Player player = findPlayer(state, playerId);
        if (player == null) {
            throw new GameApiException(404, "플레이어를 찾을 수 없습니다.");
        }

        if (player.eliminated) {
            throw new GameApiException(400, "탈락한 플레이어는 행동할 수 없습니다.");
        }

        if (player.turnEnded) {
            throw new GameApiException(400, "이미 턴을 종료했습니다.");
        }

        if (player.usedCardsThisTurn >= CardCatalog.MAX_CARDS_PER_TURN) {
            throw new GameApiException(400, "한 턴에 최대 3장까지 사용할 수 있습니다.");
        }

        if (cardIndex == null || cardIndex < 0 || cardIndex >= player.hand.size()) {
            throw new GameApiException(400, "잘못된 카드입니다.");
        }

        Card usedCard = player.hand.remove((int) cardIndex);
        String message = applyCardEffect(state, player, usedCard);

        player.usedCardsThisTurn += 1;

        Card newCard = drawCard(state);
        if (newCard != null) {
            player.hand.add(newCard);
            message += " 새 카드 1장을 뽑았습니다.";
        } else {
            message += " 덱이 모두 소진되었습니다.";
        }

        state.message = "플레이어 " + playerId + ": " + usedCard.name + " 사용 - " + message;

        return okWithState(state, playerId);
    }

    // ===================== 턴 종료 / 라운드 진행 =====================

    public Map<String, Object> endTurn(String roomId, Integer playerId) {
        GameState state = getStateOrThrow(roomId);

        Player player = findPlayer(state, playerId);
        if (player == null) {
            logEvent("WARNING", roomId, playerId, null, "TURN_END", null, "PLAYER_NOT_FOUND");
            throw new GameApiException(404, "플레이어를 찾을 수 없습니다.");
        }

        if (player.eliminated) {
            throw new GameApiException(400, "탈락한 플레이어는 행동할 수 없습니다.");
        }

        boolean deckEmpty = state.deck.isEmpty();
        boolean handEmpty = player.hand.isEmpty();
        List<Player> connectedPlayers = getActivePlayers(state);
        boolean hasEmptyHandPlayer = connectedPlayers.stream().anyMatch(p -> p.hand.isEmpty());
        boolean hasCardsPlayer = connectedPlayers.stream().anyMatch(p -> !p.hand.isEmpty());

        boolean canEndTurn = false;
        String errorMsg = "";

        if (handEmpty) {
            canEndTurn = true;
        } else if (deckEmpty) {
            if (hasEmptyHandPlayer && hasCardsPlayer) {
                canEndTurn = false;
                errorMsg = "덱이 소진되었고 손패가 떨어진 플레이어가 있으므로, 남은 카드를 모두 사용해야 턴을 종료할 수 있습니다.";
            } else if (player.usedCardsThisTurn >= 1) {
                canEndTurn = true;
            } else {
                errorMsg = "최소 1장의 카드를 사용해야 합니다.";
            }
        } else {
            if (player.usedCardsThisTurn >= CardCatalog.MIN_CARDS_PER_TURN) {
                canEndTurn = true;
            } else {
                errorMsg = "최소 " + CardCatalog.MIN_CARDS_PER_TURN + "장의 카드를 사용해야 합니다.";
            }
        }

        if (!canEndTurn) {
            logEvent("WARNING", roomId, playerId, null, "TURN_END", null, errorMsg);
            throw new GameApiException(400, errorMsg);
        }

        player.turnEnded = true;
        logEvent("INFO", roomId, playerId, null, "TURN_END", null, null);

        boolean allPlayersEnded = connectedPlayers.stream().allMatch(p -> p.turnEnded);

        if (!allPlayersEnded) {
            state.message = "플레이어 " + playerId + "가 턴을 종료했습니다.";
            return turnResult(state, playerId, true, false);
        }

        if (!state.deck.isEmpty()) {
            startNewTurnCycle(state);
            state.message = state.round + "라운드를 계속합니다.";
            logEvent("INFO", roomId, null, null, "EVENT_NEW_TURN_CYCLE", "round=" + state.round, null);
            return turnResult(state, playerId, false, false);
        }

        boolean hasRemainingHand = connectedPlayers.stream().anyMatch(p -> !p.hand.isEmpty());

        if (hasRemainingHand) {
            startNewTurnCycle(state);
            state.message = "덱이 소진되었습니다. 남은 손패를 모두 사용할 때까지 " + state.round + "라운드를 계속합니다.";
            logEvent("INFO", roomId, null, null, "EVENT_NEW_TURN_CYCLE_DECK_EMPTY", "round=" + state.round, null);
            return turnResult(state, playerId, false, false);
        }

        int currentRound = state.round;

        if (currentRound >= state.maxRounds) {
            int requiredLevel = currentRound + 1;
            List<String> eliminatedNow = new ArrayList<>();
            for (Player p : getActivePlayers(state)) {
                if (p.level < requiredLevel) {
                    p.eliminated = true;
                    eliminatedNow.add(p.name);
                    logEvent("INFO", roomId, p.id, null, "PLAYER_ELIMINATED",
                            "required_level=" + requiredLevel + " level=" + p.level, null);
                }
            }

            state.gameOver = true;
            state.message = eliminatedNow.isEmpty()
                    ? state.maxRounds + "라운드가 종료되었습니다. 보스전입니다."
                    : state.maxRounds + "라운드가 종료되었습니다. 보스전입니다. (탈락: " + String.join(", ", eliminatedNow) + ")";
            logEvent("INFO", roomId, null, null, "EVENT_BOSS_BATTLE_START", "round=" + currentRound, null);
            return turnResult(state, playerId, false, true);
        }

        int nextRound = currentRound + 1;
        int requiredLevel = nextRound;
        List<String> eliminatedNow = new ArrayList<>();
        for (Player p : getActivePlayers(state)) {
            if (p.level < requiredLevel) {
                p.eliminated = true;
                eliminatedNow.add(p.name);
                logEvent("INFO", roomId, p.id, null, "PLAYER_ELIMINATED",
                        "round=" + nextRound + " required_level=" + requiredLevel + " level=" + p.level, null);
            }
        }

        if (getActivePlayers(state).isEmpty()) {
            state.gameOver = true;
            state.message = "모든 플레이어가 레벨 미달로 탈락했습니다. (탈락: " + String.join(", ", eliminatedNow) + ")";
            logEvent("INFO", roomId, null, null, "EVENT_ALL_PLAYERS_ELIMINATED", "round=" + nextRound, null);
            return turnResult(state, playerId, false, true);
        }

        state.round = nextRound;
        state.deck = createDeck();
        startNewTurnCycle(state);
        refillHands(state, 5);
        state.message = eliminatedNow.isEmpty()
                ? state.round + "라운드가 시작되었습니다."
                : state.round + "라운드가 시작되었습니다. (탈락: " + String.join(", ", eliminatedNow) + ")";
        logEvent("INFO", roomId, null, null, "EVENT_NEXT_ROUND", "round=" + state.round, null);
        return turnResult(state, playerId, false, false);
    }

    private Map<String, Object> turnResult(GameState state, Integer playerId, boolean waiting, boolean bossBattle) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("waiting", waiting);
        body.put("boss_battle", bossBattle);
        body.put("state", getPublicState(state, playerId));
        return body;
    }

    // ===================== 보스전 =====================

    private double round2(double v) {
        return Math.floor(v * 100 + 0.5) / 100;
    }

    private List<BossPartyMember> getPartyByPosition(GameState state) {
        List<BossPartyMember> alive = new ArrayList<>();
        for (BossPartyMember p : state.bossParty) {
            if (p.hp > 0 && p.mp > 0) alive.add(p);
        }

        List<BossPartyMember> sorted = new ArrayList<>();
        for (String pos : CardCatalog.POS_ORDER) {
            for (BossPartyMember p : alive) {
                if (p.pos.equals(pos)) sorted.add(p);
            }
        }
        return sorted;
    }

    private void supportAction(BossPartyMember supporter, GameState state, List<String> log) {
        if (supporter.hp <= 0 || supporter.mp <= 0 || supporter.stunned) return;

        int dice = random.nextInt(6) + 1;
        log.add(supporter.name + "이(가) 서포트 주사위: " + dice);

        BossBuffs buffs = state.bossBuffs;

        if (dice == 1) {
            buffs.dmgReduce = supporter.intel * 4;
            log.add("→ 버프: 다음 보스 공격 피해 " + fmt(buffs.dmgReduce) + " 감소");
        } else if (dice == 2) {
            buffs.atkBuff = supporter.intel / 6.0;
            log.add(String.format("→ 버프: 다음 턴 모든 팀원 공격력 +%.2f", buffs.atkBuff));
        } else if (dice == 3) {
            buffs.noStun = true;
            log.add("→ 버프: 다음 보스의 상태이상 면역");
        } else if (dice == 4) {
            for (BossPartyMember p : state.bossParty) {
                if (p.hp > 0 && p.mp > 0) p.mp += 1;
            }
            log.add("→ 모든 팀원 정신력 +1");
        } else if (dice == 5) {
            buffs.healBoost = 1 + (supporter.intel / 10.0);
            log.add(String.format("→ 버프: 이번 턴 힐량 %.2f배", buffs.healBoost));
        } else {
            double inc = supporter.intel / 10.0;
            for (BossPartyMember p : state.bossParty) {
                if (p.hp > 0 && p.mp > 0) {
                    p.maxHp = round2(p.maxHp + inc);
                    p.atk = round2(p.atk + inc);
                    p.hp = Math.min(p.maxHp, round2(p.hp + inc));
                }
            }
            log.add(String.format("→ 영구 버프: 모든 팀원 최대체력 +%.2f, 공격력 +%.2f, 즉시 힐 +%.2f", inc, inc, inc));
        }
    }

    private void playerAttack(BossPartyMember player, GameState state, List<String> log) {
        if (player.hp <= 0 || player.mp <= 0 || player.stunned) {
            log.add(player.name + " 행동 불가");
            return;
        }

        double dmg = 0;

        if (player.pos.equals("탱커") || player.pos.equals("서포터") || player.pos.equals("힐러")) {
            dmg = player.atk * 0.5;
        } else if (player.pos.equals("딜러")) {
            int dice = random.nextInt(6) + 1;
            if (dice == 6) {
                dmg = player.atk * (1 + player.intel / 24.0);
                log.add(player.name + "이(가) 딜러 주사위 6! (지능 보정)");
            } else {
                dmg = player.atk;
                log.add(player.name + "이(가) 딜러 주사위 " + dice);
            }
        }

        if (state.bossBuffs.atkBuff != null) {
            dmg += state.bossBuffs.atkBuff;
        }

        dmg = round2(dmg);
        state.boss.hp = round2(Math.max(0, state.boss.hp - dmg));

        log.add(String.format("%s(%s) 공격: %.2f 피해 → 보스 HP %.2f", player.name, player.pos, dmg, state.boss.hp));

        if (player.pos.equals("서포터")) {
            supportAction(player, state, log);
        }
    }

    private void healerAction(BossPartyMember healer, GameState state, List<String> log) {
        if (healer.hp <= 0 || healer.mp <= 0 || healer.stunned) {
            log.add(healer.name + " 행동 불가");
            return;
        }

        double healAmount = healer.intel * 0.2;
        if (state.bossBuffs.healBoost != null) {
            healAmount *= state.bossBuffs.healBoost;
        }
        healAmount = round2(healAmount);

        List<BossPartyMember> targets = new ArrayList<>();
        for (BossPartyMember p : state.bossParty) {
            if (p.hp > 0 && p.mp > 0) targets.add(p);
        }

        for (BossPartyMember target : targets) {
            double old = target.hp;
            target.hp = Math.min(target.maxHp, round2(target.hp + healAmount));
            log.add(String.format("%s 힐: %s HP %.2f → %.2f (힐량 %.2f)", healer.name, target.name, old, target.hp, healAmount));
        }
    }

    private void bossAction(GameState state, List<String> log) {
        Boss boss = state.boss;
        int dice = random.nextInt(6) + 1;
        log.add("보스 주사위: " + dice);

        double dmgReduce = state.bossBuffs.dmgReduce != null ? state.bossBuffs.dmgReduce : 0;

        if (dice == 1) {
            for (BossPartyMember p : getPartyByPosition(state)) {
                double dmg = boss.atk * 0.5;
                if (p.pos.equals("탱커")) {
                    dmg = dmg / 2;
                    int tDice = random.nextInt(6) + 1;
                    if (tDice == 6) {
                        dmg = Math.max(0, dmg - (p.intel / 2.0));
                        log.add(p.name + "이(가) 탱커 보호 주사위 6! 추가 피해 감소 " + fmt(p.intel / 2.0));
                    }
                }
                dmg = Math.max(0, dmg - dmgReduce);
                dmg = round2(dmg);
                p.hp = round2(p.hp - dmg);
                log.add(String.format("%s이(가) 광역피해 %.2f (HP %.2f)", p.name, dmg, p.hp));
            }
        } else if (dice == 2) {
            List<BossPartyMember> frontParty = getPartyByPosition(state);
            if (!frontParty.isEmpty()) {
                BossPartyMember front = frontParty.get(0);
                double dmg = boss.atk;
                if (front.pos.equals("탱커")) {
                    int tDice = random.nextInt(6) + 1;
                    if (tDice == 6) {
                        dmg = Math.max(0, dmg - (front.intel / 2.0));
                    }
                    dmg = dmg / 2;
                }
                dmg = Math.max(0, dmg - dmgReduce);
                dmg = round2(dmg);
                front.hp = round2(front.hp - dmg);
                log.add(String.format("%s이(가) 단일공격 %.2f 피해 (HP %.2f)", front.name, dmg, front.hp));
            }
        } else if (dice == 3) {
            List<BossPartyMember> sortedParty = getPartyByPosition(state);

            for (BossPartyMember p : sortedParty) {
                double dmg = boss.atk * 0.3;
                if (p.pos.equals("탱커")) {
                    dmg = dmg / 2;
                    int tDice = random.nextInt(6) + 1;
                    if (tDice == 6) {
                        dmg = Math.max(0, dmg - (p.intel / 2.0));
                        log.add(p.name + " 탱커 주사위 6 (광역): " + fmt(p.intel / 2.0) + " 감소");
                    }
                }
                dmg = Math.max(0, dmg - dmgReduce);
                dmg = round2(dmg);
                p.hp = round2(p.hp - dmg);
                log.add(String.format("%s 광역 %.2f 피해 (HP %.2f)", p.name, dmg, p.hp));
            }

            if (!sortedParty.isEmpty()) {
                BossPartyMember front2 = sortedParty.get(0);
                double dmg2 = boss.atk * 0.7;
                if (front2.pos.equals("탱커")) {
                    int tDice = random.nextInt(6) + 1;
                    if (tDice == 6) {
                        dmg2 = Math.max(0, dmg2 - (front2.intel / 2.0));
                        log.add(front2.name + " 탱커 주사위 6 (추가단일): " + fmt(front2.intel / 2.0) + " 감소");
                    }
                    dmg2 = dmg2 / 2;
                }
                dmg2 = Math.max(0, dmg2 - dmgReduce);
                dmg2 = round2(dmg2);
                front2.hp = round2(front2.hp - dmg2);
                log.add(String.format("%s 추가 단일 %.2f 피해 (HP %.2f)", front2.name, dmg2, front2.hp));
            }
        } else if (dice == 4) {
            if (Boolean.TRUE.equals(state.bossBuffs.noStun)) {
                log.add("버프로 상태이상 무효화");
            } else {
                List<BossPartyMember> backParty = getPartyByPosition(state);
                if (!backParty.isEmpty()) {
                    BossPartyMember back = backParty.get(backParty.size() - 1);
                    back.stunned = true;
                    log.add(back.name + "이(가) 상태이상에 걸려 다음턴 행동 불가");
                }
            }
        } else if (dice == 5) {
            if (boss.summons >= 2) {
                log.add("이미 친구 2명 존재, 소환 실패");
            } else {
                boss.summons += 1;
                log.add("보스 친구 소환! 현재 " + boss.summons + "명 (다음턴부터 공격 시작, 친구 피해: 보스ATK*0.5)");
            }
        } else {
            for (BossPartyMember p : getPartyByPosition(state)) {
                p.mp = Math.max(0, p.mp - 1);
                log.add(p.name + " 정신력 -1 (현재 " + fmt(p.mp) + ")");
            }
        }
    }

    private void friendsAttack(GameState state, List<String> log) {
        List<BossPartyMember> alive = new ArrayList<>();
        for (BossPartyMember p : state.bossParty) {
            if (p.hp > 0 && p.mp > 0) alive.add(p);
        }

        for (int i = 0; i < state.boss.summons; i++) {
            if (alive.isEmpty()) break;
            BossPartyMember target = alive.get(random.nextInt(alive.size()));
            double dmg = round2(state.boss.atk * 0.5);
            target.hp = round2(target.hp - dmg);
            log.add(String.format("보스 친구가 %s에게 %.2f 피해 (HP %.2f)", target.name, dmg, target.hp));
        }
    }

    private boolean isPartyDead(GameState state) {
        for (BossPartyMember p : state.bossParty) {
            if (!(p.hp <= 0 || p.mp <= 0)) return false;
        }
        return true;
    }

    private String doBossTurn(GameState state) {
        List<String> log = state.bossLog;

        log.add("\n=== 턴 " + state.bossTurn + " 시작 ===");

        state.bossBuffs = new BossBuffs();

        List<BossPartyMember> sortedParty = new ArrayList<>();
        for (String pos : CardCatalog.POS_ORDER) {
            for (BossPartyMember p : state.bossParty) {
                if (p.pos.equals(pos)) sortedParty.add(p);
            }
        }

        for (BossPartyMember p : sortedParty) {
            if (state.boss.hp <= 0) break;
            if (p.hp <= 0 || p.mp <= 0) continue;

            if (p.stunned) {
                log.add(p.name + "은(는) 기절로 이번 턴 행동불가");
                p.stunned = false;
                continue;
            }

            if (p.pos.equals("힐러")) {
                healerAction(p, state, log);
            }

            playerAttack(p, state, log);
        }

        if (state.boss.hp <= 0) {
            log.add("보스 HP 0 이하 — 승리!");
            return "win";
        }

        bossAction(state, log);

        if (state.boss.summons > 0) {
            friendsAttack(state, log);
        }

        if (isPartyDead(state)) {
            log.add("모든 팀원 사망 또는 정신력 0 — 패배");
            return "lose";
        }

        state.boss.hp = round2(state.boss.hp);
        for (BossPartyMember p : state.bossParty) {
            p.hp = round2(p.hp);
            p.atk = round2(p.atk);
            p.maxHp = round2(p.maxHp);
        }

        state.bossTurn += 1;

        return "continue";
    }

    public Map<String, Object> startBossBattle(String roomId, Integer playerId) {
        GameState state = getStateOrThrow(roomId);

        if (!state.gameOver) {
            throw new GameApiException(400, "아직 보스전을 시작할 수 없습니다.");
        }

        if (state.bossActive) {
            return okWithState(state, playerId);
        }

        List<BossPartyMember> bossParty = new ArrayList<>();
        for (Player player : state.players) {
            if (!player.connected || player.eliminated) continue;

            BossPartyMember member = new BossPartyMember();
            member.playerId = player.id;
            member.name = player.name;
            member.pos = (player.job != null) ? player.job : "탱커";
            member.maxHp = player.stats.get("hp");
            member.hp = player.stats.get("hp");
            member.atk = player.stats.get("attack");
            member.intel = player.stats.get("intelligence");
            member.mp = player.stats.get("mental");
            member.stunned = false;
            member.initialMaxHp = player.stats.get("hp");
            member.initialMaxMp = player.stats.get("mental");

            bossParty.add(member);
        }

        if (bossParty.isEmpty()) {
            state.boss = new Boss(0, 0, 0);
            state.bossParty = new ArrayList<>();
            state.bossTurn = 1;
            state.bossActive = true;
            state.bossResult = "lose";
            state.bossLog = new ArrayList<>(List.of("모든 플레이어가 레벨 미달로 탈락하여 보스전에 참여할 인원이 없습니다."));
            state.message = "모든 플레이어가 레벨 미달로 탈락하여 보스전에서 패배했습니다.";
            recordBattleIfFinished(roomId, state);
            return okWithState(state, playerId);
        }

        double bossHp = 52;
        double bossAtk = 10;

        state.boss = new Boss(bossHp, bossAtk, 0);
        state.bossParty = bossParty;
        state.bossTurn = 1;
        state.bossBuffs = new BossBuffs();
        state.bossActive = true;
        state.bossResult = null;
        state.bossLog = new ArrayList<>(List.of(
                String.format("전투 시작 — 보스 HP %.2f, ATK %.2f / 파티 인원 %d", bossHp, bossAtk, bossParty.size())
        ));

        return okWithState(state, playerId);
    }

    public Map<String, Object> advanceBossTurn(String roomId, Integer playerId) {
        GameState state = getStateOrThrow(roomId);

        if (!state.bossActive) {
            throw new GameApiException(400, "보스전이 시작되지 않았습니다.");
        }

        if (state.bossResult != null) {
            return okWithState(state, playerId);
        }

        String result = doBossTurn(state);

        if (!result.equals("continue")) {
            state.bossResult = result;
            recordBattleIfFinished(roomId, state);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("result", result);
        body.put("state", getPublicState(state, playerId));
        return body;
    }
}
