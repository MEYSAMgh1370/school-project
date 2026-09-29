package com.example.roshdeandishe.finance.dto;

public record VendorResponse(Long id, String name, String phone, String nationalId, String economicCode,
                             String address, boolean active) {
}
