package com.example.roshdeandishe.finance.dto;

public record VendorRequest(String name, String phone, String nationalId, String economicCode,
                            String address, Boolean active) {
}
