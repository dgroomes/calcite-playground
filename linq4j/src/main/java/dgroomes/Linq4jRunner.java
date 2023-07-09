package dgroomes;

import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Linq4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Example using Linq4j to query in-memory collections.
 */
public class Linq4jRunner {

    private static final Logger log = LoggerFactory.getLogger(Linq4jRunner.class);

    /**
     * Note: "oid" means "object ID". It's a unique identifier for that object type.
     */
    record City(int oid, String name, String stateCode) {}

    static City[] CITIES = {
            new City(1, "Boulder", "CO"),
            new City(2, "Savannah", "GA")
    };

    record Zip(int zipCode, int population, int cityOid) {}

    static Zip[] ZIPS = new Zip[]{
            new Zip(80301, 18174, 1),
            new Zip(80302, 29384, 1),
            new Zip(80303, 39860, 1),
            new Zip(80304, 21550, 1),
            new Zip(31401, 37544, 2),
            new Zip(31405, 28739, 2),
            new Zip(31406, 34024, 2),
            new Zip(31409, 3509, 2),
            new Zip(31410, 15808, 2),
            new Zip(31411, 4707, 2),
    };

    public static void main(String[] args) {
        log.info("Let's learn the Linq4j API by summing up a sample of ZIP code population data by their city.");

        // It's convenient to use Java record classes to represent the intermediate (and final projection) forms of the
        // data as it goes through a Linq4j collection pipeline.
        record CityPopulationOid(int cityOid, int population) {}
        record CityPopulationName(String cityName, int population) {}

        Enumerable<CityPopulationName> cityPopulations = Linq4j.asEnumerable(ZIPS)
                .groupBy(Zip::cityOid,
                        () -> 0,
                        (runningSum, zip) -> runningSum + zip.population,
                        CityPopulationOid::new)
                .hashJoin(Linq4j.asEnumerable(CITIES),
                        CityPopulationOid::cityOid,
                        City::oid,
                        (cityPopulationOid, city) -> new CityPopulationName(city.name(), cityPopulationOid.population()))
                .orderByDescending(CityPopulationName::population);

        log.info("Cities from most populous to least populous:");
        cityPopulations.forEach(cityPopulation -> {
            var pop = formatInteger(cityPopulation.population());
            log.info("{} has a population of {}", cityPopulation.cityName(), pop);
        });
    }

    /**
     * Formats an integer value with commas.
     * <p>
     * For example, 1234567 becomes "1,234,567".
     */
    public static String formatInteger(int value) {
        return NumberFormat.getNumberInstance(Locale.US).format(value);
    }
}
