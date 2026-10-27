package com.example.cardgame.battlelog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BattleRecordRepository extends JpaRepository<BattleRecord, Long> {

    List<BattleRecord> findAllByOrderByCreatedAtDesc();

    List<BattleRecord> findByRoomIdOrderByCreatedAtDesc(String roomId);

    List<BattleRecord> findByResultOrderByCreatedAtDesc(String result);

    long countByResult(String result);

    @Query("select coalesce(avg(r.bossTurns), 0) from BattleRecord r where r.playerCount > 0")
    double averageBossTurns();

    /** row = [job, 등장 횟수, 생존 횟수] */
    @Query("select p.job, count(p), coalesce(sum(case when p.alive = true then 1 else 0 end), 0) "
            + "from BattleParticipant p group by p.job order by p.job")
    List<Object[]> jobBreakdown();
}
