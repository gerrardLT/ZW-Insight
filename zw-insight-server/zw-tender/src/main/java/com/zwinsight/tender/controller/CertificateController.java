package com.zwinsight.tender.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.tender.domain.BizCompanyCertificate;
import com.zwinsight.tender.domain.BizPersonCertificate;
import com.zwinsight.tender.service.CompanyCertificateService;
import com.zwinsight.tender.service.PersonCertificateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 证书管理接口（人员证书 + 企业证书）
 */
@RestController
@RequestMapping("/api/v1/tender/certificate")
@RequiredArgsConstructor
public class CertificateController {

    private final PersonCertificateService personCertificateService;
    private final CompanyCertificateService companyCertificateService;

    // ===== 人员证书 =====

    @GetMapping("/person")
    public R<PageResult<BizPersonCertificate>> personPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String personName,
            @RequestParam(required = false) String certificateType) {
        return R.ok(personCertificateService.page(page, size, personName, certificateType));
    }

    @PostMapping("/person")
    public R<Void> savePerson(@RequestBody BizPersonCertificate certificate) {
        personCertificateService.save(certificate);
        return R.ok();
    }

    @PutMapping("/person/{id}")
    public R<Void> updatePerson(@PathVariable Long id, @RequestBody BizPersonCertificate certificate) {
        certificate.setId(id);
        personCertificateService.update(certificate);
        return R.ok();
    }

    @DeleteMapping("/person/{id}")
    public R<Void> deletePerson(@PathVariable Long id) {
        personCertificateService.delete(id);
        return R.ok();
    }

    // ===== 企业证书 =====

    @GetMapping("/company")
    public R<PageResult<BizCompanyCertificate>> companyPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String certificateName,
            @RequestParam(required = false) String certificateType) {
        return R.ok(companyCertificateService.page(page, size, certificateName, certificateType));
    }

    @PostMapping("/company")
    public R<Void> saveCompany(@RequestBody BizCompanyCertificate certificate) {
        companyCertificateService.save(certificate);
        return R.ok();
    }

    @PutMapping("/company/{id}")
    public R<Void> updateCompany(@PathVariable Long id, @RequestBody BizCompanyCertificate certificate) {
        certificate.setId(id);
        companyCertificateService.update(certificate);
        return R.ok();
    }

    @DeleteMapping("/company/{id}")
    public R<Void> deleteCompany(@PathVariable Long id) {
        companyCertificateService.delete(id);
        return R.ok();
    }
}
