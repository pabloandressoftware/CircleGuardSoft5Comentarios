package com.circleguard.auth.controller;

import com.circleguard.auth.model.LocalUser;
import com.circleguard.auth.repository.LocalUserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserControllerTest {

    @Test
    void listsUsernameAndEmailAndDefaultsMissingEmailToEmpty() {
        LocalUserRepository repo = mock(LocalUserRepository.class);
        when(repo.findUsersByPermissionName("alert:receive")).thenReturn(List.of(
                LocalUser.builder().username("a").email("a@x.co").build(),
                LocalUser.builder().username("b").build()));

        List<Map<String, String>> body = new UserController(repo).getUsersByPermission("alert:receive").getBody();

        assertEquals(2, body.size());
        assertEquals("a@x.co", body.get(0).get("email"));
        assertEquals("", body.get(1).get("email"));
    }
}
