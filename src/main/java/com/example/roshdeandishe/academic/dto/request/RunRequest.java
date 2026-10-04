package com.example.roshdeandishe.academic.dto.request;

import java.util.List;

public record RunRequest(String fromYear, List<Decision> decisions) {
}
