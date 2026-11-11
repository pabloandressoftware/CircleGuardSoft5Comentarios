package com.circleguard.promotion.service;

import com.circleguard.promotion.model.AccessPoint;
import com.circleguard.promotion.model.Building;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.repository.jpa.AccessPointRepository;
import com.circleguard.promotion.repository.jpa.BuildingRepository;
import com.circleguard.promotion.repository.jpa.FloorRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Altas, cambios y bajas de edificios, pisos y puntos de acceso (HU-13). */
class SpatialServicesTest {

    private final BuildingRepository buildings = mock(BuildingRepository.class);
    private final FloorRepository floors = mock(FloorRepository.class);
    private final AccessPointRepository aps = mock(AccessPointRepository.class);
    private final UUID id = UUID.randomUUID();


    // ---------- BuildingService ----------
    @Test
    void buildingCrud() {
        BuildingService s = new BuildingService(buildings, floors);
        when(buildings.save(any(Building.class))).thenAnswer(i -> i.getArgument(0));
        Building b = s.createBuilding("Torre", "T1", "d", 1.0, 2.0, "Calle 1");
        assertEquals("T1", b.getCode());

        when(buildings.findById(id)).thenReturn(Optional.of(b));
        Building u = s.updateBuilding(id, "Nuevo", "T2", "x", 3.0, 4.0, "Calle 2");
        assertEquals("Nuevo", u.getName());
        assertEquals("Calle 2", u.getAddress());

        when(buildings.findAll()).thenReturn(List.of(b));
        assertEquals(1, s.getAllBuildings().size());

        when(floors.findByBuildingId(id)).thenReturn(List.of());
        s.deleteBuilding(id);
        verify(buildings).deleteById(id);
    }

    @Test
    void buildingWithFloorsCannotBeDeletedAndUnknownCannotBeUpdated() {
        BuildingService s = new BuildingService(buildings, floors);
        when(floors.findByBuildingId(id)).thenReturn(List.of(new Floor()));
        assertThrows(RuntimeException.class, () -> s.deleteBuilding(id));
        when(buildings.findById(id)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> s.updateBuilding(id, "n", "c", "d", 0.0, 0.0, "a"));
    }

    // ---------- FloorService ----------
    @Test
    void floorServiceAddsUpdatesAndGuardsDeletion() {
        FloorService s = new FloorService(buildings, floors, aps);
        Building b = Building.builder().name("T").build();
        when(buildings.findById(id)).thenReturn(Optional.of(b));
        when(floors.save(any(Floor.class))).thenAnswer(i -> i.getArgument(0));
        assertEquals(3, s.addFloor(id, 3, "Piso 3").getFloorNumber());

        Floor f = Floor.builder().floorNumber(1).name("a").build();
        when(floors.findById(id)).thenReturn(Optional.of(f));
        Floor u = s.updateFloor(id, null, "b", "http://plano");
        assertEquals(1, u.getFloorNumber());
        assertEquals("b", u.getName());
        assertEquals("http://plano", u.getFloorPlanUrl());

        when(floors.findByBuildingId(id)).thenReturn(List.of(f));
        assertEquals(1, s.getFloorsByBuilding(id).size());

        when(aps.findByFloorId(id)).thenReturn(List.of(new AccessPoint()));
        assertThrows(RuntimeException.class, () -> s.deleteFloor(id));
        when(aps.findByFloorId(id)).thenReturn(List.of());
        s.deleteFloor(id);
        verify(floors).deleteById(id);
    }

    @Test
    void floorServiceRejectsUnknownBuilding() {
        FloorService s = new FloorService(buildings, floors, aps);
        when(buildings.findById(id)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> s.addFloor(id, 1, "x"));
    }

    // ---------- AccessPointService ----------
    @Test
    void accessPointCrud() {
        AccessPointService s = new AccessPointService(aps, floors);
        when(floors.findById(id)).thenReturn(Optional.of(new Floor()));
        when(aps.save(any(AccessPoint.class))).thenAnswer(i -> i.getArgument(0));
        AccessPoint ap = s.registerAccessPoint(id, "aa:bb", 1.0, 2.0, "AP1");
        assertEquals("aa:bb", ap.getMacAddress());

        when(aps.findById(id)).thenReturn(Optional.of(ap));
        assertEquals("AP2", s.updateAccessPoint(id, "cc:dd", 5.0, 6.0, "AP2").getName());
        assertTrue(s.getAccessPoint(id).isPresent());

        when(aps.findByFloorId(id)).thenReturn(List.of(ap));
        assertEquals(1, s.getAccessPointsByFloor(id).size());
        s.deleteAccessPoint(id);
        verify(aps).deleteById(id);
    }

    @Test
    void accessPointErrorsOnUnknownFloorOrAp() {
        AccessPointService s = new AccessPointService(aps, floors);
        when(floors.findById(id)).thenReturn(Optional.empty());
        when(aps.findById(id)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> s.registerAccessPoint(id, "m", 0.0, 0.0, "n"));
        assertThrows(RuntimeException.class, () -> s.updateAccessPoint(id, "m", 0.0, 0.0, "n"));
    }

    // ---------- SpatialService ----------
    @Test
    void spatialServiceFacadeOperations() {
        SpatialService s = new SpatialService(buildings, floors, aps);
        when(buildings.save(any(Building.class))).thenAnswer(i -> i.getArgument(0));
        when(floors.save(any(Floor.class))).thenAnswer(i -> i.getArgument(0));
        when(aps.save(any(AccessPoint.class))).thenAnswer(i -> i.getArgument(0));

        Building b = s.createBuilding("T", "C", "d");
        when(buildings.findById(id)).thenReturn(Optional.of(b));
        assertEquals("N", s.updateBuilding(id, "N", "C2", "d2").getName());
        assertEquals(2, s.addFloor(id, 2, "P2").getFloorNumber());

        Floor f = new Floor();
        when(floors.findById(id)).thenReturn(Optional.of(f));
        assertEquals("P9", s.updateFloor(id, 9, "P9").getName());
        assertNotNull(s.registerAccessPoint(id, "m", 1.0, 1.0, "AP"));

        when(aps.findById(id)).thenReturn(Optional.of(new AccessPoint()));
        assertEquals("AP3", s.updateAccessPoint(id, "m", 1.0, 1.0, "AP3").getName());
        assertTrue(s.getAccessPoint(id).isPresent());

        when(buildings.findAll()).thenReturn(List.of(b));
        when(floors.findByBuildingId(id)).thenReturn(List.of());
        when(aps.findByFloorId(id)).thenReturn(List.of());
        assertEquals(1, s.getAllBuildings().size());
        assertTrue(s.getFloorsByBuilding(id).isEmpty());
        assertTrue(s.getAccessPointsByFloor(id).isEmpty());
        s.deleteBuilding(id);
        s.deleteFloor(id);
        s.deleteAccessPoint(id);
        verify(buildings).deleteById(id);
        verify(floors).deleteById(id);
        verify(aps).deleteById(id);
    }
}
