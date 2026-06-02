package com.zillya.timonfech.zillwrapper.core.services.persistance;

import com.zillya.timonfech.zillwrapper.core.entities.LicenseStatus;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseEntity;
import com.zillya.timonfech.zillwrapper.core.entities.license.LicenseVersionEntity;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseRepository;
import com.zillya.timonfech.zillwrapper.core.repos.LicenseVersionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LicenseRestoreServiceTest {

    @Test
    void dryRunReturnsDiffAndDoesNotSave() {
        LicenseRepository licenseRepository = Mockito.mock(LicenseRepository.class);
        LicenseVersionRepository versionRepository = Mockito.mock(LicenseVersionRepository.class);
        EntityManager entityManager = Mockito.mock(EntityManager.class);

        LicenseEntity current = new LicenseEntity();
        current.setId(10L);
        current.setVersionNo(5L);
        current.setStatus(LicenseStatus.ALLOWED);
        current.setDevices(1);
        current.setExpiresAt(Instant.parse("2026-01-01T00:00:00Z"));

        LicenseVersionEntity target = new LicenseVersionEntity();
        target.setLicenseId(10L);
        target.setVersionNo(3L);
        target.setStatus(LicenseStatus.BLOCKED);
        target.setDevices(3);
        target.setExpiresAt(Instant.parse("2026-02-01T00:00:00Z"));

        when(licenseRepository.findById(10L)).thenReturn(Optional.of(current));
        when(versionRepository.findByLicenseIdAndVersionNo(10L, 3L)).thenReturn(Optional.of(target));

        LicenseRestoreService service = new LicenseRestoreService(licenseRepository, versionRepository, entityManager);
        LicenseRestoreResult result = service.restore(new LicenseRestoreRequest(10L, 3L, "tester", true));

        assertFalse(result.applied());
        assertEquals(10L, result.licenseId());
        assertEquals(5L, result.fromVersion());
        assertEquals(3L, result.targetVersion());
        assertTrue(result.diff().stream().anyMatch(d -> d.field().equals("status")));
        verify(licenseRepository, never()).save(Mockito.any());
    }

    @Test
    void versionNotFoundThrows() {
        LicenseRepository licenseRepository = Mockito.mock(LicenseRepository.class);
        LicenseVersionRepository versionRepository = Mockito.mock(LicenseVersionRepository.class);
        EntityManager entityManager = Mockito.mock(EntityManager.class);

        LicenseEntity current = new LicenseEntity();
        current.setId(10L);
        when(licenseRepository.findById(10L)).thenReturn(Optional.of(current));
        when(versionRepository.findByLicenseIdAndVersionNo(10L, 99L)).thenReturn(Optional.empty());

        LicenseRestoreService service = new LicenseRestoreService(licenseRepository, versionRepository, entityManager);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.restore(new LicenseRestoreRequest(10L, 99L, "tester", false))
        );
        assertEquals("VERSION_NOT_FOUND", ex.getMessage());
    }
}

