package com.example.roshdeandishe.attendance.dto.request;

import java.util.List;

/**
 * بدنه‌ی {@code PUT .../classes/{classId}/days/{day}}: فقط غایب‌ها و دیرآمدگان؛ فهرستِ خالی یعنی «همه حاضر».
 * {@code marks} الزامی است (تهی = ۴۰۰، تا غلطِ تایپی مُهرهای روز را پاک نکند).
 */
public record SaveRollRequest(List<MarkRequest> marks) {

    /** {@code status}: ABSENT یا LATE؛ {@code excused} فقط برای ABSENT معنا دارد. */
    public record MarkRequest(Long studentId, String status, Boolean excused, String note) {
    }
}
