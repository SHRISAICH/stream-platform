package com.streamplatform.streamapi.service.impl;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.streamplatform.streamapi.dto.JwtResponse;
import com.streamplatform.streamapi.dto.LoginRequest;
import com.streamplatform.streamapi.dto.RegisterRequest;
import com.streamplatform.streamapi.entity.User;
import com.streamplatform.streamapi.exception.ApiException;
import com.streamplatform.streamapi.repository.UserRepository;
import com.streamplatform.streamapi.security.JwtService;
import com.streamplatform.streamapi.service.AuthService;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    public String register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApiException("Email already exists.", HttpStatus.BAD_REQUEST);
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ApiException("Username already exists.", HttpStatus.BAD_REQUEST);
        }

        User user = new User();

        user.setFullName(request.getFullName());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("USER");
        user.setEnabled(true);

        userRepository.save(user);

        return "User registered successfully.";
    }

    @Override
    public JwtResponse login(LoginRequest request) {

        Optional<User> optionalUser =
                userRepository.findByEmail(request.getUsernameOrEmail());

        if (optionalUser.isEmpty()) {
            optionalUser =
                    userRepository.findByUsername(request.getUsernameOrEmail());
        }

        if (optionalUser.isEmpty()) {
            throw new ApiException("Invalid username or email.", HttpStatus.UNAUTHORIZED);
        }

        User user = optionalUser.get();

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword())) {
            throw new ApiException("Invalid password.", HttpStatus.UNAUTHORIZED);
        }

        String token = jwtService.generateToken(user.getUsername());

        return new JwtResponse(token);
    }
}