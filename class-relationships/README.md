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

* HOLD (I think it's as simple as "don't use the interpreter" but it's murky to me still) Why does the Calcite "interpreter" not use the "fast path" for joins? By contrast, a demo "CSV using Calcite" demo
  program (like my own in `csv/`) will not use the interpreter and instead use `org.apache.calcite.linq4j.EnumerableDefaults.mergeJoin`
  and I think the Janino generated code (not 100% sure). The reason I'm using the interpreter is that I found that
  it's the only way to avoid JDBC and therefore to use the relational algebra API directly. But maybe Calcite just
  doesn't offer a path for that.
  * I think it might just be a matter of passing the relational algebra expression (nodes-to-noes) through an optimizer?
    Calcite has a planner called "Volcano". This is promising, is it as simple as just passing in the unoptimized expression
    (a "nested for loop join" (slow) vs. a "hashset join" (fast))?
  * Interesting, in a "normal" usage of Calcite where you connect via a JDBC connection instead of directly using the
    interpreter class, I see debug log output that indicates two different planners are used: "Hep" and "Volcano".
    Specifically, set the log level to DEBUG for `org.apache.calcite.plan.AbstractRelOptPlanner.rule_execution_summary`.
    What is "Hep"? Answer: "he" is for heuristic. How can I make my execution use the Volcano planner?
  * I'm taking the heavy-handed approach of copying a few of the Calcite classes (Interpreter, JaninoRexCompiler, and
    CustomNodes) into this project and I'll modify them to achieve the effect I'm looking for.
  * SKIP (I think the interpreter will only ever execute "logical" relational expression trees and these do not define
    the physical join like merge/hash/inner, but instead just a logical one) If we sort the rows before joining, then the query execution should be able to do a merge join. If it still doesn't
    do a merge join, there should be heuristic rule that identifies this optimization (I'm looking in the area of org.apache.calcite.rel.rules.LoptOptimizeJoinRule)
* [ ] Assuming that the join is not optimized (or even if it is?), write a custom optimizer rule to optimize the join.
  I want to know the options for implementing joins where there isn't a join key but instead there is a direct pointer
  (object-to-object reference). Or maybe I'll realize that my question doesn't even make sense.


## Finished Wish List Items

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
* [x] DONE (It's a nested for loop (of course slow; quadratic) in the "interpreter" module) Research how the join is executed at runtime. Is there a bit set?
  * DONE Reduce the dataset (parameterizable) so that we have a more manageable dataset to work with.
  * DONE (well I just increased the log level but when it comes to actual execution nothing is logged, probably for performance) Look into Calcite's documentation on debugging and tracing.
  * I profiled the program execution and this is the hot spot: `org.apache.calcite.interpreter.JoinNode.doJoin()`
  * DONE (Answer: no row count stats didn't help) It is doing a nested loop join which is slow. From the trace logs I can tell that it estimates the table scan for each
    table (field and class) to be 100 rows. I think this is just a default. This is wrong of course because there are
    tens of thousands of rows total. If I can update the statistics, will the planner choose a better join algorithm
    like hash join?
  * DONE Figure out where the 100 rows value is coming from and try to update it. Update: I think maybe I should
    switch gears and try to learn about Calcite inner workings from a blessed entrypoint like a custom adapter, like the
    CSV one. Because what I'm doing is jumping into the middle. Answer: https://github.com/apache/calcite/blob/54e3faf0618c25a63b1c40c0ec3855ce0b842127/core/src/main/java/org/apache/calcite/prepare/RelOptTableImpl.java#L243
* [x] DONE Custom table implementation which is mostly just a port of ReflectiveSchema. I need support for statistics.
  * DONE "Scaffold by copy"
  * DONE Make it my own. Refactoring/restructuring to my liking. Update: this is the second time I've gone down
    this road. I made a custom schema in my `csv/` subproject. It makes me pause, but not sure what the lesson is.
  * DONE Support only lists and not arrays.
  * SKIP (I totally forgot that records are impractical if I ever want to model instance-to-instance
    relationships. Like MethodInfo's List<ClassInfo> field. I make this mistake very often. How can I learn?) Support record types
  * DONE I want less reflection. I want reflection only on the table-as-a-class classes, and I don't need it on
    th schema-as-a-class class.
  * DONE Support statistics (row count at least)
    * We want to go from the current execution speed (slow): `Query executed in PT33.866871S.` to a few hundred ms. This
      is small data after all.
    * DONE (this had no effect on speed, unfortunately. why is it so slow?) row count
* [x] DONE Visualize the query plan. I keep getting stumped and I have to change gears again. Need to learn the native tools
  here. I think I should visualize the query plan using org.apache.calcite.plan.visualizer.RuleMatchVisualizer. It
  produces HTML. It's hard to read a query plan in the toString form, so I'm hoping this form will illuminate things.
  * Reference <https://github.com/apache/calcite/blob/5151168e9a9035595939c2ae0f21a06984229209/core/src/test/java/org/apache/calcite/test/RuleMatchVisualizerTest.java#L60>
  * Update: I got this working for the Volcano planner at least. I think when I used the heuristic planner, it just
    wasn't finding optimizations so there was nothing to report on. And then even with the Volcano plan, I think I
    screwed up my web server and I think I was serving cached content (bad) and was getting blank content (but with
    UI/viz controls) so I need to be careful with that. Anyway, the viz is not so important if I can instead just
    reduce the problem space to very small.
