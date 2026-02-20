"""
jOOQ code generation by way of testcontainers and flyway
"""

load("//private:jooqflyway.bzl", _jooqflyway = "jooqflyway")

visibility("public")
jooqflyway = _jooqflyway
