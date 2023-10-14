# class-relationships

NOT YET FULLY IMPLEMENTED.

Model class-to-class relationships in an in-memory Calcite schema and explore advanced features like optimizer rules.  


## Overview

This subproject is designed to explore advanced features of Apache Calcite like optimizer rules. The example domain is
arbitrary, but I want something interesting and relatable. Let's just look at runtime class metadata. There is a `class`
table, which has a name, a list of methods, and a list of fields. Correspondingly there is a `method` table, a `field`
table and so on. Things get interesting because `methods` have relationships to other classes by way of the types of their
method signatures. The same for fields.

I think this will make for a rich enough data set to keep things interesting.


## Instructions

Follow these instructions to build and run the example program.

1. Use Java 17
2. Build and run the program
    * ```shell
      ./gradlew run
      ```
3. Try running the program with the `TAKE_FIRST_N_CLASSES` option
    * ```shell
      TAKE_FIRST_N_CLASSES=100 ./gradlew run
      ```


## Wish List

General clean-ups, TODOs and things I wish to implement for this project:

* [x] DONE Scaffold
* [x] DONE Populate the `types` table with names only. Use ClassGraph to scan the classpath for classes and populate the table.
  Also, write a `limit` query or something to exercise it.
   * DONE Schema and sample query.
   * DONE Use ClassGraph
* [x] DONE Lean more into ClassGraph. It's a nice API. I need to be coding to the "Info" classes instead of loading
  the classes because I'll get `ClassNotFoundException` for classes that are not on the classpath at runtime but were
  at compile time.
* [x] DONE Populate the `field` table with names and their owning class (NOT their declared class. that comes later). Also, write a `limit` query or something to exercise it.
* [x] DONE (wow it executes slowly! 30+ seconds) Write a join query between `class` and `field`.
* [ ] HOLD (Partially implemented. I need to switch gears and I think creating my own adapter which is a simple port of
    ReflectiveSchema will help untangle the understanding weeds and open APIs that are closed.) Research how the join is executed at runtime. Is there a bit set?
  * DONE Reduce the dataset (parameterizable) so that we have a more manageable dataset to work with.
  * DONE (well I just increased the log level but when it comes to actual execution nothing is logged, probably for performance) Look into Calcite's documentation on debugging and tracing.
  * I profiled the program execution and this is the hot spot: `org.apache.calcite.interpreter.JoinNode.doJoin()`
  * (Blocked. Statistics are inaccessible in ReflectiveSchema). It is doing a nested loop join which is slow. From the trace logs I can tell that it estimates the table scan for each
    table (field and class) to be 100 rows. I think this is just a default. This is wrong of course because there are
    tens of thousands of rows total. If I can update the statistics, will the planner choose a better join algorithm
    like hash join?
  * DONE Figure out where the 100 rows value is coming from and try to update it. Update: I think maybe I should
    switch gears and try to learn about Calcite inner workings from a blessed entrypoint like a custom adapter, like the
    CSV one. Because what I'm doing is jumping into the middle. Answer: https://github.com/apache/calcite/blob/54e3faf0618c25a63b1c40c0ec3855ce0b842127/core/src/main/java/org/apache/calcite/prepare/RelOptTableImpl.java#L243
* [ ] Assuming that the join is not optimized (or even if it is?), write a custom optimizer rule to optimize the join.
  I want to know the options for implementing joins where there isn't a join key but instead there is a direct pointer
  (object-to-object reference). Or maybe I'll realize that my question doesn't even make sense.
* [ ] IN PROGRESS Custom adapter which is mostly just a port of ReflectiveSchema. I need support for statistics, but it would also
  be nice if it supported record types.
  * DONE "Scaffold by copy"
  * IN PROGRESS Make it my own. Refactoring/restructuring to my liking. Update: this is the second time I've gone down
    this road. I made a custom schema in my `csv/` subproject. It makes me pause, but not sure what the lesson is.
  * Support record types
  * Support statistics
