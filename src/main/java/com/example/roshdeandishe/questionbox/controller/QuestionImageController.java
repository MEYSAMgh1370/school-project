package com.example.roshdeandishe.questionbox.controller;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.ImageContent;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.ImageRef;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.ImageRole;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * تصویرِ سؤال‌های باکس. خواندنِ بایت‌ها هدر {@code X-School-ID} می‌خواهد، پس کلاینت آن را با fetch می‌گیرد و
 * به blob-URL تبدیل می‌کند (تگ img نمی‌تواند هدر بفرستد). نوعِ پاسخ همان نوعِ واقعیِ ذخیره‌شده است، با nosniff.
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}/question-box")
@RequiresModule("QUESTION_BOX")
@RequiresPermission("QUESTION_BOX_MANAGE")
public class QuestionImageController {

    private final QuestionBoxFacade facade;
    private final IamFacade iamFacade;

    public QuestionImageController(QuestionBoxFacade facade, IamFacade iamFacade) {
        this.facade = facade;
        this.iamFacade = iamFacade;
    }

    @PostMapping(value = "/questions/{questionId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImageRef upload(@PathVariable Long schoolId, @PathVariable Long questionId,
                           @RequestParam("role") ImageRole role, @RequestParam("file") MultipartFile file) {
        TenantContext.requireMatches(schoolId);
        try {
            return facade.addImage(schoolId, iamFacade.currentUserId().value(), questionId, role, file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DeleteMapping("/questions/{questionId}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long schoolId, @PathVariable Long questionId, @PathVariable Long imageId) {
        TenantContext.requireMatches(schoolId);
        facade.removeImage(schoolId, iamFacade.currentUserId().value(), questionId, imageId);
    }

    @GetMapping("/images/{imageId}")
    public ResponseEntity<byte[]> content(@PathVariable Long schoolId, @PathVariable Long imageId) {
        TenantContext.requireMatches(schoolId);
        ImageContent image = facade.getImage(schoolId, imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(image.content());
    }
}
