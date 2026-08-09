package com.one.service;


import com.one.domain.UserRole;
import com.one.exception.UserException;
import com.one.modal.User;

import java.util.List;
import java.util.Set;
//import com.one.payload.request.UpdateUserDto;


public interface UserService {
	User getUserByEmail(String email) throws UserException;
	User getUserFromJwtToken(String jwt) throws UserException;
	User getUserById(Long id) throws UserException;
	Set<User> getUserByRole(UserRole role) throws UserException;
	List<User> getUsers() throws UserException;
	User getCurrentUser() throws UserException;



	/**
	 * Get total count of all registered users (Admin only)
	 */
	long getTotalUserCount();
}
