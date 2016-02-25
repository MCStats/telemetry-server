package org.mcstats.generator.aggregator.plugin;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.mcstats.generator.DataContainer;
import org.mcstats.generator.aggregator.BasicAggregator;
import org.mcstats.model.Server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class CountryAggregator implements BasicAggregator<Server> {

    private static final String COUNTRIES_RESOURCE_PATH = "/countries.json";

    /**
     * A map of all countries, keyed by the 2 letter country code
     */
    private final Map<String, String> countries = new HashMap<>();

    public CountryAggregator() {
        loadCountries();
    }

    @Override
    public void aggregate(DataContainer container, Server instance) {
        String countryName;
        String serverCountryCode = instance.getCountry();

        if (serverCountryCode.equals("ZZ")) {
            countryName = "Unknown";
        } else {
            countryName = countries.get(serverCountryCode);
        }

        if (countryName != null) {
            container.add("Server Locations", countryName, 1);
        }
    }

    /**
     * Loads country short codes
     */
    private void loadCountries() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(COUNTRIES_RESOURCE_PATH)))) {
            JSONArray root = (JSONArray) JSONValue.parse(reader);

            for (Object object : root) {
                JSONObject countryRoot = (JSONObject) object;

                String countryName = countryRoot.get("name").toString();
                String twoLetterCode = countryRoot.get("alpha-2").toString();

                countries.put(twoLetterCode, countryName);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
