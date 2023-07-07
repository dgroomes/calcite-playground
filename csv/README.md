# csv

A Calcite-based program that defines a simple CSV adapter to treat CSV files as tables and make them queryable via SQL.

## Overview

This was originally adapted from the [`csv` example project](https://github.com/apache/calcite/tree/main/example/csv) in
the Calcite codebase but reduced in scope and uses ZIP codes as the domain for sample data.

In essence, we have tabular ZIP code and city data in CSV files, and we can query it using SQL. Pretty neat! For
example, the ZIP code data looks like this (`ZIPs.csv`):

| ZIP_CODE:int | POPULATION:int | CITY_OID:int |
|--------------|----------------|--------------|
| 80301        | 18174          | 1            |
| 80302        | 29384          | 1            |
| 31401        | 37544          | 2            |
| 31405        | 28739          | 2            |
| ...etc       |                |              |

And the city data looks like this (`CITIES.csv`):

| OID:int | NAME:string | STATE_CODE:string |
|---------|-------------|-------------------|
| 1       | Boulder     | CO                |
| 2       | Savannah    | GA                |

And you can query it with the regular force of SQL:

```sql
select c.name,
       c.state_code,
       sum(z.population) as population
from cities as c
         join zips z on c.oid = z.city_oid
group by c.name, c.state_code
order by population desc
```


## Instructions

Follow these instructions to build and run the example program.

1. Use Java 17
2. Build and run the program
    * ```shell
      ./gradlew run
      ```
    * It should print something like the following.
    * ```text
      00:36:39 [main] INFO dgroomes.Runner - Let's learn about Apache Calcite! Let's treat local CSV files as tables.
      00:36:39 [main] INFO dgroomes.Runner -
      00:36:39 [main] INFO dgroomes.Runner - Select all ZIP codes and their populations...
      00:36:40 [main] INFO dgroomes.Runner - ZIP code: 80301, population: 18,174
      00:36:40 [main] INFO dgroomes.Runner - ZIP code: 80302, population: 29,384
      00:36:40 [main] INFO dgroomes.Runner - ZIP code: 80303, population: 39,860
      00:36:40 [main] INFO dgroomes.Runner - ZIP code: 80304, population: 21,550
      00:36:40 [main] INFO dgroomes.Runner - ZIP code: 31401, population: 37,544
      00:36:40 [main] INFO dgroomes.Runner - ZIP code: 31405, population: 28,739
      00:36:40 [main] INFO dgroomes.Runner - ZIP code: 31406, population: 34,024
      00:36:40 [main] INFO dgroomes.Runner - ZIP code: 31409, population: 3,509
      00:36:40 [main] INFO dgroomes.Runner - ZIP code: 31410, population: 15,808
      00:36:40 [main] INFO dgroomes.Runner - ZIP code: 31411, population: 4,707
      00:36:40 [main] INFO dgroomes.Runner -
      00:36:40 [main] INFO dgroomes.Runner - Sum up the population of each city...
      00:36:40 [main] INFO dgroomes.Runner - Population of Savannah (GA): 124,331
      00:36:40 [main] INFO dgroomes.Runner - Population of Boulder (CO): 108,968
      ```

## Wish List

General clean-ups, TODOs and things I wish to implement for this project:

* [x] DONE Move package
* [x] DONE Remove sqlline dependency and instead feature hardcoded queries from the `main` method
* [x] DONE Consider removing the Immutables dependency. It's a nice library, but it's not really necessary for this
  project.
* [x] DONE Pare down the model stuff. I don't need so much variety, I just a working example project. For example, it's
  necessary to have both JSON and YAML model files. Let's just do JSON.
* [x] DONE Remove Guava
* [x] DONE Remove checker-framework
* [x] DONE Wire up the schema programmatically instead of using a JSON model file
* [x] DONE support just `.csv` files in the example
* [x] DONE Replace employee/sales domain with ZIPs/cities/states
