package com.menufood.userservice.service.impl;

import com.menufood.userservice.service.UserActivityService;

import com.menufood.userservice.dto.request.CheckInRequestDTO;
import com.menufood.userservice.dao.repository.AccountStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import java.util.*;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@RequiredArgsConstructor
@Transactional
public class UserActivityServiceImpl implements UserActivityService {
    private final AccountStateRepository store;
    private final ObjectMapper json;

    @Override
    public JsonNode shoppingList(UUID id) {
        return store.load(id).state().withArray("checkedShopping");
    }

    @Override
    public JsonNode checkIns(UUID id) {
        return store.load(id).state().withArray("checkIns");
    }

    @Override
    public void shopping(UUID id, List<String> ids) {
        if (ids.size() > 2000) throw new ResponseStatusException(BAD_REQUEST, "Danh sách quá dài");
        var account = store.load(id);
        account.state().set("checkedShopping", json.valueToTree(new LinkedHashSet<>(ids)));
        store.save(account);
    }

    @Override
    public JsonNode checkIn(UUID id, CheckInRequestDTO entry) {
        var account = store.load(id);
        ArrayNode entries = json.createArrayNode();
        for (JsonNode previous : account.state().path("checkIns")) if (!previous.path("id").asText().equals(entry.id().toString())) entries.add(previous);
        entries.add(json.valueToTree(entry));
        if (entries.size() > 2000) throw new ResponseStatusException(BAD_REQUEST, "Đã đạt giới hạn nhật ký");
        account.state().set("checkIns", entries); store.save(account);
        return json.valueToTree(entry);
    }

    @Override
    public void deleteCheckIn(UUID id, UUID entryId) {
        var account = store.load(id);
        ArrayNode entries = json.createArrayNode();
        account.state().path("checkIns").forEach(entry -> { if (!entry.path("id").asText().equals(entryId.toString())) entries.add(entry); });
        account.state().set("checkIns", entries); store.save(account);
    }
}
