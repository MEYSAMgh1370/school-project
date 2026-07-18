package com.example.roshdeandishe.school.internal;

import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.shared.SchoolError;
import com.example.roshdeandishe.school.shared.SchoolException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
class SchoolFacadeImpl implements SchoolFacade {

    private final SchoolRepository schoolRepository;

    SchoolFacadeImpl(SchoolRepository schoolRepository) {
        this.schoolRepository = schoolRepository;
    }

    @Override
    @Transactional
    public SchoolId createSchool(String name, String timezone, String locale, SchoolLevel level) {
        SchoolEntity entity = schoolRepository.save(new SchoolEntity(name, timezone, locale, level));
        return new SchoolId(entity.getId());
    }

    @Override
    public Optional<SchoolInfo> findSchool(SchoolId schoolId) {
        return schoolRepository.findById(schoolId.value()).map(this::toInfo);
    }

    @Override
    public List<SchoolInfo> listSchools() {
        return schoolRepository.findAll().stream().map(this::toInfo).toList();
    }

    @Override
    @Transactional
    public void setSchoolStatus(SchoolId schoolId, SchoolStatus status) {
        SchoolEntity entity = schoolRepository.findById(schoolId.value())
                .orElseThrow(() -> new SchoolException(new SchoolError.SchoolNotFound(schoolId.value())));
        entity.setStatus(status);
        schoolRepository.save(entity);
    }

    private SchoolInfo toInfo(SchoolEntity entity) {
        return new SchoolInfo(
                new SchoolId(entity.getId()),
                entity.getName(),
                entity.getStatus(),
                entity.getTimezone(),
                entity.getLocale(),
                entity.getSchoolLevel()
        );
    }
}
