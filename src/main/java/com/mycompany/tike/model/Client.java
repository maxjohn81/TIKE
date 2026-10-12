package com.mycompany.tike.model;

public record Client(long id, String fullName, String phone, String email) {
    @Override
    public String toString() {
        return fullName + (phone == null || phone.isBlank() ? "" : " — " + phone);
    }
}
