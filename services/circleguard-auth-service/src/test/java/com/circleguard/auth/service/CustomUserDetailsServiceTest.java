package com.circleguard.auth.service;

import com.circleguard.auth.model.LocalUser;
import com.circleguard.auth.model.Permission;
import com.circleguard.auth.model.Role;
import com.circleguard.auth.repository.LocalUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomUserDetailsServiceTest {

    private final LocalUserRepository repo = mock(LocalUserRepository.class);
    private final CustomUserDetailsService service = new CustomUserDetailsService(repo);

    private LocalUser user(boolean active) {
        Role role = Role.builder().name("ADMIN")
                .permissions(Set.of(Permission.builder().name("gate:validate").build())).build();
        return LocalUser.builder().username("ana").password("hash").isActive(active).roles(Set.of(role)).build();
    }

    @Test
    void loadsRolesWithPrefixAndPermissions() {
        when(repo.findByUsername("ana")).thenReturn(Optional.of(user(true)));

        UserDetails details = service.loadUserByUsername("ana");

        var names = details.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toSet());
        assertEquals(Set.of("ROLE_ADMIN", "gate:validate"), names);
        assertEquals("hash", details.getPassword());
    }

    @Test
    void unknownUserIsRejected() {
        when(repo.findByUsername("x")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("x"));
    }

    @Test
    void disabledUserIsRejected() {
        when(repo.findByUsername("ana")).thenReturn(Optional.of(user(false)));
        assertThrows(DisabledException.class, () -> service.loadUserByUsername("ana"));
    }
}
