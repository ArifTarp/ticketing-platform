package com.demo.ticketing.auth.application;

import com.demo.ticketing.auth.application.exception.DuplicateEmailException;
import com.demo.ticketing.auth.application.exception.InvalidCredentialsException;
import com.demo.ticketing.auth.domain.Role;
import com.demo.ticketing.auth.domain.User;
import com.demo.ticketing.auth.infra.RoleRepository;
import com.demo.ticketing.auth.infra.UserRepository;
import com.demo.ticketing.auth.infra.security.JwtService;
import com.demo.ticketing.auth.web.dto.AuthResponse;
import com.demo.ticketing.auth.web.dto.LoginRequest;
import com.demo.ticketing.auth.web.dto.RegisterRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {

    private static final String DEFAULT_ROLE = "USER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                        RoleRepository roleRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }

        User user = new User(request.email(), passwordEncoder.encode(request.password()));
        Role defaultRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("Default role '" + DEFAULT_ROLE + "' is missing"));
        user.addRole(defaultRole);
        userRepository.save(user);

        return issueTokenFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return issueTokenFor(user);
    }

    private AuthResponse issueTokenFor(User user) {
        List<String> roleNames = user.getRoles().stream().map(Role::getName).toList();
        String token = jwtService.issueToken(user.getId(), user.getEmail(), roleNames);
        return new AuthResponse(token);
    }
}
