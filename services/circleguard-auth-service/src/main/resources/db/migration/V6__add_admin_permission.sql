-- HU-21: autoridad que exigen promotion-service en los endpoints espaciales
-- (AccessPointController, BuildingController, FloorController: hasAuthority('ADMIN')).
-- Sin esta migracion esos endpoints devolvian 403 para todos los usuarios.
INSERT INTO permissions (name, description) VALUES
('ADMIN', 'Gestionar edificios, pisos y puntos de acceso'),
('certificate:validate', 'Aprobar o rechazar certificados medicos'),
('questionnaire:manage', 'Crear y activar cuestionarios de salud')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'HEALTH_CENTER'
  AND p.name IN ('ADMIN', 'certificate:validate', 'questionnaire:manage')
ON CONFLICT (role_id, permission_id) DO NOTHING;
