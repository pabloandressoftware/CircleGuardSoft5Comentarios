package com.circleguard.promotion.controller;

import com.circleguard.promotion.dto.AccessPointDTO;
import com.circleguard.promotion.dto.BuildingDTO;
import com.circleguard.promotion.dto.FloorDTO;
import com.circleguard.promotion.model.AccessPoint;
import com.circleguard.promotion.model.Building;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.model.jpa.SystemSettings;
import com.circleguard.promotion.repository.graph.UserNodeRepository;
import com.circleguard.promotion.repository.jpa.SystemSettingsRepository;
import com.circleguard.promotion.service.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Los controladores traducen DTO <-> servicio; la seguridad se prueba en el gateway (HU-13). */
class PromotionControllersTest {

    private final UUID id = UUID.randomUUID();

    @Test
    void buildingController() {
        BuildingService buildings = mock(BuildingService.class);
        FloorService floors = mock(FloorService.class);
        BuildingController c = new BuildingController(buildings, floors);
        Building b = Building.builder().id(id).name("Torre").code("T").build();
        Floor f = Floor.builder().id(UUID.randomUUID()).building(b).floorNumber(1).name("P1").build();
        BuildingDTO req = BuildingDTO.builder().name("Torre").code("T").build();
        when(buildings.createBuilding(any(), any(), any(), any(), any(), any())).thenReturn(b);
        when(buildings.updateBuilding(eq(id), any(), any(), any(), any(), any(), any())).thenReturn(b);
        when(buildings.getAllBuildings()).thenReturn(List.of(b));
        when(floors.getFloorsByBuilding(id)).thenReturn(List.of(f));
        when(floors.addFloor(eq(id), any(), any())).thenReturn(f);

        assertEquals("Torre", c.createBuilding(req).getBody().getName());
        assertEquals("T", c.updateBuilding(id, req).getBody().getCode());
        assertEquals(1, c.listBuildings().getBody().size());
        assertEquals(id, c.getFloors(id).getBody().get(0).getBuildingId());
        assertEquals("P1", c.addFloor(id, FloorDTO.builder().floorNumber(1).name("P1").build()).getBody().getName());
        assertEquals(200, c.deleteBuilding(id).getStatusCode().value());
        verify(buildings).deleteBuilding(id);
    }

    @Test
    void floorAndAccessPointControllers() {
        FloorService floors = mock(FloorService.class);
        AccessPointService aps = mock(AccessPointService.class);
        Floor f = Floor.builder().id(id).floorNumber(2).name("P2").build();
        AccessPoint ap = AccessPoint.builder().id(UUID.randomUUID()).floor(f).macAddress("aa").name("AP").build();
        AccessPointDTO req = AccessPointDTO.builder().macAddress("aa").coordinateX(1.0).coordinateY(2.0).name("AP").build();
        when(aps.registerAccessPoint(eq(id), any(), any(), any(), any())).thenReturn(ap);
        when(aps.getAccessPointsByFloor(id)).thenReturn(List.of(ap));
        when(aps.updateAccessPoint(eq(ap.getId()), any(), any(), any(), any())).thenReturn(ap);
        when(aps.getAccessPoint(ap.getId())).thenReturn(Optional.of(ap));
        when(aps.getAccessPoint(id)).thenReturn(Optional.empty());
        when(floors.updateFloor(eq(id), any(), any(), any())).thenReturn(f);

        FloorController fc = new FloorController(floors, aps);
        assertEquals("aa", fc.addAccessPoint(id, req).getBody().getMacAddress());
        assertEquals(1, fc.getAccessPoints(id).getBody().size());
        assertEquals("P2", fc.updateFloor(id, FloorDTO.builder().name("P2").build()).getBody().getName());

        AccessPointController ac = new AccessPointController(aps);
        assertEquals(id, ac.getAccessPoint(ap.getId()).getBody().getFloorId());
        assertEquals(404, ac.getAccessPoint(id).getStatusCode().value());
        assertEquals("AP", ac.updateAccessPoint(ap.getId(), req).getBody().getName());
        assertEquals(200, ac.deleteAccessPoint(ap.getId()).getStatusCode().value());
    }

    @Test
    void sessionHandshake() {
        MacSessionRegistry registry = mock(MacSessionRegistry.class);
        SessionHandshakeController c = new SessionHandshakeController(registry);
        assertEquals(400, c.handshake(Map.of("macAddress", "aa")).getStatusCode().value());
        assertEquals(200, c.handshake(Map.of("macAddress", "aa", "anonymousId", "u")).getStatusCode().value());
        verify(registry).registerSession("aa", "u");
        assertEquals(204, c.closeSession("aa").getStatusCode().value());
        verify(registry).closeSession("aa");
    }

    @Test
    void encounterController() {
        UserNodeRepository users = mock(UserNodeRepository.class);
        AutoCircleService auto = mock(AutoCircleService.class);
        EncounterController c = new EncounterController(users, auto);
        EncounterController.EncounterRequest r = new EncounterController.EncounterRequest();
        r.setSourceId("a");
        r.setTargetId("b");

        assertEquals(200, c.reportEncounter(r).getStatusCode().value());
        verify(users).recordEncounter(eq("a"), eq("b"), anyLong(), eq("mobile_ble")); // lugar por defecto
        verify(auto).evaluateEncounter("a", "b");
        c.toggleValidity(5L);
        c.forceFence(6L);
        verify(users).toggleEncounterValidity(5L);
        verify(users).forceEncounterFence(6L);
    }

    @Test
    void healthStatusController() {
        HealthStatusService svc = mock(HealthStatusService.class);
        HealthStatusController c = new HealthStatusController(svc);

        c.reportStatus(Map.of("anonymousId", "u", "status", "SUSPECT", "adminOverride", true));
        verify(svc).updateStatus("u", "SUSPECT", true);
        c.reportStatus(Map.of("anonymousId", "u", "status", "SUSPECT"));
        verify(svc).updateStatus("u", "SUSPECT", false);
        c.confirmPositive(Map.of("anonymousId", "u"));
        verify(svc).updateStatus("u", "CONFIRMED");
        c.recover("u");
        verify(svc).promoteToRecovered("u");
        c.resolve(Map.of("anonymousId", "u", "adminOverride", true));
        verify(svc).resolveStatus("u", true);
    }

    @Test
    void adminSettingsMergeOnlyProvidedFieldsAndCreateDefaults() {
        SystemSettingsRepository repo = mock(SystemSettingsRepository.class);
        when(repo.save(any(SystemSettings.class))).thenAnswer(i -> i.getArgument(0));
        AdminController c = new AdminController(repo);

        when(repo.getSettings()).thenReturn(Optional.empty());
        SystemSettings defaults = c.getSettings().getBody();
        assertEquals(3600L, defaults.getAutoThresholdSeconds());
        assertEquals(14, defaults.getMandatoryFenceDays());

        when(repo.getSettings()).thenReturn(Optional.of(defaults));
        SystemSettings patch = new SystemSettings();
        patch.setMandatoryFenceDays(7);
        SystemSettings updated = c.updateSettings(patch).getBody();
        assertEquals(7, updated.getMandatoryFenceDays());
        assertEquals(3600L, updated.getAutoThresholdSeconds()); // no se toca lo que no vino

        assertFalse(c.toggleUnconfirmedFencing(false).getBody().getUnconfirmedFencingEnabled());
    }
}
