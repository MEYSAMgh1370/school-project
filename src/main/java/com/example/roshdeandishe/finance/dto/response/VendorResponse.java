package com.example.roshdeandishe.finance.dto.response;

public record VendorResponse(Long id, String name, String phone, String nationalId, String economicCode,
                             String address, boolean active) {
}
