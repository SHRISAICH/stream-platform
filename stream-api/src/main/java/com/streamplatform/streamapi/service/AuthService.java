package com.streamplatform.streamapi.service;

import com.streamplatform.streamapi.dto.JwtResponse;
import com.streamplatform.streamapi.dto.LoginRequest;
import com.streamplatform.streamapi.dto.RegisterRequest;

public interface AuthService {

    String register(RegisterRequest request);

    JwtResponse login(LoginRequest request);

}