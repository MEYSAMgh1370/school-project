package com.example.roshdeandishe.academic.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import org.springframework.stereotype.Service;

@Service
class AcademicFacadeImpl implements AcademicFacade {

    @Override
    public StudentInfo getStudentInfo(Long studentId) {
        // TODO: اتصال به StudentEntity/StudentRepository واقعی (فاز بعدی)
        throw new UnsupportedOperationException("هنوز پیاده‌سازی نشده - فاز بعدی توسعه");
    }
}
