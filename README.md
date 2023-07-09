# calcite-playground

📚 Learning and exploring Apache Calcite.

> Apache Calcite
>
> The foundation for your next high-performance database.
>
> --<cite>https://calcite.apache.org</cite>


## Overview

This repository is me learning Apache Calcite by example. I'm interested in its query planning, optimizing and execution capabilities which it offers through a generic API.

I've struggled to really understand the details and API of Calcite by reading the user guide-type docs on the website. So I'm going to "learn by doing" and adapt from the [`csv` example project](https://github.com/apache/calcite/tree/main/example/csv) and tweak that project to learn the concepts and features I'm interested in. I'm particularly interested in how joins work for in-memory datasets. How does that algorithm work? Does it use bit sets?


## Standalone subprojects

This repository illustrates different concepts, patterns and examples via standalone subprojects. Each sub-project is
completely independent of the others and do not depend on the root project. This _standalone sub-project constraint_
forces the subprojects to be complete and maximizes the reader's chances of successfully running, understanding, and
re-using the code.

The subprojects include:

### `csv/`

A Calcite-based program that uses the official CSV adapter to treat CSV files as tables and make them queryable via SQL.

See the README in [csv/](csv/).

### `linq4j/`

An example program that uses Linq4j (a subproject of Apache Calcite) to join in-memory collections.

See the README in [linq4j/](linq4j/).


## Notes

Quote from the [Calcite tutorial](https://calcite.apache.org/docs/tutorial.html):

> As a "database without a storage layer”, Calcite doesn’t know about any file formats


## Wish List

General clean-ups, TODOs and things I wish to implement for this project:

* [x] DONE Get the CSV example working. Adapt it mostly verbatim from the official example.
* [x] DONE (I think I'm satisfied by finding https://github.com/apache/calcite/blob/e3105a8fe03a08d02500001314dc4e9696285e83/linq4j/src/test/java/com/example/Linq4jExample.java#L25) "Drill to the core". I should be able to drill past the JDBC/Avatica layer and create an "enumerable and/or binding?"
  directly (and via the relational algebra API) and then execute it. I learned that the "bindable" is the thing that
  gets executed and "does the work" of actual query execution. In Calcite, code-generated LinQ4J code is how this works
  by default (in the absense of other adapters/engines). This is what I want to do. Probably do this work in the context
  of a simple in-memory/small dataset. Maybe I could do in the `csv/` subproject but that would be conflating two things.
* [x] DONE Create a Linq4j subproject. See https://github.com/apache/calcite/blob/e3105a8fe03a08d02500001314dc4e9696285e83/linq4j/src/test/java/com/example/Linq4jExample.java#L25
* [ ] (stretch) Create a subproject that creates a schema over a heap dump. There already is [a Calcite plugin for Eclipse Memory Analyzer](https://github.com/vlsi/mat-calcite-plugin),
  but I want to learn by implementing something myself and heap dumps are a convenient source of in-memory relational data.
* [ ] Learn how in-memory joins (the thing that Calcite does for something it calls its "enumerable calling convention") are implemented. I want to learn this in the context of the heap dump subproject. Can
  I make an optimizer rule?


## Reference

* [Apache Calcite](https://calcite.apache.org/)
* [Apache Calcite: Tracing](https://calcite.apache.org/docs/howto.html#tracing)
   * > To enable tracing, add the following flags to the java command line: `-Dcalcite.debug=true`
* [Apache Calcite: *Built-in SQL implementation*](https://calcite.apache.org/docs/adapter.html#built-in-sql-implementation)
   * > Relational expressions of enumerable convention are implemented as “built-ins”: Calcite generates Java code, compiles it, and executes inside its own JVM. Enumerable convention is less efficient than, say, a distributed engine running over column-oriented data files, but it can implement all core relational operators and all built-in SQL functions and operators. 
