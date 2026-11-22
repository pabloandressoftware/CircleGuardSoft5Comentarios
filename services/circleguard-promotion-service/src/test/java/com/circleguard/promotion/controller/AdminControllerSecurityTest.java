package com.circleguard.promotion.controller;

import com.circleguard.promotion.model.jpa.SystemSettings;
import com.circleguard.promotion.repository.jpa.SystemSettingsRepository;
import com.circleguard.promotion.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-21: /api/v1/admin/** exige autenticacion y el permiso dashboard:view. */
@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SystemSettingsRepository settingsRepository;

    @Test
    void adminSettings_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "dashboard:view")
    void adminSettings_WithDashboardPermission_Returns200() throws Exception {
        when(settingsRepository.getSettings()).thenReturn(Optional.empty());
        when(settingsRepository.save(any(SystemSettings.class))).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(get("/api/v1/admin/settings"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "other:permission")
    void adminSettings_WithoutDashboardPermission_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings"))
                .andExpect(status().isForbidden());
    }
}
