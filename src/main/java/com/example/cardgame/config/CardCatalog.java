package com.example.cardgame.config;

import com.example.cardgame.model.CardDefinition;
import com.example.cardgame.model.EquipmentInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 게임 상수 및 카드/직업/장비 마스터 데이터
public final class CardCatalog {

    private CardCatalog() {
    }

    public static final int MAX_ROUNDS = 3;
    public static final int MIN_CARDS_PER_TURN = 2;
    public static final int MAX_CARDS_PER_TURN = 3;

    public static final List<String> JOBS = List.of("탱커", "딜러", "서포터", "힐러");

    public static final List<String> POS_ORDER = List.of("탱커", "딜러", "서포터", "힐러");

    public static final List<CardDefinition> CARD_DATA = List.of(
            new CardDefinition("덤벨", "성장", "공격력 +2", 2),
            new CardDefinition("검술훈련", "성장", "주사위 (1-3: 공격력+1, 4-6: 공격력+2)", 2),
            new CardDefinition("책읽기", "성장", "지능 +2", 2),
            new CardDefinition("마법연구", "성장", "주사위 (1-3: 지능+1, 4-6: 지능+2)", 2),
            new CardDefinition("수영", "성장", "체력 +3", 2),
            new CardDefinition("등산", "성장", "체력 +2", 2),
            new CardDefinition("검", "장비", "공격력 ×1.1", 1),
            new CardDefinition("가죽 갑옷", "장비", "방어력 ×1.05", 1),
            new CardDefinition("철 갑옷", "장비", "방어력 ×1.1", 1),
            new CardDefinition("금 갑옷", "장비", "방어력 ×1.15", 1),
            new CardDefinition("다이아 갑옷", "장비", "방어력 ×1.2", 1),
            new CardDefinition("은반지", "장비", "지력 ×1.05", 1),
            new CardDefinition("금반지", "장비", "지력 ×1.1", 1),
            new CardDefinition("전우의 격려", "협력", "자신: 체력+4 / 동료: 경험치+1", 3),
            new CardDefinition("마법 조언", "협력", "자신: 지력+4 / 동료: 경험치+1", 3),
            new CardDefinition("훈련 지원", "협력", "자신: 공격력+4 / 동료: 경험치+1", 3),
            new CardDefinition("칭찬", "협력", "자신: 정신력+1 / 동료: 경험치+1", 3),
            new CardDefinition("게으름", "딜레마", "손패 카드 1장 제거", 2),
            new CardDefinition("질병", "딜레마", "다음 턴 경험치 획득 불가", 1),
            new CardDefinition("질투", "딜레마", "다음 턴 자신에게 협력카드 사용 불가", 1),
            new CardDefinition("욕심", "딜레마", "다음 턴 동료에게 협력카드 사용 불가", 1),
            new CardDefinition("분열", "딜레마", "다음 턴 모든 플레이어 협력카드 사용 금지", 1),
            new CardDefinition("민폐", "딜레마", "다른 플레이어 손패/장비 1개 제거", 1),
            new CardDefinition("부상", "딜레마", "본인: 랜덤 스탯-4 / 타인: 랜덤 스탯-3", 1),
            new CardDefinition("인내심 폭발", "딜레마", "본인: 정신력-1 / 타인: 랜덤 스탯-1", 1)
    );

    public static final List<String> TARGETED_CARDS = List.of(
            "전우의 격려", "마법 조언", "훈련 지원", "칭찬", "게으름", "민폐", "부상", "인내심 폭발"
    );

    public static final Map<String, EquipmentInfo> EQUIPMENT_INFO = createEquipmentInfo();

    private static Map<String, EquipmentInfo> createEquipmentInfo() {
        Map<String, EquipmentInfo> map = new LinkedHashMap<>();
        map.put("검", new EquipmentInfo("무기", 1));
        map.put("가죽 갑옷", new EquipmentInfo("갑옷", 1));
        map.put("철 갑옷", new EquipmentInfo("갑옷", 2));
        map.put("금 갑옷", new EquipmentInfo("갑옷", 3));
        map.put("다이아 갑옷", new EquipmentInfo("갑옷", 4));
        map.put("은반지", new EquipmentInfo("반지", 1));
        map.put("금반지", new EquipmentInfo("반지", 2));
        return map;
    }
}
