package com.zillya.timonfech.zillwrapper.core.services.persistance;

import com.zillya.timonfech.zillwrapper.core.entities.user_clients.ContactMethod;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.EmailContact;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.PhoneContact;
import com.zillya.timonfech.zillwrapper.core.entities.user_clients.TelegramContact;
import com.zillya.timonfech.zillwrapper.core.repos.EmailContactRepository;
import com.zillya.timonfech.zillwrapper.core.repos.PhoneContactRepository;
import com.zillya.timonfech.zillwrapper.core.repos.TelegramContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactManagementService {

    private final PhoneContactRepository phoneRepository;
    private final TelegramContactRepository telegramRepository;
    private final EmailContactRepository emailRepository;

    public Optional<EmailContact> getEmailContactByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String normalized = email.trim().toLowerCase();
        List<EmailContact> all = emailRepository.findAllByEncryptedValueIgnoreCase(normalized);
        if (all.isEmpty()) {
            return Optional.empty();
        }
        if (all.size() > 1) {
            log.warn("Multiple EmailContact records found for {}. Using the first one.", normalized);
        }
        return all.stream()
                .filter(c -> c.getClient() != null)
                .findFirst()
                .or(() -> Optional.of(all.getFirst()));
    }

    @Transactional
    public ContactMethod saveContact(ContactMethod contact) {
        try {
            contact.prepareForPersist(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to prepare contact data", e);
        }

        return switch (contact) {
            case PhoneContact phoneContact -> phoneRepository.save(phoneContact);
            case TelegramContact telegramContact -> telegramRepository.save(telegramContact);
            case EmailContact emailContact -> emailRepository.save(emailContact);
            default -> throw new UnsupportedOperationException("Unknown contact type: " + contact.getClass());
        };
    }
}
