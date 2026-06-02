package com.zillya.timonfech.zillwrapper.init;

import com.zillya.timonfech.zillwrapper.core.entities.user_clients.UserEntity;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class UsersConfig {
    private List<UserSeed> users = new ArrayList<>();

    @Data
    public static class UserSeed {
        private String username;
        private String fullName;
        private UserEntity.Role role;
        private Boolean isActive = true;
        private List<ContactSeed> contacts = new ArrayList<>(1);
        private List<SourceSeed> sources = new ArrayList<>(1);
    }

    @Data
    public static class ContactSeed {
        private String type;
        private String value;
    }

    @Data
    public static class SourceSeed {
        private String sourceType;
        private String identifierName;
        private Map<String, String> factors = new HashMap<>();
    }
}