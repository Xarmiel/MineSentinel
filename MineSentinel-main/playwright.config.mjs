import { defineConfig } from '@playwright/test';

const baseURL = 'http://127.0.0.1:8080';
const testServerArguments = [
    '--server.port=8080',
    '--spring.profiles.active=test',
    '--spring.datasource.url=jdbc:h2:mem:minesentinel-e2e',
    '--spring.datasource.username=sa',
    '--spring.datasource.password=',
    '--spring.datasource.driver-class-name=org.h2.Driver',
    '--spring.jpa.database-platform=org.hibernate.dialect.H2Dialect',
    '--spring.jpa.hibernate.ddl-auto=create-drop',
    '--spring.sql.init.mode=never',
    '--spring.devtools.restart.enabled=false',
    '--minesentinel.browser.auto-launch=false',
    '--minesentinel.api.key=ci-test-key',
    '--minesentinel.api.key-requerida=true',
    '--minesentinel.admin.password=test-admin-password',
    '--minesentinel.supervisor.password=test-supervisor-password'
].join(' ');

export default defineConfig({
    testDir: './src/test/e2e',
    fullyParallel: false,
    reporter: 'list',
    use: {
        baseURL,
        browserName: 'chromium',
        headless: true,
        trace: 'retain-on-failure'
    },
    webServer: {
        command: `mvn -B spring-boot:run -Dspring-boot.run.useTestClasspath=true "-Dspring-boot.run.arguments=${testServerArguments}"`,
        url: `${baseURL}/login`,
        stdout: 'pipe',
        reuseExistingServer: false,
        timeout: 120_000
    }
});
