package com.jobseekercopilot.locationgateway.service;

import com.jobseekercopilot.locationgateway.client.PostcodeIoClient;
import com.jobseekercopilot.locationgateway.model.Location;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LocationService {
    
    @Autowired
    private PostcodeIoClient postcodeIoClient;
    
    private List<Location> locationCatalog;

    @PostConstruct
    public void init() {
        locationCatalog = new ArrayList<>();
        locationCatalog.add(new Location("loc-1", "Leeds, West Yorkshire", "LS1", "Yorkshire and the Humber"));
        locationCatalog.add(new Location("loc-2", "Manchester, Greater Manchester", "M1", "North West"));
        locationCatalog.add(new Location("loc-3", "Birmingham, West Midlands", "B1", "West Midlands"));
        locationCatalog.add(new Location("loc-4", "London Central, Greater London", "EC1A", "London"));
        locationCatalog.add(new Location("loc-5", "London Enfield, Greater London", "EN1", "London"));
        locationCatalog.add(new Location("loc-6", "London Westminster, Greater London", "SW1A", "London"));
        locationCatalog.add(new Location("loc-7", "Glasgow City Centre, Scotland", "G1", "Scotland"));
        locationCatalog.add(new Location("loc-8", "Edinburgh, Midlothian", "EH1", "Scotland"));
        locationCatalog.add(new Location("loc-9", "Bristol City Centre, Bristol", "BS1", "South West"));
        locationCatalog.add(new Location("loc-10", "Sheffield, South Yorkshire", "S1", "Yorkshire and the Humber"));
        locationCatalog.add(new Location("loc-11", "Cardiff City Centre, Wales", "CF10", "Wales"));
        locationCatalog.add(new Location("loc-12", "Newcastle-upon-Tyne, Tyne and Wear", "NE1", "North East"));
        locationCatalog.add(new Location("loc-13", "Liverpool, Merseyside", "L1", "North West"));
        locationCatalog.add(new Location("loc-14", "Belfast City Centre, Northern Ireland", "BT1", "Northern Ireland"));
        locationCatalog.add(new Location("loc-15", "Wakefield, West Yorkshire", "WF1", "Yorkshire and the Humber"));
        locationCatalog.add(new Location("loc-16", "York, North Yorkshire", "YO1", "Yorkshire and the Humber"));
        locationCatalog.add(new Location("loc-17", "Leicester, East Midlands", "LE1", "East Midlands"));
        locationCatalog.add(new Location("loc-18", "Coventry, West Midlands", "CV1", "West Midlands"));
        locationCatalog.add(new Location("loc-19", "Nottingham, East Midlands", "NG1", "East Midlands"));
        locationCatalog.add(new Location("loc-20", "Southampton, Hampshire", "SO14", "South East"));
    }
    
    public List<Location> searchLocations(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        String lowerQuery = query.toLowerCase();
        
        return locationCatalog.stream()
                .filter(loc -> loc.getName().toLowerCase().contains(lowerQuery) ||
                               loc.getPostcode().toLowerCase().contains(lowerQuery) ||
                               loc.getRegion().toLowerCase().contains(lowerQuery))
                .limit(10)
                .collect(Collectors.toList());
    }
    
    public Mono<Location> getLocationFromPostcodeIo(String postcode) {
        return postcodeIoClient.getPostcodeDetails(postcode)
                .map(result -> {
                    String region = result.getRegion();
                    String adminDistrict = result.getAdminDistrict();
                    String formattedName = (adminDistrict != null ? adminDistrict : "") 
                            + (region != null && !region.trim().isEmpty() ? ", " + region : "");
                    return new Location(
                        "external-" + postcode,
                        formattedName,
                        postcode,
                        region
                    );
                });
    }
}
