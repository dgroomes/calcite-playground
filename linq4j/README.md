# linq4j

An example program that uses Linq4j (a subproject of Apache Calcite) to join in-memory collections.


## Overview

Basically, I want to learn Linq4j. Linq4j is a namesake of LINQ (Language Integrated Query), the much-beloved query
capability available natively in .NET languages like C#.


## Instructions

Follow these instructions to build and run the example program.

1. Use Java 17
2. Build and run the program
    * ```shell
      ./gradlew run
      ```
    * It should print something like the following.
    * ```text
      13:51:23 [main] INFO dgroomes.Linq4jRunner - Let's learn the Linq4j API by summing up a sample of ZIP code population data by their city.
      13:51:23 [main] INFO dgroomes.Linq4jRunner - Cities from most populous to least populous:
      13:51:23 [main] INFO dgroomes.Linq4jRunner - Savannah has a population of 124,331
      13:51:23 [main] INFO dgroomes.Linq4jRunner - Boulder has a population of 108,968
      ```


## Wish List

General clean-ups, TODOs and things I wish to implement for this project:

* [ ] For large collections, what is the performance of joins? Does it take into account the size of the collections to
  figure out which to use for the "build" side (this should be the smaller collection) and which to use for the "probe"
  side (this should be the larger collection)?
* [ ] Instead of writing a Linq4j expression tree directly, can I write an expression using the Calcite relational algebra
  API and then "compile that" (or whatever) to a proper Calcite physical execution plan (which I think should execute
  with the full force of Calcite's query optimizations and magic).

## Reference

* [Linq4j source code](https://github.com/apache/calcite/tree/main/linq4j)
  * Interestingly, Linq4j exists as a subproject of [Apache Calcite](https://calcite.apache.org/). I think this is
    because the authors of Linq4j had ambitions to broaden the scope of Linq4j by building up from its core and they
    were successful. Calcite is the result.
