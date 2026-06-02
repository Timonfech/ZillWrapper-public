package com.zillya.timonfech.zillwrapper.core.services.persistance;

import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseVersionEntity;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseVersionRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class LicenseRestoreService {

    private final LicenseRepository licenseRepository;
    private final LicenseVersionRepository licenseVersionRepository;
    private final EntityManager entityManager;

    @Transactional
    public LicenseRestoreResult restore(LicenseRestoreRequest request) {
        if (request == null || request.licenseId() == null || request.targetVersionNo() == null) {
            throw new IllegalArgumentException("licenseId and targetVersionNo are required");
        }

        LicenseEntity current = licenseRepository.findById(request.licenseId())
                .orElseThrow(() -> new IllegalArgumentException("LICENSE_NOT_FOUND"));
        LicenseVersionEntity target = licenseVersionRepository
                .findByLicenseIdAndVersionNo(request.licenseId(), request.targetVersionNo())
                .orElseThrow(() -> new IllegalArgumentException("VERSION_NOT_FOUND"));

        List<LicenseRestoreResult.FieldDiff> diff = buildDiff(current, target);
        if (request.dryRun()) {
            return new LicenseRestoreResult(
                    false,
                    current.getId(),
                    current.getVersionNo(),
                    request.targetVersionNo(),
                    diff,
                    List.of()
            );
        }

        setAuditContext(coalesceActor(request.actor()), "RESTORE");
        try {
            applyWhitelist(current, target);
            LicenseEntity saved = licenseRepository.save(current);
            return new LicenseRestoreResult(
                    true,
                    saved.getId(),
                    saved.getVersionNo() == null ? null : Math.max(1L, saved.getVersionNo() - 1L),
                    request.targetVersionNo(),
                    diff,
                    List.of()
            );
        } finally {
            clearAuditContext();
        }
    }

    private List<LicenseRestoreResult.FieldDiff> buildDiff(LicenseEntity current, LicenseVersionEntity target) {
        List<LicenseRestoreResult.FieldDiff> diff = new ArrayList<>();
        addDiff(diff, "status", asText(current.getStatus()), asText(target.getStatus()));
        addDiff(diff, "expiresAt", asText(current.getExpiresAt()), asText(target.getExpiresAt()));
        addDiff(diff, "periodAmount", asText(current.getPeriodAmount()), asText(target.getPeriodAmount()));
        addDiff(diff, "periodUnit", asText(current.getPeriodUnit()), asText(target.getPeriodUnit()));
        addDiff(diff, "devices", asText(current.getDevices()), asText(target.getDevices()));
        addDiff(diff, "description", asText(current.getDescription()), asText(target.getDescription()));
        addDiff(diff, "orderId", asText(current.getOrderId()), asText(target.getOrderId()));
        addDiff(diff, "orderItemId", asText(current.getOrderItemId()), asText(target.getOrderItemId()));
        addDiff(diff, "clientId", asText(current.getClientId()), asText(target.getClientId()));
        addDiff(diff, "sourceId", asText(current.getSourceId()), asText(target.getSourceId()));
        return diff;
    }

    private void applyWhitelist(LicenseEntity current, LicenseVersionEntity target) {
        current.setStatus(target.getStatus());
        current.setExpiresAt(target.getExpiresAt());
        current.setPeriodAmount(target.getPeriodAmount());
        current.setPeriodUnit(target.getPeriodUnit());
        current.setDevices(target.getDevices());
        current.setDescription(target.getDescription());
        current.setOrderId(target.getOrderId());
        current.setOrderItemId(target.getOrderItemId());
        current.setClientId(target.getClientId());
        current.setSourceId(target.getSourceId());
    }

    private void setAuditContext(String actor, String changeSource) {
        entityManager.createNativeQuery("select set_config('app.changed_by', :v, true)")
                .setParameter("v", actor)
                .getSingleResult();
        entityManager.createNativeQuery("select set_config('app.change_source', :v, true)")
                .setParameter("v", changeSource)
                .getSingleResult();
    }

    private void clearAuditContext() {
        entityManager.createNativeQuery("select set_config('app.changed_by', '', true)").getSingleResult();
        entityManager.createNativeQuery("select set_config('app.change_source', '', true)").getSingleResult();
    }

    private String coalesceActor(String actor) {
        return actor == null || actor.isBlank() ? "system" : actor.trim();
    }

    private void addDiff(List<LicenseRestoreResult.FieldDiff> out, String field, String current, String target) {
        if (!Objects.equals(current, target)) {
            out.add(new LicenseRestoreResult.FieldDiff(field, nullSafe(current), nullSafe(target)));
        }
    }

    private String nullSafe(String value) {
        return value == null ? "-" : value;
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

