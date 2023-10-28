# without-jdbc

An example program that directly engages the core Apache Calcite APIs. JDBC is not in the mix.


## Overview

The core of Apache Calcite is relational algebra. Surrounding the core is JDBC and adapters to specific database
systems. I'm interested in "drilling to the core" of Calcite and using and learning the relational algebra API directly
without the added layers of JDBC and complex adapters. SQL is inextricably linked to relational algebra, so this
project will co-mingle SQL and relational algebra expressions.


## Instructions

Follow these instructions to build and run the example program.

1. Use Java 17
2. Build and run the program
    * ```shell
      ./gradlew run
      ```
    * It should print something like the following.
    * ```text
      22:45:54 [main] INFO dgroomes.WithoutJdbcRunner - Let's learn the Apache Calcite relational algebra API!
      22:45:54 [main] INFO dgroomes.WithoutJdbcRunner - City 'Boulder' (1) has a population of 108,968
      22:45:54 [main] INFO dgroomes.WithoutJdbcRunner - City 'Savannah' (2) has a population of 124,331
      ```


## Wish List

General clean-ups, TODOs and things I wish to implement for this project:

* [x] DONE Present city name instead of just OID.
* [ ] IN PROGRESS Turn this into a `without-jdbc` subproject. The dimension of `SQL vs relational expression` is interesting and the
  dimension of `JDBC or no-JDBC` is also interesting. While I considered these dimensions related before, e.g. using SQL
  means you probably use JDBC and using relational expressions means you probably don't use JDBC, I'm now seeing that
  is not a real constraint. My `csv/` subproject uses JDBC and SQL. I'll enrich it to also show relational expressions.
  This project (`relational-algebra`) uses relational expressions and no JDBC. I'll enrich it to also show SQL.
   * DONE Rename to `without-jdbc`
   * Implement a SQL query


## Reference

* [Linq4j source code](https://github.com/apache/calcite/tree/main/linq4j)
  * Interestingly, Linq4j exists as a subproject of [Apache Calcite](https://calcite.apache.org/). I think this is
    because the authors of Linq4j had ambitions to broaden the scope of Linq4j by building up from its core and they
    were successful. Calcite is the result.
