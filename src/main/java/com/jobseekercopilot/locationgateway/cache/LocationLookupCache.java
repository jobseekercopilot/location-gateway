package com.jobseekercopilot.locationgateway.cache;

import com.jobseekercopilot.locationgateway.config.LocationControlProperties;
import com.jobseekercopilot.locationgateway.model.Location;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LocationLookupCache {
    private final long ttlNanos;
    private final int maximumEntries;
    private final LongSupplier nanoTime;
    private final Map<String, CacheEntry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final Counter hits;
    private final Counter misses;
    private final Counter evictions;

    @Autowired
    public LocationLookupCache(LocationControlProperties properties, MeterRegistry meterRegistry) {
        this(properties, meterRegistry, System::nanoTime);
    }

    LocationLookupCache(
            LocationControlProperties properties,
            MeterRegistry meterRegistry,
            LongSupplier nanoTime) {
        properties.validate();
        this.ttlNanos = properties.getCacheTtl().toNanos();
        this.maximumEntries = properties.getCacheMaximumEntries();
        this.nanoTime = nanoTime;
        this.hits = Counter.builder("location.postcode.cache.requests")
                .tag("result", "hit").register(meterRegistry);
        this.misses = Counter.builder("location.postcode.cache.requests")
                .tag("result", "miss").register(meterRegistry);
        this.evictions = Counter.builder("location.postcode.cache.evictions").register(meterRegistry);
    }

    public synchronized Optional<Location> get(String canonicalPostcode) {
        CacheEntry entry = entries.get(canonicalPostcode);
        if (entry == null) {
            misses.increment();
            return Optional.empty();
        }
        if (nanoTime.getAsLong() - entry.storedAtNanos >= ttlNanos) {
            entries.remove(canonicalPostcode);
            misses.increment();
            evictions.increment();
            return Optional.empty();
        }
        hits.increment();
        return Optional.of(copy(entry.location));
    }

    public synchronized void put(String canonicalPostcode, Location location) {
        if (!entries.containsKey(canonicalPostcode) && entries.size() >= maximumEntries) {
            Iterator<String> oldest = entries.keySet().iterator();
            if (oldest.hasNext()) {
                oldest.next();
                oldest.remove();
                evictions.increment();
            }
        }
        entries.put(canonicalPostcode, new CacheEntry(copy(location), nanoTime.getAsLong()));
    }

    synchronized int size() {
        return entries.size();
    }

    private static Location copy(Location location) {
        return new Location(
                location.getId(),
                location.getName(),
                location.getPostcode(),
                location.getRegion(),
                location.getLatitude(),
                location.getLongitude());
    }

    private record CacheEntry(Location location, long storedAtNanos) {
    }
}
