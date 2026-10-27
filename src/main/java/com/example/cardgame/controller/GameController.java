package com.example.cardgame.controller;

import com.example.cardgame.dto.*;
import com.example.cardgame.service.GameService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createGame(@RequestBody CreateGameRequest request) {
        return ResponseEntity.ok(gameService.createGame(request.playerCount, request.job));
    }

    @PostMapping("/join")
    public ResponseEntity<Map<String, Object>> joinGame(@RequestBody JoinGameRequest request) {
        return ResponseEntity.ok(gameService.joinGame(request.roomId, request.job));
    }

    @GetMapping("/state")
    public ResponseEntity<Map<String, Object>> getState(@RequestParam("room_id") String roomId,
                                                          @RequestParam(value = "player_id", required = false) Integer playerId) {
        return ResponseEntity.ok(gameService.getState(roomId, playerId));
    }

    @PostMapping("/card/cooperation")
    public ResponseEntity<Map<String, Object>> useCooperationCard(@RequestBody TargetCardRequest request) {
        return ResponseEntity.ok(gameService.useCooperationCard(
                request.roomId, request.senderId, request.targetId, request.cardIndex));
    }

    @PostMapping("/card/use")
    public ResponseEntity<Map<String, Object>> useCard(@RequestBody CardUseRequest request) {
        return ResponseEntity.ok(gameService.useCard(request.roomId, request.playerId, request.cardIndex));
    }

    @PostMapping("/card/target")
    public ResponseEntity<Map<String, Object>> useTargetCard(@RequestBody TargetCardRequest request) {
        return ResponseEntity.ok(gameService.useTargetCard(
                request.roomId, request.senderId, request.targetId, request.cardIndex));
    }

    @PostMapping("/turn/end")
    public ResponseEntity<Map<String, Object>> endTurn(@RequestBody EndTurnRequest request) {
        return ResponseEntity.ok(gameService.endTurn(request.roomId, request.playerId));
    }

    @PostMapping("/boss/start")
    public ResponseEntity<Map<String, Object>> startBossBattle(@RequestBody BossRequest request) {
        return ResponseEntity.ok(gameService.startBossBattle(request.roomId, request.playerId));
    }

    @PostMapping("/boss/turn")
    public ResponseEntity<Map<String, Object>> advanceBossTurn(@RequestBody BossRequest request) {
        return ResponseEntity.ok(gameService.advanceBossTurn(request.roomId, request.playerId));
    }
}
