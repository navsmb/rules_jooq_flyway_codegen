load("@contrib_rules_jvm//java:defs.bzl", "java_binary", "java_library")
load("@rules_jvm_external//:defs.bzl", "artifact")

visibility("//...")

def _impl(ctx):
    src_jar_file = ctx.actions.declare_file(ctx.attr.name + ".srcjar")
    args = ctx.actions.args()
    args.add("--output_file_name", src_jar_file.path)
    args.add("--db_type", ctx.attr.db_type)
    args.add("--jooq_config_xml_path", ctx.file.jooq_config)
    inputs = depset([ctx.file.jooq_config], transitive = [ctx.attr.migration_jar.files])
    if ctx.attr.docker_image:
        args.add("--docker_image", ctx.attr.docker_image)
    if ctx.file.flyway_config:
        args.add("--flyway_config_file_path", ctx.file.flyway_config)
        inputs = depset([ctx.file.flyway_config], transitive = [inputs])

    ctx.actions.run(
        inputs = inputs,
        outputs = [src_jar_file],
        executable = ctx.executable.tool,
        arguments = [args],
    )

    return [DefaultInfo(files = depset([src_jar_file]))]

jooqflyway_gensrcs = rule(
    implementation = _impl,
    attrs = {
        "migration_jar": attr.label(),
        "jooq_config": attr.label(allow_single_file = True),
        "tool": attr.label(
            executable = True,
            cfg = "exec",
        ),
        "db_type": attr.string(),
        "docker_image": attr.string(mandatory = False),
        "flyway_config": attr.label(allow_single_file = True, mandatory = False),
    },
)

def jooqflyway(
        name,
        migration_jar,
        jooq_config,
        db_type,
        flyway_config = None,
        docker_image = None,
        deps = [
            artifact("org.jooq:jooq"),
        ],
        **kwargs):
    """
            Generate jooq code from a set of flyway mirgartions.
            Creates a test container using the passed `docker_image` (or a default one),
            connects to it as `db_type`, runs the migrations in `migration_jar` using
            flyway, and uses jOOQ to generate java code from the migrated database
            instance and `codegen_xml`.

            The generated sources are compiled as a `java_library` and all `java_library`
            args are passed to it.

            Args:
              name: name of the target.
              migration_jar: a jar to be added to the classpath of the code generator and used to run flyway migrations.
              jooq_config: a jOOQ [configuration xml](https://www.jooq.org/doc/latest/manual/code-generation/codegen-configuration/) that configures jOOQ.
              db_type: the type of DB. Must be one of: `postgres`, `mariadb`, `mysql`, `sqlite`
              flyway_config: a flwyway [config file](https://documentation.red-gate.com/fd/configuration-277578842.html)
              docker_image: a docker url to use instead of a default image. By default is `--` which will be interpreted as 'use the default image'.
              deps: dependencies of the generated code. If not specified will be `[artifact("org.jooq:jooq")]`
    """

    # generate a binary target with the migration jar added to the class path
    java_binary(
        name = name + "_codegen",
        main_class = "dev.richst.jooq_bazel.JooqBazelCodegen",
        runtime_deps = [
            migration_jar,
            Label("//private/src/main/java/dev/richst/jooq_bazel:codegen_lib"),
        ],
    )

    # run the binary with the required arguments. migration_jar is passed so it is marked as an input to the task
    jooqflyway_gensrcs(
        name = name + "_srcjar",
        migration_jar = migration_jar,
        tool = name + "_codegen",
        jooq_config = jooq_config,
        db_type = db_type,
        docker_image = docker_image,
        flyway_config = flyway_config,
    )

    java_library(
        name = name,
        srcs = [":" + name + "_srcjar"],
        deps = deps,
        **kwargs
    )
