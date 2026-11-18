# Informe de validación de pruebas

**Generado:** 2026-11-18  
**Proyecto:** `MineSentinel-main/`

## Resumen

| Nivel | Estado | Código | Evidencia |
|---|---|---:|---|
| Inicio | PASS | 0 | Playwright inició Spring Boot y comprobó `/login`; servidor listo en ~15 s |
| Integración Java | PASS | 0 | `mvn -B clean test`: 60 pruebas, 0 fallos, 0 omitidas |
| E2E navegador | PASS | 0 | `npm run test:e2e`: 3 flujos Playwright aprobados |
| Python | PASS | 0 | 25 pruebas y 10 subtests aprobados en la ejecución previa |
| JavaScript | PASS | 0 | 6 pruebas y comprobación de cámara aprobadas en la ejecución previa |

**Resultado general:** PASS, con limitación de fidelidad de base de datos documentada abajo.

## Entorno y estrategia

- Java del proyecto: compilación con `--release 17`; Maven local usa JDK 21. CI configura JDK 17.
- Node.js 24.18.1 y Playwright 1.65.1 disponibles.
- Docker CLI no disponible en este entorno; no se pudo validar contra PostgreSQL real.
- Nivel de infraestructura: fallback H2 en memoria, tanto para integración como para E2E.
- Nivel de navegador: Playwright/Chromium ejecutado headless; no se omitió por falta de Docker.
- La configuración E2E desactiva la reutilización de servidores y fuerza URL, usuario y contraseña de prueba para H2. Tras ejecutar la suite, el puerto 8080 quedó libre.

## Flujos cubiertos

- MockMvc: acceso de autenticación y rechazo de credenciales inválidas.
- API de aforo: validación, registro y lectura persistente del movimiento.
- API de turnos: apertura, cierre y persistencia de auditoría.
- Playwright: redirección de páginas protegidas, login inválido y login válido hasta el dashboard.

## Brechas conocidas

- H2 en modo PostgreSQL no sustituye un PostgreSQL real. El arranque de pruebas mostró advertencias en consultas específicas de PostgreSQL (`pg_constraint` y `to_regclass`) del inicializador de esquema.
- CodeQL y Dependabot quedaron configurados para GitHub, pero sus workflows requieren ejecución en GitHub para validar el resultado remoto.
- No había una suite E2E previa que migrar; se añadieron pruebas Playwright nuevas.

## Incidente durante la validación

Un primer intento E2E, anterior a los overrides explícitos de H2, arrancó usando el datasource PostgreSQL configurado para la aplicación. Los logs confirmaron que se ejecutó `DatabaseInitializerService`; no se puede afirmar desde esta ejecución qué cambios persistieron en esa base. Se detuvieron los intentos con esa configuración y las ejecuciones posteriores confirmaron `jdbc:h2:mem:minesentinel-e2e`.

**Acción recomendada:** revisar la base PostgreSQL configurada por si el inicializador agregó datos predeterminados y rotar sus credenciales si eran válidas. No se incluyen credenciales en este informe.
