package com.mycompany.tike.model;

public record Room(long id, long venueId, String venueName, String name, int capacity) {
    @Override
    public String toString() {
        return venueName + " — " + name + " (" + capacity + " places)";
    }
}
