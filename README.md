# Bazel rules for jOOQ codegen from Flyway migrations

This Bazel rule will apply Flyway migrations to a database
launched in a Testcontainer, use those to run jOOQ's code
generator, and produce a source JAR containing your generated
classes.

Please note that this rule is still in an alpha-quality state,
and the steps taken to import it may change in the future.

Add the following to your `MODULE.bazel` file, setting the `version` to the latest one available on https://registry.bazel.build/modules/rules_jooq_flyway_codegen:

```starlark
bazel_dep(name = "rules_jvm_external", version = "...")
```

The codegen rule takes as a parameter a resource jar containing your
application's flyway migrations. Your directory structure should look
something like this:

```
myservice/
|> src/
|  |> main/
|     |> resources/
|        |> db/
|           |> migration/
|              |> V01_00_00__first_migration.sql
|> codegen.xml
|> BUILD.bazel
```

Note that migrations are under `db/migration`. This is needed because Flyway expects to find its migrations at
a path in the form of `db/migration` by default. This can be overridden by supplying a `flyway.toml` file and
specifying a target for `flyway.locations`:

```toml
[flyway]
locations = ["classpath:db/migration"]
```

In the BUILD file, you can build a resource jar with your migrations as follows:

```starlark
java_library(
    name = "migration-jar",
    resources = glob(["src/main/resources/db/migration/*.sql"]),
    visibility = ["//visibility:private"],
)
```

Now you need to set up your `codegen.xml` file. This will be passed to jOOQ's code
generator with only the output directory and JDBC configuration replaced. An [example codegen.xml file is here](./examples/northwind/codegen.xml),
and the [jOOQ documentation for the codegen.xml file is here](https://www.jooq.org/doc/latest/manual/code-generation/codegen-configuration/).

Now that you have a resource jar containing your migrations, you can call the
code generator like so:

```starlark
load("@rules_jooq_flyway_codegen//:defs.bzl", "jooqflyway")
load("@rules_jvm_external//:defs.bzl", "artifact")

jooqflyway(
    name = "northwind-db-classes",
    db_type = "mysql",
    #     flyway_config = "flyway.toml",
    jooq_config = "codegen.xml",
    migration_jar = ":migration-jar",
    visibility = ["//visibility:private"],
    deps = [
        artifact("org.jooq:jooq"),
        artifact("org.jooq:jooq-meta"),
    ],
)
```

Valid options for db type are `postgres`, `mariadb`, or `mysql` and `sqlite`.

And that's it! You can now depend on `//myservice:myservice-db-classes`
