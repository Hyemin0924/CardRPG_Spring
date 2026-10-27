package com.example.cardgame.model;

// 장비 슬롯/등급 정보 (같은 슬롯은 가장 좋은 등급 하나만 장착됨)
public class EquipmentInfo {
    public String slot;
    public int tier;

    public EquipmentInfo(String slot, int tier) {
        this.slot = slot;
        this.tier = tier;
    }
}
