load("@rules_jvm_external//:defs.bzl", "artifact")
load("@contrib_rules_jvm//java:defs.bzl", "java_library", "java_binary")

visibility("//...")

def _impl(ctx):
    file = ctx.actions.declare_file(ctx.attr.name + ".srcjar")
    args = ctx.actions.args()
    args.add(file.path)
    args.add(ctx.attr.db_type)
    args.add(ctx.attr.docker_image)
    args.add_all(ctx.attr.codegen_xml.files)

    ctx.actions.run(
        inputs = ctx.attr.migration_jar.files.to_list() + ctx.attr.codegen_xml.files.to_list(),
        outputs = [file],
        executable = ctx.executable.tool,
        arguments = [args],
    )

    return [DefaultInfo(files = depset([file]))]

jooqflyway_gensrcs = rule(
    implementation = _impl,
    attrs = {
        "migration_jar": attr.label(),
        "codegen_xml": attr.label(allow_single_file = True),
        "tool": attr.label(
            executable = True,
            cfg = "exec",
        ),
        "db_type": attr.string(),
        "docker_image": attr.string(),
    },
)

def jooqflyway(
        name,
        migration_jar,
        codegen_xml,
        db_type,
        docker_image = "--",
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
              codegen_xml: a jOOQ [configuration xml](https://www.jooq.org/doc/latest/manual/code-generation/codegen-configuration/) that configures jOOQ.
              db_type: the type of DB. Must be one of: `postgres`, `mariadb`, `mysql`, `sqlite`
              docker_image: a docker url to use instead of a default image. By default is `--` which will be interpreted as 'use the default image'.
              deps: dependencies of the generated code. If not specified will be `[artifact("org.jooq:jooq")]`
    """

    # generate a binary target with the migration jar added to the class path
    java_binary(
        name = name + "_codegen",
        main_class = "dev.richst.jooq_bazel.JooqBazelCodegen",
        runtime_deps = [
            migration_jar,
            Label("//private/src/main/java/dev/richst/jooq_bazel:codegen_lib")
        ]
    )
    # run the binary with the required arguments. migration_jar is passed so it is marked as an input to the task
    jooqflyway_gensrcs(
        name = name + "_srcjar",
        migration_jar = migration_jar,
        tool = name + "_codegen",
        codegen_xml = codegen_xml,
        db_type = db_type,
        docker_image = docker_image,
    )

    java_library(
        name = name,
        srcs = [":" + name + "_srcjar"],
        deps = deps,
        **kwargs
    )
