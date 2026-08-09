package com.one.service.impl;


import com.one.configurations.JwtProvider;
import com.one.domain.UserRole;
import com.one.exception.UserException;
import com.one.modal.User;
import com.one.repository.UserRepository;
import com.one.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;


@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {


	private final UserRepository userRepository;

	private final JwtProvider jwtProvider;


	@Override
	public User getUserByEmail(String email) throws UserException {
		User user=userRepository.findByEmail(email);
		if(user==null){
			throw new UserException("User not found with email: "+email);
		}
		return user;
	}

	@Override
	public User getUserFromJwtToken(String jwt) throws UserException {
		String email = jwtProvider.getEmailFromJwtToken(jwt);
		User user = userRepository.findByEmail(email);
		if(user==null) throw new UserException("user not exist with email "+email);
		return user;
	}

	@Override
	public User getUserById(Long id) throws UserException {
		return userRepository.findById(id).orElse(null);
	}

	@Override
	public Set<User> getUserByRole(UserRole role) throws UserException {
		return userRepository.findByRole(role);
	}

	@Override
	public User getCurrentUser() {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		User user= userRepository.findByEmail(email);
		if(user == null) {
			throw new EntityNotFoundException("User not found");
		}
		return user;
	}



	@Override
	public List<User> getUsers() throws UserException {
		return userRepository.findAll();
	}

	@Override
	public long getTotalUserCount() {
		return userRepository.count();
	}

}
