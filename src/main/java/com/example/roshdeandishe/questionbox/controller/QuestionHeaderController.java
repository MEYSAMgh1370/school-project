package com.example.roshdeandishe.questionbox.controller;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.HeaderInfo;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.ImageContent;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.NewHeader;
import com.example.roshdeandishe.questionbox.dto.request.HeaderRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** سربرگ‌های برگه‌ی چاپی (openspec/changes/question-box.md)؛ تصویر مثل QuestionImageController با fetch گرفته می‌شود. */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}/question-box/headers")
@RequiresModule("QUESTION_BOX")
@RequiresPermission("QUESTION_BOX_MANAGE")
public class QuestionHeaderController {

    private final QuestionBoxFacade facade;
    private final IamFacade iamFacade;

    public QuestionHeaderController(QuestionBoxFacade facade, IamFacade iamFacade) {
        this.facade = facade;
        this.iamFacade = iamFacade;
    }

    @GetMapping
    public List<HeaderInfo> list(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);
        return facade.listHeaders(schoolId, actor());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HeaderInfo create(@PathVariable Long schoolId, @RequestBody HeaderRequest request) {
        TenantContext.requireMatches(schoolId);
        return facade.createHeader(schoolId, actor(), toNew(request));
    }

    @PutMapping("/{headerId}")
    public HeaderInfo update(@PathVariable Long schoolId, @PathVariable Long headerId,
                             @RequestBody HeaderRequest request) {
        TenantContext.requireMatches(schoolId);
        return facade.updateHeader(schoolId, actor(), headerId, toNew(request));
    }

    @DeleteMapping("/{headerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long schoolId, @PathVariable Long headerId) {
        TenantContext.requireMatches(schoolId);
        facade.deleteHeader(schoolId, actor(), headerId);
    }

    @PutMapping(value = "/{headerId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HeaderInfo uploadImage(@PathVariable Long schoolId, @PathVariable Long headerId,
                                  @RequestParam("file") MultipartFile file) {
        TenantContext.requireMatches(schoolId);
        try {
            return facade.setHeaderImage(schoolId, actor(), headerId, file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GetMapping("/{headerId}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long schoolId, @PathVariable Long headerId) {
        TenantContext.requireMatches(schoolId);
        ImageContent image = facade.getHeaderImage(schoolId, headerId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(image.content());
    }

    private Long actor() {
        return iamFacade.currentUserId().value();
    }

    private static NewHeader toNew(HeaderRequest r) {
        return new NewHeader(r.name(), r.mode(), r.layout(), r.showSchoolName() == null || r.showSchoolName(),
                r.showLogo() != null && r.showLogo(), r.fields());
    }
}
