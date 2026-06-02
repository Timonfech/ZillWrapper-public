package com.zillya.timonfech.zillwrapper.init;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;
import com.zillya.timonfech.zillwrapper.core.entities.product.ProductEntity;
import com.zillya.timonfech.zillwrapper.core.entities.security.ProductQuotaEntity;
import com.zillya.timonfech.zillwrapper.core.entities.security.UserSourceEntity;
import com.zillya.timonfech.zillwrapper.core.entities.source.SourceEntity;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethodType;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.EmailContact;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.PhoneContact;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import com.zillya.timonfech.zillwrapper.core.repos.ProductRepository;
import com.zillya.timonfech.zillwrapper.core.repos.UserRepository;
import com.zillya.timonfech.zillwrapper.core.services.SourceManagementService;
import com.zillya.timonfech.zillwrapper.core.source.SourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "users.sync", name = "enabled", havingValue = "true")
public class UsersSyncService {

    private final UserRepository userRepository;
    private final SourceManagementService sourceService;
    private final ProductRepository productRepository;

    @Value("${users.sync.config-path:./users.json}")
    private String configPath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    @Transactional
    public void sync() throws Exception {
        long before = userRepository.count();
        UsersConfig config = readConfig();
        if (config == null) {
            log.info("Users sync skipped: users.json not found in file path or classpath.");
            return;
        }
        if (config.getUsers() == null || config.getUsers().isEmpty()) {
            log.warn("Users sync enabled, but users config is empty.");
            return;
        }

        for (UsersConfig.UserSeed seed : config.getUsers()) {
            upsertUser(seed);
        }
        long after = userRepository.count();
        log.info("Users synced from config: {}, users in DB before={}, after={}",
                config.getUsers().size(),
                before,
                after);
    }

    private UsersConfig readConfig() throws Exception {
        InputStream resolved = resolveInputStream();
        if (resolved == null) {
            return null;
        }
        try (InputStream is = resolved) {
            return objectMapper.readValue(is, UsersConfig.class);
        }
    }

    private InputStream resolveInputStream() throws Exception {
        if (configPath != null && !configPath.isBlank()) {
            try {
                return new FileInputStream(configPath);
            } catch (Exception ignored) {
                log.warn("Users config file not found at path: {}. Falling back to classpath users.json", configPath);
            }
        }

        InputStream is = getClass().getClassLoader().getResourceAsStream("users.json");
        if (is == null) {
            return null;
        }
        return is;
    }

    private void upsertUser(UsersConfig.UserSeed seed) throws Exception {

        UserEntity user = userRepository
                .findByUsername(seed.getUsername())
                .orElseGet(UserEntity::new);

        user.setUsername(seed.getUsername());
        user.setFullName(seed.getFullName());
        user.setRole(seed.getRole());
        user.setActive(seed.getIsActive() == null || seed.getIsActive());
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(Instant.now());
        }

        mergeContacts(user, seed.getContacts());
        mergeSources(user, seed.getSources());
        seedAdminQuotas(user);

        userRepository.save(user);
    }

    private void seedAdminQuotas(UserEntity user) {
        if (user.getRole() != UserEntity.Role.ADMIN) {
            return;
        }
        if (user.getQuotas() == null) {
            user.setQuotas(new HashSet<>());
        }

        List<ProductEntity> products = productRepository.findAll();
        if (products.isEmpty()) {
            log.warn("Admin quota seed skipped for user={} because products table is empty.", user.getUsername());
            return;
        }

        Set<Integer> existingProductIds = new HashSet<>();
        for (ProductQuotaEntity quota : user.getQuotas()) {
            if (quota.getProduct() != null) {
                existingProductIds.add(quota.getProduct().getProductId());
            }
        }

        int inserted = 0;
        for (ProductEntity product : products) {
            if (existingProductIds.contains(product.getProductId())) {
                continue;
            }
            ProductQuotaEntity quota = new ProductQuotaEntity();
            quota.setUser(user);
            quota.setProduct(product);
            quota.setAllowedOperations(EnumSet.of(
                    OperationType.ORDER_CREATION,
                    OperationType.RESEND_NOTIFICATION
            ));
            quota.setReservedQuantity(0);
            quota.setLastUpdatedAt(Instant.now());
            user.getQuotas().add(quota);
            inserted++;
        }

        if (inserted > 0) {
            log.info("Seeded {} unlimited admin quota(s) for user={}", inserted, user.getUsername());
        }
    }

    // --- CONTACTS (merge по valueHash) ---
    private void mergeContacts(UserEntity user, List<UsersConfig.ContactSeed> seeds) throws Exception {

        if (seeds == null) return;
        if (user.getContacts() == null) {
            user.setContacts(new ArrayList<>());
        }

        for (UsersConfig.ContactSeed seed : seeds) {
            if (seed == null || seed.getType() == null || seed.getValue() == null || seed.getValue().isBlank()) {
                continue;
            }
            ContactMethodType type = mapContactType(seed.getType());
            if (type == null) {
                log.warn("Users sync: unknown contact type '{}' for user={}", seed.getType(), user.getUsername());
                continue;
            }
            switch (type) {
                case EMAIL -> mergeEmailContact(user, seed.getValue());
                case PHONE_NUMBER -> mergePhoneContact(user, seed.getValue());
                default -> {
                    // keep scope minimal: users.json sync currently supports email/phone
                }
            }
        }
    }

    private ContactMethodType mapContactType(String rawType) {
        if (rawType == null) {
            return null;
        }
        String normalized = rawType.trim().toUpperCase(Locale.ROOT);
        if ("PHONE".equals(normalized)) {
            normalized = "PHONE_NUMBER";
        }
        try {
            return ContactMethodType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void mergeEmailContact(UserEntity user, String rawValue) throws Exception {
        String normalized = normalizeEmail(rawValue);
        if (normalized == null) {
            return;
        }
        boolean exists = user.getContacts().stream()
                .filter(EmailContact.class::isInstance)
                .map(EmailContact.class::cast)
                .map(c -> normalizeEmail(c.getEncryptedValue()))
                .anyMatch(normalized::equals);
        if (exists) {
            return;
        }
        EmailContact email = new EmailContact(normalized);
        email.setType(ContactMethodType.EMAIL);
        email.setUser(user);
        email.prepareForPersist(null);
        user.getContacts().add(email);
    }

    private void mergePhoneContact(UserEntity user, String rawValue) throws Exception {
        String normalized = normalizePhone(rawValue);
        if (normalized == null) {
            return;
        }
        boolean exists = user.getContacts().stream()
                .filter(PhoneContact.class::isInstance)
                .map(PhoneContact.class::cast)
                .map(c -> normalizePhone(c.encryptedValue))
                .anyMatch(normalized::equals);
        if (exists) {
            return;
        }
        PhoneContact phone = new PhoneContact();
        phone.setType(ContactMethodType.PHONE_NUMBER);
        phone.setUser(user);
        phone.plainValue = normalized;
        phone.prepareForPersist(null);
        user.getContacts().add(phone);
    }

    private String normalizeEmail(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizePhone(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    // --- SOURCES (merge по source) ---
    private void mergeSources(UserEntity user, List<UsersConfig.SourceSeed> seeds) {

        if (seeds == null) return;
        if (user.getSources() == null) {
            user.setSources(new HashSet<>());
        }

        for (UsersConfig.SourceSeed seed : seeds) {

            SourceType type = SourceType.valueOf(seed.getSourceType());
            String identifier = seed.getIdentifierName();

            SourceEntity source = sourceService.getOrCreateSource(type, identifier);

            Optional<UserSourceEntity> existing = user.getSources().stream()
                    .filter(s -> s.getSource().getId().equals(source.getId()))
                    .findFirst();

            Map<UserSourceEntity.SecurityFactor, String> factors = new HashMap<>();
            seed.getFactors().forEach((k, v) ->
                    factors.put(UserSourceEntity.SecurityFactor.valueOf(k), v)
            );

            if (existing.isPresent()) {
                existing.get().setRequiredFactors(factors);
            } else {
                UserSourceEntity newSource = new UserSourceEntity(user, type, factors);
                newSource.setSource(source);
                user.getSources().add(newSource);
            }
        }
    }
}
