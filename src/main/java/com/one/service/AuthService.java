package com.one.service;

import com.one.exception.UserException;
import com.one.payload.dto.UserDTO;
import com.one.payload.response.AuthResponse;



public interface AuthService {
    AuthResponse login(String username, String password) throws UserException;
    AuthResponse signup(UserDTO req) throws UserException;

    void createPasswordResetToken(String email) throws UserException;
    void resetPassword(String token, String newPassword);
}
