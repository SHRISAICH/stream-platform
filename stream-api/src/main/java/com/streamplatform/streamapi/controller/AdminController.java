package com.streamplatform.streamapi.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.streamplatform.streamapi.dto.AdminStatsResponse;
import com.streamplatform.streamapi.dto.StreamResponse;
import com.streamplatform.streamapi.dto.UpdateUserRoleRequest;
import com.streamplatform.streamapi.dto.UpdateUserStatusRequest;
import com.streamplatform.streamapi.dto.UserResponse;
import com.streamplatform.streamapi.dto.VideoResponse;
import com.streamplatform.streamapi.service.AdminService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> getPlatformStats() {
        return ResponseEntity.ok(adminService.getPlatformStats());
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @PutMapping("/users/{id}/status")
    public ResponseEntity<UserResponse> updateUserStatus(
            @PathVariable Long id,
            @RequestBody UpdateUserStatusRequest request) {
        boolean enabled = request.getEnabled() != null && request.getEnabled();
        return ResponseEntity.ok(adminService.updateUserStatus(id, enabled));
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<UserResponse> updateUserRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRoleRequest request) {
        return ResponseEntity.ok(adminService.updateUserRole(id, request.getRole()));
    }

    @GetMapping("/streams")
    public ResponseEntity<List<StreamResponse>> getAllStreams() {
        return ResponseEntity.ok(adminService.getAllStreams());
    }

    @DeleteMapping("/streams/{id}")
    public ResponseEntity<Void> deleteStream(@PathVariable Long id) {
        adminService.deleteStreamAsAdmin(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/videos")
    public ResponseEntity<List<VideoResponse>> getAllVideos() {
        return ResponseEntity.ok(adminService.getAllVideos());
    }

    @DeleteMapping("/videos/{id}")
    public ResponseEntity<Void> deleteVideo(@PathVariable Long id) {
        adminService.deleteVideoAsAdmin(id);
        return ResponseEntity.noContent().build();
    }
}
