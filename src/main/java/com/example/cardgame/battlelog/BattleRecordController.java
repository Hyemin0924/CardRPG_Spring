package com.example.cardgame.battlelog;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 보스전 전투 기록 조회/삭제 API.
 * 기록 생성은 보스전이 끝나는 순간 서버가 자동으로 한다(GameService). 여기에 생성(POST) 경로는 없다.
 */
@RestController
@RequestMapping("/api/records")
public class BattleRecordController {

    private final BattleRecordService service;

    public BattleRecordController(BattleRecordService service) {
        this.service = service;
    }

    /** 전체 목록. ?room_id= 또는 ?result=win|lose 로 필터. 최신순 정렬. */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(value = "room_id", required = false) String roomId,
            @RequestParam(value = "result", required = false) String result) {
        List<BattleRecord> records = service.list(roomId, result);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("count", records.size());
        body.put("records", records);
        return ResponseEntity.ok(body);
    }

    /** 집계: 총 전투 수, 승/패, 승률, 평균 보스 턴, 직업별 등장/생존 수. */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("stats", service.stats());
        return ResponseEntity.ok(body);
    }

    /** 단건 조회 (참여자 상세 포함). */
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long id) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("record", service.get(id));
        return ResponseEntity.ok(body);
    }

    /** 단건 삭제. */
    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        service.delete(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "삭제되었습니다.");
        return ResponseEntity.ok(body);
    }
}
