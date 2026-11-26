package com.menufood.userservice.service;

import com.menufood.userservice.dto.request.CheckInRequestDTO;
import tools.jackson.databind.JsonNode;
import java.util.UUID;
import java.util.List;

public interface UserActivityService {
    JsonNode shoppingList(UUID id);

    JsonNode checkIns(UUID id);

    void shopping(UUID id, List<String> ids);

    JsonNode checkIn(UUID id, CheckInRequestDTO entry);

    void deleteCheckIn(UUID id, UUID entryId);
}
