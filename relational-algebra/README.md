# relational-algebra

An example program that uses the Apache Calcite relational algebra API to query over a data set.


## Overview

The core of Apache Calcite is relational algebra. Surrounding the core is JDBC, SQL and adapters to specific database
systems. I'm interested in "drilling to the core" of Calcite and using and learning the relational algebra API directly.


## Instructions

Follow these instructions to build and run the example program.

1. Use Java 17
2. Build and run the program
    * ```shell
      ./gradlew run
      ```
    * It should print something like the following.
    * ```text
      23:25:05 [main] INFO dgroomes.RelationalAlgebraRunner - Let's learn the Apache Calcite relational algebra API by summing up a sample of ZIP code population data by their city.
      23:25:06 [main] INFO dgroomes.RelationalAlgebraRunner - City '1' has a population of 108,968
      23:25:06 [main] INFO dgroomes.RelationalAlgebraRunner - City '2' has a population of 124,331
      ```


## Wish List

General clean-ups, TODOs and things I wish to implement for this project:

* [ ] Present city name instead of just OID.


## Reference

* [Linq4j source code](https://github.com/apache/calcite/tree/main/linq4j)
  * Interestingly, Linq4j exists as a subproject of [Apache Calcite](https://calcite.apache.org/). I think this is
    because the authors of Linq4j had ambitions to broaden the scope of Linq4j by building up from its core and they
    were successful. Calcite is the result.
