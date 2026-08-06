alter table academic.students
    add constraint uq_students_user_school unique (user_id, school_id);

alter table finance.invoices
    add constraint uq_invoices_school_student unique (school_id, student_id);
