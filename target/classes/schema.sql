-- =============================================================================
-- ESQUEMA RELACIONAL NORMALIZADO: PROYECTO MINESENTINEL (POSTGRESQL / SUPABASE)
-- =============================================================================

-- 1. RolesPersonal
CREATE TABLE IF NOT EXISTS roles_personal (
    rol_id SERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE,
    color_casco VARCHAR(50),
    requiere_aforo BOOLEAN NOT NULL DEFAULT TRUE
);

-- 2. Turnos
CREATE TABLE IF NOT EXISTS turnos (
    turno_id SERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL
);

-- 3. CatalogoEPP
CREATE TABLE IF NOT EXISTS catalogo_epp (
    epp_id SERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE
);

-- 4. CatalogoAnomalias
CREATE TABLE IF NOT EXISTS catalogo_anomalias (
    catalogo_anomalia_id SERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE
);

-- 5. EventosTurno (Soporta múltiples registros 'ACTIVO' en paralelo por solapamiento de turnos)
CREATE TABLE IF NOT EXISTS eventos_turno (
    evento_id SERIAL PRIMARY KEY,
    turno_id INT NOT NULL REFERENCES turnos(turno_id) ON DELETE RESTRICT,
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    fecha_inicio TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_fin TIMESTAMP,
    CONSTRAINT chk_estado_turno CHECK (estado IN ('ACTIVO', 'CERRADO', 'CANCELADO'))
);

-- 6. MovimientosAforo (Registro append-only de entradas/salidas detectadas por ByteTrack)
CREATE TABLE IF NOT EXISTS movimientos_aforo (
    movimiento_id SERIAL PRIMARY KEY,
    evento_id INT NOT NULL REFERENCES eventos_turno(evento_id) ON DELETE CASCADE,
    rol_id INT NOT NULL REFERENCES roles_personal(rol_id) ON DELETE RESTRICT,
    tipo_movimiento VARCHAR(20) NOT NULL,
    fecha_hora TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_tipo_movimiento CHECK (tipo_movimiento IN ('ENTRADA', 'SALIDA'))
);

-- 7. AnomaliasMovimiento (Detecciones de comportamiento riesgoso o no autorizado)
CREATE TABLE IF NOT EXISTS anomalias_movimiento (
    anomalia_id SERIAL PRIMARY KEY,
    evento_id INT NOT NULL REFERENCES eventos_turno(evento_id) ON DELETE CASCADE,
    rol_id INT REFERENCES roles_personal(rol_id) ON DELETE SET NULL,
    catalogo_anomalia_id INT NOT NULL REFERENCES catalogo_anomalias(catalogo_anomalia_id) ON DELETE RESTRICT,
    nivel_confianza REAL NOT NULL,
    snapshot_url VARCHAR(500),
    estado_alerta VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE',
    fecha_hora TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_estado_anomalia CHECK (estado_alerta IN ('PENDIENTE', 'NOTIFICADA', 'REVISADA', 'DESCARTADA'))
);

-- 8. FaltasEPP (Incumplimientos de seguridad detectados por YOLOv8)
CREATE TABLE IF NOT EXISTS faltas_epp (
    falta_id SERIAL PRIMARY KEY,
    evento_id INT NOT NULL REFERENCES eventos_turno(evento_id) ON DELETE CASCADE,
    rol_id INT REFERENCES roles_personal(rol_id) ON DELETE SET NULL,
    epp_id INT NOT NULL REFERENCES catalogo_epp(epp_id) ON DELETE RESTRICT,
    nivel_confianza REAL NOT NULL,
    snapshot_url VARCHAR(500),
    estado_alerta VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE',
    fecha_hora TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_estado_falta CHECK (estado_alerta IN ('PENDIENTE', 'NOTIFICADA', 'REVISADA', 'DESCARTADA'))
);

-- 9. CierresAuditoriaTurno (Reconciliación y balance de personal al finalizar guardia)
CREATE TABLE IF NOT EXISTS cierres_auditoria_turno (
    auditoria_id SERIAL PRIMARY KEY,
    evento_id INT NOT NULL UNIQUE REFERENCES eventos_turno(evento_id) ON DELETE CASCADE,
    total_entradas INT NOT NULL,
    total_salidas INT NOT NULL,
    diferencia INT NOT NULL,
    fecha_hora TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 10. Usuarios (Gestión de credenciales administrativas y operadores)
CREATE TABLE IF NOT EXISTS usuarios (
    usuario_id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nombre_completo VARCHAR(150),
    rol VARCHAR(50) NOT NULL DEFAULT 'ADMIN',
    estado_activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- ÍNDICES ESTRATÉGICOS PARA ALTA VELOCIDAD DE CONSULTA Y AGREGACIONES
-- =============================================================================
CREATE INDEX IF NOT EXISTS idx_eventos_turno_estado ON eventos_turno(estado);
CREATE INDEX IF NOT EXISTS idx_movimientos_evento_tipo ON movimientos_aforo(evento_id, tipo_movimiento);
CREATE INDEX IF NOT EXISTS idx_movimientos_fecha_hora ON movimientos_aforo(fecha_hora DESC);
CREATE INDEX IF NOT EXISTS idx_faltas_epp_evento ON faltas_epp(evento_id);
CREATE INDEX IF NOT EXISTS idx_anomalias_evento ON anomalias_movimiento(evento_id);
CREATE INDEX IF NOT EXISTS idx_usuarios_username ON usuarios(username);

-- =============================================================================
-- DATOS SEMILLA (CATÁLOGOS BASE)
-- =============================================================================
INSERT INTO roles_personal (nombre, color_casco, requiere_aforo) VALUES
    ('Operador de Maquinaria / Perforista', 'Amarillo', TRUE),
    ('Supervisor de Seguridad / Ingeniero de Minas', 'Blanco', TRUE),
    ('Técnico Electricista / Mantenimiento', 'Azul', TRUE),
    ('Geólogo / Topógrafo', 'Verde', TRUE),
    ('Visitante Técnico / Auditor', 'Rojo', TRUE)
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO turnos (nombre, hora_inicio, hora_fin) VALUES
    ('Turno Guardia Día (Mañana)', '07:00:00', '15:30:00'),
    ('Turno Guardia Tarde', '15:00:00', '23:30:00'),
    ('Turno Guardia Noche', '23:00:00', '07:30:00')
ON CONFLICT DO NOTHING;

INSERT INTO catalogo_epp (nombre) VALUES
    ('Casco de Seguridad con Barbiquejo'),
    ('Chaleco Reflectivo de Alta Visibilidad'),
    ('Lámpara Minera Frontal'),
    ('Respirador contra Polvo / Vapores'),
    ('Botas de Seguridad con Puntera de Acero')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO catalogo_anomalias (nombre) VALUES
    ('Cruce en Sentido Contrario al Flujo'),
    ('Aglomeración en Boca-Mina (> 5 personas)'),
    ('Ingreso sin Autorización de Guardia'),
    ('Tiempo Excesivo en Zona Crítica de Tránsito'),
    ('Detección de Caída o Postura Anómala')
ON CONFLICT (nombre) DO NOTHING;
