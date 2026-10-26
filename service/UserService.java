package com.ecommerce.productordermanagement.service;

import com.ecommerce.productordermanagement.entity.User;

public interface UserService {

    User registerUser(User user);

    User loginUser(String email, String password);

    User getUserById(Long id);
}