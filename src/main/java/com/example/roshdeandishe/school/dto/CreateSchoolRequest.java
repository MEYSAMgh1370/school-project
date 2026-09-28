package com.example.roshdeandishe.school.dto;

import com.example.roshdeandishe.school.SchoolFacade.SchoolLevel;

public record CreateSchoolRequest(String name, String timezone, String locale, SchoolLevel level,
                                  String slug) {
}
