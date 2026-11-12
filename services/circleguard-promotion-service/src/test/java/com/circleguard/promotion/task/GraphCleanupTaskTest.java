package com.circleguard.promotion.task;

import com.circleguard.promotion.repository.graph.UserNodeRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class GraphCleanupTaskTest {

    private final UserNodeRepository repo = mock(UserNodeRepository.class);
    private final GraphCleanupTask task = new GraphCleanupTask(repo);

    @Test
    void purgesEncountersOlderThanFourteenDays() {
        long before = System.currentTimeMillis();
        when(repo.purgeStaleEncounters(anyLong())).thenReturn(3L);

        task.purgeStaleEncounters();

        verify(repo).purgeStaleEncounters(longThat(t -> t <= before - 14L * 24 * 60 * 60 * 1000 + 5_000));
    }

    @Test
    void aFailingPurgeDoesNotBreakTheScheduler() {
        when(repo.purgeStaleEncounters(anyLong())).thenThrow(new RuntimeException("neo4j caido"));
        assertDoesNotThrow(task::purgeStaleEncounters);
    }
}
