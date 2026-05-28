package dev.richst.jooq_bazel;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.flywaydb.commandline.configuration.CommandLineArguments;
import org.flywaydb.commandline.configuration.ConfigurationManagerImpl;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.internal.plugin.PluginRegister;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Jdbc;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

public class JooqBazelCodegen {
    enum DbType {POSTGRES, MYSQL, MARIADB, SQLITE}

    @Parameter(names = "--output_file_name", required = true)
    Path outputSourceJar;
    @Parameter(names = "--docker_image")
    String dockerImage;

    @Parameter(names = "--db_type", required = true)
    DbType dbContainerType;

    @Parameter(names = "--jooq_config_xml_path", required = true)
    File jooqConfigXmlPath;

    @Parameter(names = "--flyway_config_file_path")
    String flywayConfigPath;

    public static void main(String[] argv) throws Exception {
        var main = new JooqBazelCodegen();
        JCommander.newBuilder()
                .addObject(main)
                .build()
                .parse(argv);

        main.run();
    }

    private void run() throws IOException {
        Path codegenOutputDir = Files.createTempDirectory("jooq-codegen");
        try (JdbcProvider jdbcContainer = getJDBCDatabase()) {
            migrateWithFlyway(jdbcContainer);


            Configuration configuration =
                    configureJooq(jdbcContainer, codegenOutputDir);
            // generate code with jOOQ
            new GenerationTool().run(configuration);

            createSrcJar(codegenOutputDir);
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            System.exit(1);
        } finally {
            recursiveDeleteOnExit(codegenOutputDir);
        }
    }

    private JdbcProvider getJDBCDatabase() {
        if (dbContainerType == DbType.SQLITE) {
            return new SqliteJdbcProvider();
        }
        var container = switch (dbContainerType) {
            case POSTGRES -> new PostgreSQLContainer(resolveContainer(dockerImage, PostgreSQLContainer.IMAGE));
            case MYSQL -> new MySQLContainer(resolveContainer(dockerImage, MySQLContainer.NAME));
            case MARIADB -> new MariaDBContainer(resolveContainer(dockerImage, MariaDBContainer.NAME));
            case SQLITE -> throw new IllegalStateException("already returned");
        };
        return new TestContainersJdbcProvider(container).start();
    }

    private DockerImageName resolveContainer(String dockerImage, String compatibleImageName) {
        DockerImageName compatibleImage = DockerImageName.parse(compatibleImageName);
        if (dockerImage == null) {
            return compatibleImage;
        }
        return DockerImageName.parse(dockerImage).asCompatibleSubstituteFor(compatibleImage);
    }

    private Configuration configureJooq(
            JdbcProvider jdbcContainer, Path codegenOutputDir) throws IOException {
        Configuration configuration =
                GenerationTool.load(new FileInputStream(jooqConfigXmlPath));
        configuration.getGenerator().getTarget().setDirectory(codegenOutputDir.toAbsolutePath().toString());

        Jdbc jdbc = new Jdbc();
        jdbc.setDriver(jdbcContainer.getDriverClassName());
        jdbc.setUrl(jdbcContainer.getJdbcUrl());
        jdbc.setUser(jdbcContainer.getUsername());
        jdbc.setPassword(jdbcContainer.getPassword());
        configuration.setJdbc(jdbc);
        return configuration;
    }

    private void migrateWithFlyway(JdbcProvider jdbcContainer) {
        FluentConfiguration flyway;
        if (flywayConfigPath != null) {
            String[] args = new String[]{String.format("-configFiles=%s", flywayConfigPath)};
            var commandLineArguments = new CommandLineArguments(new PluginRegister(), args);
            commandLineArguments.validate();

            var configuration = new ConfigurationManagerImpl().getConfiguration(commandLineArguments);

            flyway = Flyway.configure(configuration.getClassLoader()).configuration(configuration);
        } else {
            flyway = Flyway.configure();
        }
        flyway.dataSource(
                        jdbcContainer.getJdbcUrl(),
                        jdbcContainer.getUsername(),
                        jdbcContainer.getPassword())
                .load()
                .migrate();
    }

    private void recursiveDeleteOnExit(Path path) throws IOException {
        Files.walkFileTree(
                path,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes unused) throws IOException {
                        Files.deleteIfExists(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException unused) throws IOException {
                        Files.deleteIfExists(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    private void createSrcJar(Path codegenDir) throws IOException {
        try (var zipFs = FileSystems.newFileSystem(outputSourceJar, Map.of("create","true"))) {
            Files.walkFileTree(codegenDir, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                        throws IOException {
                    Files.createDirectories(zipFs.getPath(codegenDir.relativize(dir).toString()));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    Files.copy(file, zipFs.getPath(codegenDir.relativize(file).toString()));
                    return FileVisitResult.CONTINUE;
                }
            });

        }
    }
}
