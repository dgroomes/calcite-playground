# jdbc

An example Calcite program that uses the JDBC adapter.


## Overview

A natural way to use Calcite is via JDBC. This project illustrates that approach. 


## Instructions

Follow these instructions to build and run the example program.

1. Use Java 17
2. Build the program distribution
    * ```shell
      ./gradlew installDist
      ```
3. Run the program
    * ```shell
      build/install/jdbc/bin/jdbc
      ```
    * It should print something like the following.
    * ```text
      18:39:38 [main] INFO dgroomes.JdbcRunner - Found this observation: Observation[id=1, observation=The sky is blue, type=Uninteresting observation]
      18:39:38 [main] INFO dgroomes.JdbcRunner - Found this observation: Observation[id=2, observation=The speed of light can circle the earth 7 times in a second, type=Interesting observation]
      ```


## Wish List

General clean-ups, TODOs and things I wish to implement for this project:

* [x] DONE Scaffold
* [x] DONE Wire in H2, some tables and some test data
* [x] DONE Make some table-to-table relationships. I'm curious if Calcite looks at the foreign key relationships,
  uniqueness constraints, so that it considers those when query planning.
* [ ] Use the "clone" adapter. I'm interested to see how the dataset gets serialized into some in-memory data structure
  after the initial load from the database.
