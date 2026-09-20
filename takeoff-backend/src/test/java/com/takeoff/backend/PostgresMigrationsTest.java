package com.takeoff.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.TreeSet;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * The PostgreSQL migration set (used by the hosted demo) must describe the same schema as the MySQL set the app is
 * developed against. Neither can be run against a real server here, so both are applied to H2 (in its MySQL and
 * PostgreSQL modes) and their tables and columns are compared. A script added to one folder and forgotten in the other
 * fails this test.
 */
class PostgresMigrationsTest {

	private static final String MYSQL_URL = "jdbc:h2:mem:migration_mysql;MODE=MySQL;DB_CLOSE_DELAY=-1";
	private static final String POSTGRES_URL = "jdbc:h2:mem:migration_postgres;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

	private static void migrate(String url, String location) {
		Flyway.configure().dataSource(url, "sa", "").locations(location).load().migrate();
	}

	/** "table.column:NULLABLE|REQUIRED" for every column in the public schema, except Flyway's own table. */
	private static Set<String> columns(String url) throws SQLException {
		Set<String> found = new TreeSet<>();
		try (Connection connection = DriverManager.getConnection(url, "sa", "");
				ResultSet rs = connection.getMetaData().getColumns(null, connection.getSchema(), "%", "%")) { // "PUBLIC" or "public", by mode
			while (rs.next()) {
				String table = rs.getString("TABLE_NAME").toLowerCase();
				if (table.equals("flyway_schema_history")) {
					continue;
				}
				found.add(table + "." + rs.getString("COLUMN_NAME").toLowerCase() + ":"
						+ (rs.getInt("NULLABLE") == java.sql.DatabaseMetaData.columnNoNulls ? "REQUIRED" : "NULLABLE"));
			}
		}
		return found;
	}

	@Test
	void thePostgresScriptsApplyCleanly() throws SQLException {
		migrate(POSTGRES_URL, "classpath:db/migration-postgresql");

		assertThat(columns(POSTGRES_URL).stream().map(c -> c.substring(0, c.indexOf('.'))).distinct())
			.containsExactlyInAnyOrder("users", "otp_tokens", "driver_applications", "application_documents", "notifications",
					"stored_files");
	}

	@Test
	void bothSetsProduceTheSameTablesColumnsAndNullability() throws SQLException {
		migrate(MYSQL_URL, "classpath:db/migration");
		migrate(POSTGRES_URL, "classpath:db/migration-postgresql");

		Set<String> mysql = columns(MYSQL_URL);
		Set<String> postgres = columns(POSTGRES_URL);

		assertThat(postgres).as("PostgreSQL columns").isEqualTo(mysql);
		assertThat(mysql).contains("users.must_change_password:REQUIRED", "users.temporary_password_expires_at:NULLABLE",
				"stored_files.content:REQUIRED", "driver_applications.version:REQUIRED");
	}

	@Test
	void bothSetsHaveTheSameVersionsSoNeitherFallsBehind() {
		Flyway mysql = Flyway.configure().dataSource(MYSQL_URL, "sa", "").locations("classpath:db/migration").load();
		Flyway postgres = Flyway.configure().dataSource(POSTGRES_URL, "sa", "").locations("classpath:db/migration-postgresql").load();

		var mysqlVersions = java.util.Arrays.stream(mysql.info().all()).map(i -> i.getVersion() + " " + i.getDescription()).toList();
		var postgresVersions = java.util.Arrays.stream(postgres.info().all()).map(i -> i.getVersion() + " " + i.getDescription()).toList();

		assertThat(postgresVersions).isEqualTo(mysqlVersions);
	}
}
