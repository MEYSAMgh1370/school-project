package com.example.roshdeandishe.academic.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.StudentEnrolledEvent;
import com.example.roshdeandishe.academic.shared.AcademicError;
import com.example.roshdeandishe.academic.shared.AcademicException;
import com.example.roshdeandishe.iam.IamFacade;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
class AcademicFacadeImpl implements AcademicFacade {

    private final StudentRepository studentRepository;
    private final ClassRepository classRepository;
    private final IamFacade iamFacade;
    private final ApplicationEventPublisher eventPublisher;

    AcademicFacadeImpl(
            StudentRepository studentRepository,
            ClassRepository classRepository,
            IamFacade iamFacade,
            ApplicationEventPublisher eventPublisher
    ) {
        this.studentRepository = studentRepository;
        this.classRepository = classRepository;
        this.iamFacade = iamFacade;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public StudentId enrollStudent(String email, String fullName, Long schoolId, Long classId) {
        requireClassExists(schoolId, classId);

        // همان الگوی SchoolController: پیدا/ساخت کاربر در iam، سپس Membership
        // نقش STUDENT برای او در این مدرسه (idempotent در هر دو مرحله)
        IamFacade.UserId userId = iamFacade.findOrCreateUser(email, fullName);
        iamFacade.createMembership(userId, schoolId, "STUDENT");

        StudentEntity student = studentRepository.findByUserIdAndSchoolId(userId.value(), schoolId)
                .orElseGet(() -> new StudentEntity(userId.value(), schoolId, classId, Instant.now()));
        student.setClassId(classId); // پشتیبانی از جابه‌جایی کلاس در فراخوانی دوباره
        StudentEntity saved = studentRepository.save(student);

        eventPublisher.publishEvent(new StudentEnrolledEvent(schoolId, saved.getId(), classId));

        return new StudentId(saved.getId());
    }

    @Override
    public StudentInfo getStudentInfo(Long studentId) {
        StudentEntity student = studentRepository.findById(studentId)
                .orElseThrow(() -> new AcademicException(new AcademicError.StudentNotFound(studentId)));

        return new StudentInfo(
                new StudentId(student.getId()),
                student.getUserId(),
                student.getSchoolId(),
                new ClassId(student.getClassId())
        );
    }

    @Override
    @Transactional
    public ClassId createClass(Long schoolId, String name, Long teacherId, String section) {
        ClassEntity saved = classRepository.save(new ClassEntity(schoolId, name, teacherId, section));
        return new ClassId(saved.getId());
    }

    @Override
    public List<ClassInfo> listClasses(Long schoolId) {
        return classRepository.findBySchoolId(schoolId).stream()
                .map(c -> new ClassInfo(new ClassId(c.getId()), c.getSchoolId(), c.getName(), c.getTeacherId(), c.getSection()))
                .toList();
    }

    private void requireClassExists(Long schoolId, Long classId) {
        ClassEntity classEntity = classRepository.findById(classId)
                .orElseThrow(() -> new AcademicException(new AcademicError.ClassNotFound(classId)));
        if (!classEntity.getSchoolId().equals(schoolId)) {
            // کلاس متعلق به مدرسه‌ی دیگری است - از دید academic همان "یافت نشد" است
            throw new AcademicException(new AcademicError.ClassNotFound(classId));
        }
    }
}
