package com.circleguard.promotion.service;

import com.circleguard.promotion.model.AccessPoint;
import com.circleguard.promotion.model.Building;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.repository.jpa.AccessPointRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class LocationAndSessionTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final SetOperations<String, String> sets = mock(SetOperations.class);

    // ---------- MacSessionRegistry ----------
    @Test
    void sessionRegistryNormalizesMacToLowerCase() {
        when(redis.opsForValue()).thenReturn(values);
        MacSessionRegistry registry = new MacSessionRegistry(redis);

        registry.registerSession("AA:BB", "anon");
        verify(values).set(eq("session:mac:aa:bb"), eq("anon"), any(Duration.class));

        when(values.get("session:mac:aa:bb")).thenReturn("anon");
        assertEquals("anon", registry.getAnonymousId("AA:BB"));
        registry.closeSession("AA:BB");
        verify(redis).delete("session:mac:aa:bb");
    }

    // ---------- LocationResolutionService ----------
    private AccessPoint accessPoint() {
        Building b = Building.builder().id(UUID.randomUUID()).build();
        Floor f = Floor.builder().id(UUID.randomUUID()).floorNumber(2).building(b).build();
        return AccessPoint.builder().id(UUID.randomUUID()).name("AP1").floor(f).coordinateX(1.0).coordinateY(2.0).build();
    }

    @Test
    void unknownAccessPointOrUnmappedDeviceEmitNothing() {
        AccessPointRepository aps = mock(AccessPointRepository.class);
        MacSessionRegistry sessions = mock(MacSessionRegistry.class);
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        LocationResolutionService service = new LocationResolutionService(aps, sessions, mock(GraphService.class), kafka, redis);

        when(aps.findByMacAddress("ap")).thenReturn(Optional.empty());
        service.processSignal("ap", "dev", -50.0);

        when(aps.findByMacAddress("ap")).thenReturn(Optional.of(accessPoint()));
        when(sessions.getAnonymousId("dev")).thenReturn(null);
        service.processSignal("ap", "dev", -50.0);

        verifyNoInteractions(kafka);
    }

    @Test
    void resolvedSignalEmitsEventRecordsEncountersAndTracksPresence() {
        AccessPointRepository aps = mock(AccessPointRepository.class);
        MacSessionRegistry sessions = mock(MacSessionRegistry.class);
        GraphService graph = mock(GraphService.class);
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        LocationResolutionService service = new LocationResolutionService(aps, sessions, graph, kafka, redis);
        AccessPoint ap = accessPoint();
        when(aps.findByMacAddress("ap")).thenReturn(Optional.of(ap));
        when(sessions.getAnonymousId("dev")).thenReturn("me");
        when(redis.opsForSet()).thenReturn(sets);
        when(sets.members(anyString())).thenReturn(Set.of("me", "other"));

        service.processSignal("ap", "dev", -60.0);

        verify(kafka).send(eq("proximity.detected"), eq("me"), any());
        verify(graph).recordEncounter("me", "other", ap.getId().toString());
        verify(graph, never()).recordEncounter(eq("me"), eq("me"), anyString());
        verify(sets).add(anyString(), eq("me"));
        verify(graph).detectAndFormCircles(ap.getId().toString());
    }
}
