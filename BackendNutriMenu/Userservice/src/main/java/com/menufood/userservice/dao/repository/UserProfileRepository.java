package com.menufood.userservice.dao.repository;

import com.menufood.userservice.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
}
