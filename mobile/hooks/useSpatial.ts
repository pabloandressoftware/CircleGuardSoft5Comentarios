import { useState, useCallback } from 'react';
import { PROMOTION_BASE_URL, FILE_BASE_URL } from '../constants/Config';
import { authHeaders } from '../utils/apiAuth';

const API_BASE = `${PROMOTION_BASE_URL}/api/v1`;
const FILE_API_BASE = `${FILE_BASE_URL}/api/v1/files`;

export interface Building {
  id: string;
  name: string;
  code: string;
  description: string;
  latitude?: number;
  longitude?: number;
  address?: string;
}

export interface Floor {
  id: string;
  buildingId: string;
  floorNumber: number;
  name: string;
  floorPlanUrl?: string;
}

export interface AccessPoint {
  id?: string;
  macAddress: string;
  coordinateX: number;
  coordinateY: number;
  name: string;
  floorId: string;
}

async function jsonHeaders(): Promise<Record<string, string>> {
  return { 'Content-Type': 'application/json', ...(await authHeaders()) };
}

export const useSpatial = () => {
  const [loading, setLoading] = useState(false);

  const getBuildings = useCallback(async (): Promise<Building[]> => {
    setLoading(true);
    try {
      const resp = await fetch(`${API_BASE}/buildings`, { headers: await authHeaders() });
      if (!resp.ok) throw new Error(`No se pudieron cargar los edificios (${resp.status})`);
      return await resp.json();
    } finally {
      setLoading(false);
    }
  }, []);

  const createBuilding = async (data: Omit<Building, 'id'>) => {
    const resp = await fetch(`${API_BASE}/buildings`, {
      method: 'POST',
      headers: await jsonHeaders(),
      body: JSON.stringify(data),
    });
    return await resp.json();
  };

  const updateBuilding = async (id: string, data: Omit<Building, 'id'>) => {
    await fetch(`${API_BASE}/buildings/${id}`, {
      method: 'PUT',
      headers: await jsonHeaders(),
      body: JSON.stringify(data),
    });
  };

  const deleteBuilding = async (id: string) => {
    await fetch(`${API_BASE}/buildings/${id}`, {
      method: 'DELETE',
      headers: await authHeaders(),
    });
  };

  const getFloors = useCallback(async (buildingId: string): Promise<Floor[]> => {
    const resp = await fetch(`${API_BASE}/buildings/${buildingId}/floors`, {
      headers: await authHeaders(),
    });
    if (!resp.ok) throw new Error(`No se pudieron cargar los pisos (${resp.status})`);
    return await resp.json();
  }, []);

  const createFloor = async (buildingId: string, data: { floorNumber: number; name: string }) => {
    const resp = await fetch(`${API_BASE}/buildings/${buildingId}/floors`, {
      method: 'POST',
      headers: await jsonHeaders(),
      body: JSON.stringify(data),
    });
    return await resp.json();
  };

  const updateFloor = async (
    id: string,
    data: { floorNumber?: number; name?: string; floorPlanUrl?: string },
  ) => {
    const resp = await fetch(`${API_BASE}/floors/${id}`, {
      method: 'PUT',
      headers: await jsonHeaders(),
      body: JSON.stringify(data),
    });
    return await resp.json();
  };

  const uploadFloorPlan = async (floorId: string, file: any) => {
    const formData = new FormData();
    formData.append('file', file);

    // No fijar Content-Type: fetch/RN deben añadir el boundary del multipart.
    const resp = await fetch(`${FILE_API_BASE}/upload`, {
      method: 'POST',
      headers: await authHeaders(),
      body: formData,
    });
    if (!resp.ok) throw new Error(`No se pudo subir el plano (${resp.status})`);
    const { filename } = await resp.json();
    const url = `${FILE_API_BASE}/download/${filename}`;

    return await updateFloor(floorId, { floorPlanUrl: url });
  };

  const getAccessPoints = useCallback(async (floorId: string): Promise<AccessPoint[]> => {
    const resp = await fetch(`${API_BASE}/floors/${floorId}/access-points`, {
      headers: await authHeaders(),
    });
    if (!resp.ok) throw new Error(`No se pudieron cargar los puntos de acceso (${resp.status})`);
    return await resp.json();
  }, []);

  const saveAccessPoint = async (floorId: string, ap: AccessPoint) => {
    const method = ap.id ? 'PUT' : 'POST';
    const url = ap.id
      ? `${API_BASE}/access-points/${ap.id}`
      : `${API_BASE}/floors/${floorId}/access-points`;

    const resp = await fetch(url, {
      method,
      headers: await jsonHeaders(),
      body: JSON.stringify(ap),
    });
    return await resp.json();
  };

  const deleteAccessPoint = async (id: string) => {
    await fetch(`${API_BASE}/access-points/${id}`, {
      method: 'DELETE',
      headers: await authHeaders(),
    });
  };

  return {
    loading,
    getBuildings,
    createBuilding,
    updateBuilding,
    deleteBuilding,
    getFloors,
    createFloor,
    updateFloor,
    uploadFloorPlan,
    getAccessPoints,
    saveAccessPoint,
    deleteAccessPoint,
  };
};
