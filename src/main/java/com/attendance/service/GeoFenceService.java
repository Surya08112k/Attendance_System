package com.attendance.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GeoFenceService {

    @Value("${attendance.geo.office-lat}")
    private double officeLat;

    @Value("${attendance.geo.office-lon}")
    private double officeLon;

    @Value("${attendance.geo.radius-meters}")
    private double radiusMeters;

    @Value("${attendance.geo.enforce}")
    private boolean enforce;

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    /**
     * Returns the distance in metres between (lat,lon) and the office.
     */
    public double distanceFromOffice(double lat, double lon) {
        double dLat = Math.toRadians(lat - officeLat);
        double dLon = Math.toRadians(lon - officeLon);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(officeLat))
                 * Math.cos(Math.toRadians(lat))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }

    /**
     * Returns true if (lat,lon) is within the allowed office radius.
     */
    public boolean isWithinOffice(double lat, double lon) {
        return distanceFromOffice(lat, lon) <= radiusMeters;
    }

    /**
     * When enforcement is ON and employee is outside office, deny marking.
     */
    public boolean isEnforced() {
        return enforce;
    }

    public double getOfficeLat()     { return officeLat; }
    public double getOfficeLon()     { return officeLon; }
    public double getRadiusMeters()  { return radiusMeters; }
}
