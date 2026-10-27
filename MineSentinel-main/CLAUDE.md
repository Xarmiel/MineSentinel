# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Which copy is real

The git-tracked project is `MineSentinel-main/`. The parent directory (`../src`, `../pom.xml`, `../python_client`, run scripts) holds an **older, untracked copy** that has diverged (e.g. it still has `DeteccionEppService`, lacks `CuadroEppService`/`Vision*Service`). Edit only files under `MineSentinel-main/`.

Code, comments, identifiers, commit messages and UI text are in Spanish; keep it that way.

## Commands

Backend (Spring Boot 3.3.4, Java 17, Maven). Only `mvnw.cmd` exists (no `.mvn/` wrapper dir, no Unix `mvnw`), so on Linux use the system `mvn`:

```bash
mvn compile
mvn spring-boot:run                      # http://localhost:8080/login  (admin / sentinel123)
mvn test                                 # JUnit 5 + Mockito, all mocked, no DB needed
mvn test -Dtest=EventoTurnoServiceTest   # one class
mvn test -Dtest=EventoTurnoServiceTest#nombreDelMetodo   # one method
```

Windows equivalents: `run.cmd` / `run.ps1` (`mvnw.cmd compile -o` then `spring-boot:run`); `run_simulation.cmd` runs `simulate_sentinel.ps1`, a PowerShell event simulator.

The app connects to a remote Supabase PostgreSQL by default (`application.properties`, overridable via `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`). Schema is managed by `ddl-auto=update` (override with `SPRING_JPA_HIBERNATE_DDL_AUTO`); seed data comes from `DatabaseInitializerService` (a `CommandLineRunner`). `minesentinel.browser.auto-launch=true` opens a browser on startup.

Python vision client (`python_client/`):

```bash
pip install -r requirements.txt
python yolo_sentinel_client.py --mode simulation --interval 3        # no camera
python yolo_sentinel_client.py --mode webcam --src 0 --mostrar
python yolo_sentinel_client.py --mode video --src x.mp4 --dry-run    # no backend
python -m unittest tests.test_reglas_epp -v                          # run from python_client/
python -m unittest tests.test_ciclo_vida_epp -v
```

Do not run `unittest discover` from `python_client/`: `runs/` (Ultralytics output) makes discovery crawl.

## Architecture

Two processes talking over HTTP:

1. **Python vision client** (`python_client/`) — YOLOv8 + ByteTrack. `epp_detector.py` (`DetectorEPP`) evaluates EPP per tracked person; `yolo_sentinel_client.py` (`MineSentinelClient`, `PipelineEPP`, CLI) posts results to the backend; `motor_fluido.py` (`MotorDosHilos`) splits a video thread (camera fps, streams JPEG) from an inference thread (CPU-bound) sharing one locked state object. `epp_config.yaml` is the single source of truth for weights, class mapping, required EPP per role and "impostor" items; weight paths are relative to the YAML. EPP/anomaly IDs are **not** in the YAML — the client resolves them at startup from `GET /api/v1/catalogos/epp` and `/anomalias`. `verificable: false` in the config silently disables the "missing EPP" alert; `tests/test_reglas_epp.py` exists to guard that.
2. **Spring Boot backend** (`src/main/java/com/sentinelmine`) — controller → service → Spring Data JPA, Thymeleaf templates in `src/main/resources/templates` (dashboard, panel-admin, camara, historial, login).

Key cross-cutting ideas:

- **Aforo is an append-only ledger.** `MovimientoAforo` rows (ENTRADA/SALIDA, `trackId`) are never mutated; current occupancy is computed by filtered `COUNT` queries, only counting roles with `RolPersonal.requiereAforo = true`.
- **Overlapping shifts.** Several `EventoTurno` can be `ACTIVO` at once during handover. `EventoTurnoService.resolverEventoActivoParaMovimiento` assigns ENTRADA to the incoming shift and SALIDA to the outgoing one. `cerrarTurno` writes an immutable `CierreAuditoriaTurno` (entradas, salidas, diferencia).
- **Real-time push via SSE.** `SseNotificationService` broadcasts `aforo-update`, `alerta-nueva`, `turno-update` on `/api/v1/stream/eventos`; services call it after writes. Dashboards also poll as fallback.
- **Live vision state is in memory, not DB.** `VisionRestController` (`/api/v1/vision/*`) receives the per-frame EPP "cuadro" (`CuadroEppService`, expires after `minesentinel.cuadro.expiracion-segundos`), JPEG frames and an MJPEG stream (`VisionMarcoService`, `VisionStreamService`). Only infractions are persisted (`faltas_epp`, `anomalias_movimiento`) via `SeguridadInfraccionService`; infractions have `tipoInfraccion` FALTANTE vs IMPOSTOR and can be resolved via `/api/v1/seguridad/infracciones/resolucion`.
- **Evidence snapshots** are uploaded to `/api/v1/evidencias/upload` and stored on disk under `./uploads/snapshots/` (`SnapshotStorageService`).
- **Auth.** Session-based login (`AuthController`, BCrypt from `spring-security-crypto` only, no Spring Security filter chain). `AuthInterceptor` guards all web routes but `WebMvcConfig` excludes `/api/v1/**`, so the REST API used by the Python client is unauthenticated. Roles live in `usuarios.rol` (session attr `rol`): only `ADMIN` gets everything; `SUPERVISOR` (and any unknown role) is limited to `/`, `/camara`, `/historial/**`. Seeded users: `admin/sentinel123`, `supervisor/supervisor123`. Admin-only actions therefore belong under `/panel-admin/**`, never `/api/v1/**`.
- **Occupancy limits** (max/min) are editable from the admin panel and persisted in the single-row `configuracion_aforo` table; `AforoService` is the single source (`MovimientoAforoService` reads it), with `application.properties` as defaults.
- **Schema drift:** `ddl-auto=update` never alters existing CHECK constraints, so adding an enum value needs a fix like `EsquemaEstadoAlertaService` (rebuilds the `estado_alerta` CHECK at startup).
- Errors from REST controllers go through `GlobalExceptionHandler` → `ErrorResponseDTO`.
