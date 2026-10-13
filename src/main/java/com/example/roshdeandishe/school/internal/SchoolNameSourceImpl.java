package com.example.roshdeandishe.school.internal;

import com.example.roshdeandishe.iam.shared.SchoolNameSource;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** پیاده‌سازیِ {@link SchoolNameSource} روی {@link SchoolFacade}؛ iam از آن فقط نامِ مدرسه را می‌خواند. */
@Component
class SchoolNameSourceImpl implements SchoolNameSource {

    private final SchoolFacade schoolFacade;

    SchoolNameSourceImpl(SchoolFacade schoolFacade) {
        this.schoolFacade = schoolFacade;
    }

    @Override
    public Optional<String> nameOf(Long schoolId) {
        return schoolFacade.findSchool(new SchoolId(schoolId)).map(SchoolInfo::name);
    }
}
